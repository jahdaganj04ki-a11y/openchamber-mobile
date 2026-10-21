# OpenChamber for Android

The native Android app for OpenChamber. Everything runs on the phone: the app
starts the OpenChamber server and the OpenCode engine as child processes on
the loopback interface and talks to them over `http://127.0.0.1:<port>`.

This app is separate from the Capacitor app in `packages/mobile`. Both can be
installed on the same phone, they use different application ids.

## Building

The build needs JDK 21 and the Android SDK (compile and target 35, min SDK
24).

```bash
cd packages/android
./gradlew :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`.

CI does the same and uploads the APK as an artifact. The workflow is
`.github/workflows/android-native.yml`. The feasibility spikes run in
`.github/workflows/android-spikes.yml`.

## What still does not work yet

The port is in progress. The build produces an app that starts the server and
shows the session list of a directory. The surfaces are listed one by one in
`PARITY.md`.

The engine binary is not in the APK yet. Until it is, the app starts the
server but the engine stays in the starting state. The OpenCode build for
Android is the open question that decides the rest of the work.

## Layout

`DOCUMENTATION.md` describes what each folder owns and which decisions the
package makes. `tools/` holds the scripts that build the binaries, the
message catalogs and the spikes.
