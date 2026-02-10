# Wivern - SillyTavern on Android

A native Android wrapper for [SillyTavern](https://github.com/SillyTavern/SillyTavern), the LLM frontend for power users.

Wivern bundles the full SillyTavern server inside an Android app using [nodejs-mobile](https://github.com/nicolo-ribaudo/ppr-prebuilt-nodejs-mobile). The server runs as a foreground service and the UI is displayed in a native WebView — no Termux, no external browser, just one app.

## How It Works

- **Node.js server** runs as an Android foreground service via nodejs-mobile (Node 18.20.4)
- **WebView** points to `http://127.0.0.1:8000` once the server is ready
- **First launch** extracts SillyTavern from a bundled zip (~180MB) into the app's private storage
- **Unicode regex patching** — nodejs-mobile ships without ICU, so a Babel build step transforms all `\p{}` Unicode property escapes in node_modules before bundling

## Building

### Prerequisites

- Android SDK (API 34) with NDK 26.x and CMake 3.22+
- JDK 17
- Node.js 18+ (host machine, for the build script)
- nodejs-mobile prebuilt binaries ([download here](https://github.com/nicolo-ribaudo/ppr-prebuilt-nodejs-mobile/releases))

### Setup

1. Clone this repo:
   ```bash
   git clone https://github.com/WivernApp/Wivern.git
   cd Wivern
   npm install
   ```

2. Download `nodejs-mobile-v18.20.4-android.zip` and extract it:
   ```bash
   mkdir -p android/app/libnode
   # Extract bin/ and include/ into android/app/libnode/
   ```

3. Create `android/local.properties`:
   ```
   sdk.dir=/path/to/your/android-sdk
   ```

4. Build:
   ```bash
   ./build-android.sh
   ```

The APK will be at `android/app/build/outputs/apk/debug/app-debug.apk`.

### What the build script does

1. Installs npm dependencies
2. Runs `fix-unicode-regex.mjs` — uses Babel to transform `\p{}` regex patterns in node_modules for ICU-less Node
3. Zips the SillyTavern project into `android/app/src/main/assets/sillytavern.zip`
4. Runs Gradle to compile the Android app with the nodejs-mobile JNI bridge

## Wivern-Specific Files

| File | Purpose |
|------|---------|
| `android/` | Android app project (Gradle, Java, JNI C++) |
| `wivern-start.js` | Wrapper entry point — sets CWD, polyfills TextDecoder, copies default config, then loads `server.js` |
| `fix-unicode-regex.mjs` | Build-time Babel transform for `\p{}` patterns |
| `build-android.sh` | One-command build script |
| `src/transformers.js` | Patched to lazy-load sillytavern-transformers (avoids heavy module load at startup) |
| `wivern.png` | App icon |

## Upstream

This is a fork of [SillyTavern/SillyTavern](https://github.com/SillyTavern/SillyTavern). Upstream changes can be merged in normally — the Wivern-specific files are additive and the only modified upstream file is `src/transformers.js`.

## License

AGPL-3.0 (same as upstream SillyTavern)
