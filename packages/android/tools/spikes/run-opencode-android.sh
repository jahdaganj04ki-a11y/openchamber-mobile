#!/usr/bin/env bash
# Runs the compiled OpenCode binary on a booted emulator.
#
# The script is called by the Android emulator runner in CI. It pushes the
# x86_64 build onto the device, makes it executable and asks the engine for
# its version. Then it boots the engine and asks it for its info route. A
# binary that cannot start, or an engine that does not answer, fails the job.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../../.." && pwd)"
binary="${SPIKE_BINARY:-$root/out/opencode-android-x64}"
expected_version="${SPIKE_VERSION:-2.0.32}"
port="${SPIKE_PORT:-45123}"
work_dir=/data/local/tmp/opencode-spike

if [ ! -f "$binary" ]; then
  echo "The compiled binary is missing: $binary" >&2
  exit 1
fi

# The emulator is up, but adb needs a moment before it answers.
for attempt in $(seq 1 30); do
  if adb shell true > /dev/null 2>&1; then
    break
  fi
  sleep 2
done

adb shell "rm -rf $work_dir && mkdir -p $work_dir"
adb push "$binary" /data/local/tmp/opencode-spike/opencode
adb shell 'chmod 755 /data/local/tmp/opencode-spike/opencode'

# The engine writes its data, its config and its logs below the work
# directory. The shell user of the emulator has no home of its own.
adb shell "chmod 777 $work_dir"

version="$(adb shell "cd $work_dir && HOME=$work_dir /data/local/tmp/opencode-spike/opencode --version" | tr -d '\r\n')"
echo "opencode --version: $version"
case "$version" in
  *"$expected_version"*) ;;
  *)
    echo "The engine reported '$version' instead of $expected_version" >&2
    exit 1
    ;;
esac

# The engine is up while the app waits for it, so the spike boots it the same
# way the app does: bound to the loopback interface, with its own data
# directory. The route is the one the OpenChamber server probes for the
# health of the engine. The engine asks for a password, and the server that
# manages an engine gives it one.
password="openchamber-spike"
adb shell "cd $work_dir && HOME=$work_dir XDG_DATA_HOME=$work_dir/data XDG_CONFIG_HOME=$work_dir/config XDG_STATE_HOME=$work_dir/state XDG_CACHE_HOME=$work_dir/cache OPENCODE_SERVER_PASSWORD=$password nohup /data/local/tmp/opencode-spike/opencode serve --port $port --hostname 127.0.0.1 > $work_dir/serve.log 2>&1 &" || true

adb forward "tcp:$port" "tcp:$port"

info=""
for attempt in $(seq 1 60); do
  info="$(curl -sf -u "opencode:$password" "http://127.0.0.1:$port/api/info" 2>/dev/null || true)"
  if [ -n "$info" ]; then
    break
  fi
  sleep 2
done

if [ -z "$info" ]; then
  echo "The engine did not answer /api/info" >&2
  echo "--- the engine log ---" >&2
  adb shell "cat $work_dir/serve.log" 2>/dev/null || true
  exit 1
fi

echo "opencode /api/info: $info"
case "$info" in
  *"$expected_version"*) ;;
  *)
    echo "The engine info does not carry the version $expected_version" >&2
    exit 1
    ;;
esac

# The engine's shell tool runs bash when the device has one and falls back
# to the system shell otherwise. The phone bundles its own bash, so the
# fallback matters.
echo "shells on the device:"
adb shell 'ls -l /system/bin/sh 2>/dev/null; command -v bash 2>/dev/null || echo "no bash"'

adb shell "pkill -f 'opencode serve' || true"
adb forward --remove "tcp:$port" || true

echo "the engine boots on Android"
