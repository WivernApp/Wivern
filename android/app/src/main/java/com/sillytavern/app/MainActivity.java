package com.sillytavern.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final String TAG = "WIVERN";
    private static final int ST_PORT = 8000;
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private WebView webView;
    private TextView statusText;
    private ProgressBar progressBar;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ValueCallback<Uri[]> fileUploadCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.status_text);
        progressBar = findViewById(R.id.progress_bar);
        webView = findViewById(R.id.webview);

        setupWebView();

        new Thread(() -> {
            String nodeDir = getFilesDir().getAbsolutePath() + "/nodejs-project";

            if (needsExtraction(nodeDir)) {
                extractSillyTavern(nodeDir);
            }

            // Ensure data directories exist
            new File(nodeDir + "/data-root").mkdirs();

            updateStatus("Starting SillyTavern server...", -1);
            startNodeService();
            waitForServerAndLoad();
        }).start();
    }

    private boolean needsExtraction(String nodeDir) {
        File marker = new File(nodeDir + "/.extraction_complete");
        if (!marker.exists()) return true;

        // Re-extract if APK was updated
        SharedPreferences prefs = getSharedPreferences("WIVERN_PREFS", MODE_PRIVATE);
        long prev = prefs.getLong("APK_LAST_UPDATE", 0);
        long current = getAPKUpdateTime();
        return current != prev;
    }

    private void extractSillyTavern(String nodeDir) {
        Log.i(TAG, "Extracting SillyTavern to: " + nodeDir);
        updateStatus("Preparing extraction...", 0);

        // Clean old extraction
        File nodeDirFile = new File(nodeDir);
        if (nodeDirFile.exists()) {
            updateStatus("Cleaning old files...", 0);
            deleteRecursively(nodeDirFile);
        }
        nodeDirFile.mkdirs();

        try {
            // Get zip size for progress calculation
            InputStream sizeStream = getAssets().open("sillytavern.zip");
            long totalSize = sizeStream.available();
            sizeStream.close();

            InputStream is = getAssets().open("sillytavern.zip");
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is, 65536));
            ZipEntry entry;
            byte[] buffer = new byte[65536];
            long bytesRead = 0;
            int fileCount = 0;

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                File outFile = new File(nodeDir, name);

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(outFile);
                    BufferedOutputStream bos = new BufferedOutputStream(fos, 65536);
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        bos.write(buffer, 0, len);
                        bytesRead += len;
                    }
                    bos.close();
                    fos.close();
                    fileCount++;

                    if (fileCount % 100 == 0) {
                        int pct = totalSize > 0 ? (int)(bytesRead * 100 / totalSize) : -1;
                        // Clamp since available() isn't perfectly accurate for compressed streams
                        pct = Math.min(pct, 99);
                        updateStatus("Extracting files... (" + fileCount + " files)", pct);
                    }
                }
                zis.closeEntry();
            }
            zis.close();
            is.close();

            // Write completion marker
            new FileOutputStream(new File(nodeDir, ".extraction_complete")).close();

            // Save APK update time
            getSharedPreferences("WIVERN_PREFS", MODE_PRIVATE)
                .edit().putLong("APK_LAST_UPDATE", getAPKUpdateTime()).apply();

            updateStatus("Extraction complete! (" + fileCount + " files)", 100);
            Log.i(TAG, "Extraction complete: " + fileCount + " files, " + bytesRead + " bytes");

        } catch (IOException e) {
            Log.e(TAG, "Extraction failed", e);
            updateStatus("Extraction failed: " + e.getMessage(), -1);
            // Clean up incomplete extraction
            deleteRecursively(new File(nodeDir));
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setDatabaseEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " WivernApp/1.0");
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                handler.post(() -> {
                    statusText.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                });
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                // Cancel any pending callback
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = callback;

                Intent intent = params.createIntent();
                // Allow picking any file type
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(intent, "Choose file"), FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    Log.e(TAG, "File chooser failed", e);
                    fileUploadCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });
    }

    private void startNodeService() {
        Intent serviceIntent = new Intent(this, NodeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void waitForServerAndLoad() {
        int maxRetries = 120;
        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(1000);
                HttpURLConnection conn = (HttpURLConnection)
                    new URL("http://127.0.0.1:" + ST_PORT).openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code == 200) {
                    updateStatus("Loading SillyTavern...", 100);
                    handler.post(() -> webView.loadUrl("http://127.0.0.1:" + ST_PORT));
                    return;
                }
            } catch (Exception e) {
                updateStatus("Waiting for server... (" + (i + 1) + "s)", -1);
            }
        }
        updateStatus("Server failed to start. Check logcat for errors.", -1);
    }

    private void updateStatus(String msg, int progress) {
        Log.i(TAG, msg);
        handler.post(() -> {
            statusText.setText(msg);
            if (progress >= 0) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(false);
                progressBar.setProgress(progress);
            } else {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileUploadCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
                fileUploadCallback.onReceiveValue(results);
                fileUploadCallback = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private long getAPKUpdateTime() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return 1;
        }
    }

    private static boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        return file.delete();
    }
}
