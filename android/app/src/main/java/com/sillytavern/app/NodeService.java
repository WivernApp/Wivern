package com.sillytavern.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.File;

public class NodeService extends Service {
    private static final String TAG = "WIVERN";
    private static final String CHANNEL_ID = "wivern_node_service";
    private static boolean nodeStarted = false;
    private Thread nodeThread;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent notifIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Wivern - SillyTavern")
                .setContentText("Server is running")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        }

        startForeground(1, notification);

        if (!nodeStarted) {
            nodeStarted = true;
            String nodeDir = getApplicationContext().getFilesDir().getAbsolutePath() + "/nodejs-project";

            // Don't start Node if extraction isn't complete
            File marker = new File(nodeDir + "/.extraction_complete");
            if (!marker.exists()) {
                Log.w(TAG, "Extraction not complete, waiting...");
                // Poll for completion
                nodeThread = new Thread(() -> {
                    for (int i = 0; i < 300; i++) {
                        if (marker.exists()) {
                            startNode(nodeDir);
                            return;
                        }
                        try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                    }
                    Log.e(TAG, "Timed out waiting for extraction");
                });
                nodeThread.start();
            } else {
                nodeThread = new Thread(() -> startNode(nodeDir));
                nodeThread.start();
            }
        }

        return START_STICKY;
    }

    private void startNode(String nodeDir) {
        Log.i(TAG, "Starting SillyTavern server from: " + nodeDir);
        try {
            // Set ICU data path before starting Node so Unicode properties work
            NodeEngine.setICUData(nodeDir);

            int exitCode = NodeEngine.startNodeWithArguments(new String[]{
                "node",
                nodeDir + "/wivern-start.js",
                "--disableCsrf",
                "--browserLaunchEnabled=false",
                "--dataRoot", nodeDir + "/data-root"
            });
            Log.e(TAG, "Node.js exited with code: " + exitCode);
        } catch (Exception e) {
            Log.e(TAG, "Node.js crashed: " + e.getMessage(), e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        nodeStarted = false;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Wivern Server",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps the SillyTavern server running");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
}
