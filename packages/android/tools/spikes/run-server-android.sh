#!/usr/bin/env bash
# Boots the OpenChamber server on a booted emulator and asks it for its health.
#
# The script is called by the Android emulator runner in CI. It pushes the
# server and the engine onto the device, starts the server the way the app
# starts it, and asks the server for its health and for the health of the
# engine it manages. A server that does not come up, or an engine the server
# cannot reach, fails the job.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../../../.." && pwd)"
server_binary="${SPIKE_SERVER_BINARY:-$root/out/server-android-x64}"
engine_binary="${SPIKE_ENGINE_BINARY:-$root/out/opencode-android-x64}"
work_dir=/data/local/tmp/openchamber-spike

for binary in "$server_binary" "$engine_binary"; do
  if [ ! -f "$binary" ]; then
    echo "A binary is missing: $binary" >&2
    exit 1
  fi
done

# The emulator is up, but adb needs a moment before it answers.
for attempt in $(seq 1 30); do
  if adb shell true > /dev/null 2>&1; then
    break
  fi
  sleep 2
done

adb shell "rm -rf $work_dir && mkdir -p $work_dir/bin $work_dir/files $work_dir/home $work_dir/config $work_dir/data $work_dir/state $work_dir/cache"
adb push "$server_binary" /data/local/tmp/openchamber-server
adb push "$engine_binary" /data/local/tmp/openchamber-spike/bin/opencode
adb shell 'chmod 755 /data/local/tmp/openchamber-server /data/local/tmp/openchamber-spike/bin/opencode'
adb shell "mv /data/local/tmp/openchamber-server $work_dir/server"

# The app gives the server a home of its own, a data directory of its own and
# the engine in a folder of its own. The server starts the engine itself and
# keeps the password between them.
adb shell "cd $work_dir && env \
  OPENCHAMBER_RUNTIME=android \
  OPENCHAMBER_HOST=127.0.0.1 \
  OPENCHAMBER_API_ONLY=1 \
  OPENCHAMBER_DATA_DIR=$work_dir/files \
  OPENCHAMBER_BUNDLED_OPENCODE_CLI_DIR=$work_dir/bin \
  HOME=$work_dir/home \
  XDG_CONFIG_HOME=$work_dir/config \
  XDG_DATA_HOME=$work_dir/data \
  XDG_STATE_HOME=$work_dir/state \
  XDG_CACHE_HOME=$work_dir/cache \
  PATH=$work_dir/bin:/system/bin:/system/xbin \
  NO_PROXY=localhost,127.0.0.1 \
  nohup ./server > $work_dir/server.log 2>&1 &"

port=""
# The server waits for the engine before it reports its port, and the engine
# is a large binary on a slow device.
for attempt in $(seq 1 150); do
  line="$(adb shell "grep openchamber.listening $work_dir/server.log 2>/dev/null" | tr -d '\r')"
  if [ -n "$line" ]; then
    port="$(printf '%s' "$line" | sed -n 's/.*"port":\([0-9][0-9]*\).*/\1/p')"
    break
  fi
  sleep 2
done

if [ -z "$port" ]; then
  echo "The server did not report a port" >&2
  echo "--- the server log ---" >&2
  adb shell "cat $work_dir/server.log" 2>/dev/null || true
  exit 1
fi

echo "the server listens on port $port"
adb forward "tcp:$port" "tcp:$port"

health="$(curl -sf --max-time 20 "http://127.0.0.1:$port/health" 2>/dev/null || true)"
if [ -z "$health" ]; then
  echo "The server did not answer /health" >&2
  echo "--- the server log ---" >&2
  adb shell "cat $work_dir/server.log" 2>/dev/null || true
  exit 1
fi
echo "the server health: $health"
case "$health" in
  *'"status":"ok"'*) ;;
  *)
    echo "The server did not report itself as healthy" >&2
    exit 1
    ;;
esac

engine="$(curl -sf --max-time 90 "http://127.0.0.1:$port/api/opencode/health?wait=60000" 2>/dev/null || true)"
if [ -z "$engine" ]; then
  echo "The server did not answer the health of the engine" >&2
  echo "--- the server log ---" >&2
  adb shell "cat $work_dir/server.log" 2>/dev/null || true
  exit 1
fi
echo "the engine health: $engine"
case "$engine" in
  *'"healthy":true'*) ;;
  *)
    echo "The engine is not healthy" >&2
    echo "--- the server log ---" >&2
    adb shell "cat $work_dir/server.log" 2>/dev/null || true
    exit 1
    ;;
esac

adb shell "pkill -f $work_dir/server || true"
adb forward --remove "tcp:$port" || true

echo "the server runs on Android with the engine"
