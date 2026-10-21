# packages/android

The native Android app. It runs the whole stack on the phone: the OpenChamber
server and the OpenCode engine are child processes of the app, both bound to
the loopback interface, and the screens are a native port of the shared React
UI.

## What this package owns

| Folder | Owns |
| --- | --- |
| `app/` | The Compose screens, the activity, the foreground service and the object graph |
| `core/model/` | The domain model: sessions, messages, parts, permissions and forms |
| `core/api/` | The OpenCode 2.x and OpenChamber HTTP clients, the DTOs and the event stream |
| `core/sync/` | The event pipeline, the reducers and the stores that hold live state |
| `core/runtime/` | The child processes, their health, their logs and the client token |
| `core/design/` | The Material 3 theme, the typography and the colors |
| `tools/` | The scripts that build the binaries, the catalogs and the spikes |
| `licenses/` | The licenses of every bundled binary and library |

The package has no `package.json`, so it is not a member of the Bun
workspace. The repository's own gate does not see it. The Gradle build and
the Android Lint are what check it.

## Invariants

- **The server binds the loopback interface only.** The phone is not a server
  for other machines. The app talks to `http://127.0.0.1:<port>`.
- **The app is a client, like the browser UI.** Every action goes through the
  same server routes the web UI uses. A new route in `packages/web` needs a
  decision entry in the owning `DOCUMENTATION.md`.
- **The server owns the engine.** The app starts the server and waits for the
  engine through the engine health route. It never spawns OpenCode itself.
- **A failed request never clears existing state.** The stores keep what they
  have. A failure is shown as a failure.
- **Secrets stay in the Keystore.** Provider keys never reach the log ring
  buffer, the preferences or a log line.
- **Strings live in the shared catalogs.** A string that only the native app
  shows is added to `packages/ui/src/lib/i18n/messages/android.i18n.ts`, not
  to a second copy of the catalog.

## Decisions

### Why the server is the desktop's own server code

The server in `packages/web/server` is the product's logic: the OpenCode
lifecycle, the session list, the git surface, the terminal, the search. A
phone that reimplements it would be a second product that drifts. The app
therefore speaks the same HTTP and WebSocket contract the browser UI speaks,
and only the client side is rewritten.

The cost is that the server has to run on Android. That is what the spikes
under `tools/spikes/` are for.

### Why `tools/` and not `build/`

Gradle uses `<project>/build` as its output directory, and `gradlew clean`
removes it. The scripts that build the binaries and the catalogs are source,
so they live in `tools/` next to the spikes.

### Why the server prints its port on stdout

The app starts the server on a free port and needs to know which one it got.
The server entry prints a single line, `{"type":"openchamber.listening","port":...}`,
and the app reads it from the child's standard output. Every other line of
that stream is a server log line and goes into the log ring buffer.

Both streams are drained continuously. A pipe nobody reads blocks the writer,
and a blocked server never prints its port.

### Why the phone mints its own client token

The server runs without a UI password, so the app creates a client record with
`POST /api/client-auth/clients` and sends the returned token as a bearer
token. The server replaces the record that carries the same dedupe key, so
the phone never piles up tokens. The token lives in the Android Keystore.

### Why the runtime name is `android`

The server reports its runtime to clients through `OPENCHAMBER_RUNTIME`. Every
runtime check in the server compares against `desktop`, so a server that says
`android` behaves like a `web` server. The name tells the phone apart from
the desktop shell.

### Why the terminal reads `/proc` on Android

`packages/web/server/lib/terminal/shutdown.js` reads `/proc` on Linux and
falls back to `/bin/ps` with BSD flags everywhere else. Android is
Linux-based and ships neither `procps` nor `/bin/ps`, so the fallback would
throw and the shutdown path would lose the 20 second grace period that
terminal workloads rely on.

## The spikes

Every spike under `tools/spikes/` answers one question that decides whether
the port is possible. A spike that misses its acceptance criterion ends the
program. It is not patched over.

| Spike | Question | Status |
| --- | --- | --- |
| `bun-android.ts` | Can a compiled Bun binary run on Android? | Runs in CI on an emulator |
| OpenCode on Android | Can the engine be built and booted for Android? | Waiting for the opencode source at `v2.0.32` |
| Server on Android | Can the server boot with its native dependencies? | Waiting for the PTY, git, ripgrep and bash builds |

## Verification

The Kotlin code is checked by Gradle, not by the repository's Bun gate:

```bash
cd packages/android
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

CI builds the debug APK and runs the Bun spike on an emulator. The workflow is
`.github/workflows/android-native.yml`.
