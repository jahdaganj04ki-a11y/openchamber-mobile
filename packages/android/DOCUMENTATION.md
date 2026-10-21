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

### Why the server is one compiled binary with three patches

The phone runs the server as a single file the app extracts to its own
storage. The server reads three things that sit next to its own modules, and
a compiled binary has no directory next to it. The build
(`tools/build-server-android.ts`) replaces those reads with the content
of the files, so the server code in `packages/web` stays as it is:

| What | Where | How |
| --- | --- | --- |
| The settings registry | `lib/opencode/settings-files.js`, `lib/opencode/settings-helpers.js` | The content of `settings-registry.json` goes into the bundle |
| The gatekeeper program | `lib/spaces/gatekeeper-channel.js` | The content of `gatekeeper-program.cjs` goes into the bundle |
| The command line entry | `lib/opencode/cli-entry-runtime.js` | The entry is turned off |

The last one matters. A compiled binary is always the main module, so the
server would start itself a second time with the command line options and
the phone would run two servers on two ports. The phone starts the server
through the app, so the command line entry does not run at all.

The build fails when one of the three patches does not match. A server that
changed under the build is a build that cannot be trusted.

### Why the phone trusts no proxy

The server believes the forwarded headers only from a proxy on the machine
or on a private network, and it asks the `proxy-addr` library to compile
that list. On a phone the library says that `127.0.0.1` is not an address,
and the server does not start.

The phone has no proxy in front of its server: the app talks to the server
over the loopback interface, and nothing else can reach it. So the build
turns the trust off on the phone and leaves it on everywhere else. The
server still knows the address of the client, because that is the address
of the socket.

### Why the guest panel catalog lives in the app's storage

The server reads the catalog of the guest panels the app ships from a
directory next to its own modules. On the phone the app owns that directory
instead: `tools/server-entry.js` writes an empty catalog into the data
directory and hands the path to the server. The phone ships no guest panels
yet, and an empty catalog says exactly that.

### Why the server reports the version of the app

The server reads its version from `packages/web/package.json`, which a
compiled binary cannot reach. The build writes the version name of the app
into the bundle instead, so the About surface and the user agent of the
phone name the build the user actually installed.

### Why the binaries are optional except for two

The app cannot run without the server and the engine, so those two are
required and a missing one is an error the user can repair. `git`, `rg`,
`bash` and the library of the pseudoterminal are used when they are there.
The server answers with a clear error when one of them is missing, so a
build without them still runs.

## The spikes

Every spike under `tools/spikes/` answers one question that decides whether
the port is possible. A spike that misses its acceptance criterion ends the
program. It is not patched over.

| Spike | Question | Status |
| --- | --- | --- |
| `bun-android.ts` | Can a compiled Bun binary run on Android? | Answered: yes |
| OpenCode on Android | Can the engine be built and booted for Android? | Answered: yes |
| Server on Android | Can the server boot with the engine it manages? | Answered: yes |
| `pty-android.ts` | Can the phone have a pseudoterminal? | Running in CI |
| The native tools | Can the phone have git and a search? | Open |

The Bun spike runs on an Android 15 emulator in CI. The compiled binary starts,
serves HTTP on the loopback interface, reads and writes files, spawns a child
process, opens a `bun:sqlite` database and holds a WebSocket. Bun is therefore
the runtime the server will use on the phone.

The engine spike compiles the CLI of OpenCode 2.0.32 for Android the way
upstream compiles it for the desktop, with three parts turned off: the
bundled web interface, the file watcher and the terminal of the engine. The
build of the plugin runtime is the part upstream gets right and that is easy
to miss: the engine resolves the names of its runtime packages at runtime, so
the build has to write them into the bundle as static requires. Without that
the engine starts and dies on `effect/package.json`.

The engine answers on an Android 15 emulator with the version `2.0.32` and
serves its info route. The emulator has no bash, so the shell of the agent
falls back to the shell of Android.

The server spike boots the server on the emulator the way the app boots it,
with the engine in the folder the server is told about, and asks the server
for its health and for the health of the engine. The server answers both.

## The native tools the phone still needs

The server drives three programs that a desktop has and Android does not.

| What | What the server does with it | What the phone needs |
| --- | --- | --- |
| A pseudoterminal | `bun-pty` opens a shell for the terminal | A build of the library of `bun-pty` for Android |
| `git` | The server runs `git` from `PATH` for the source control surface | A build of git for Android, or a read-only surface |
| `rg` | The server runs `rg` for the file search, and answers 501 without it | A build of ripgrep for Android |

The pseudoterminal is the closest of the three. The library of `bun-pty` is
Rust, and it links against the C library of the device. The C library of
Android keeps `openpty`, `forkpty` and `login_tty` in its own header
(`<pty.h>`) instead of the header of the desktop, so the build needs the
compiler of the NDK and nothing else. The `bun-pty` package takes the path
of the library through `BUN_PTY_LIB`, so the app ships its own library and
the server finds it.

The library reaches the C library through the `termios` crate of Rust, and
that crate knows nothing about Android. The settings of a terminal have the
same layout there as on Linux, so the build fetches the crate, tells it
about Android, and uses it instead of the one from the registry.

`git` and `rg` are the open part. Ripgrep 15.0.0 builds for Android the
same way the library of the terminal does, and the server finds it on the
path of the app. Git is a different kind of work, and the plan leaves the
decision to the maintainer. The three ways out, and what each one costs:

| Way | What it gives | What it costs |
| --- | --- | --- |
| A build of git for Android | The source control surface works as it does on the desktop | Git is a C program with a chain of libraries under it. A build without curl and without TLS is enough for local repositories, and it is days of work in CI |
| No git on the phone | The source control surface is read-only: the files of the project, no changes | Nothing to build. The plan counts this as the fallback |
| Git in the app, with JGit | The changes list, the diff and the commit work on the phone | The app stops being a client of the server for this surface, and the logic of the server is written twice |

The recommendation is the second one for the first release of the phone, and
the first one after that. The third one is the wrong shape: the app exists
to be the interface of the server, and the server is the part that knows how
OpenCode works.



## Verification

The Kotlin code is checked by Gradle, not by the repository's Bun gate:

```bash
cd packages/android
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

CI builds the debug APK and runs the Bun spike on an emulator. The workflows
are `.github/workflows/android-native.yml` for the build and
`.github/workflows/android-spikes.yml` for the spikes.

The APK carries the engine and the server in its assets, and the app
extracts them into its own storage on the first launch. The debug APK is
about 350 MB, which the plan counts as the price of shipping the engine
with the app. The build that produces it:

```bash
mkdir -p packages/android/prebuilt
OPENCODE_CLI_DIR=opencode/packages/cli \
OPENCODE_OUTFILE=packages/android/prebuilt/opencode \
OPENCODE_TARGET=bun-linux-aarch64-android \
bun packages/android/tools/build-opencode-android.ts

SERVER_OUTFILE=packages/android/prebuilt/openchamber-server \
SERVER_TARGET=bun-linux-aarch64-android \
bun packages/android/tools/build-server-android.ts
```

The Gradle build picks the binaries up from `packages/android/prebuilt/`.
A build without them still works: the app reports the missing binary and
the screens that do not need the server still open.
