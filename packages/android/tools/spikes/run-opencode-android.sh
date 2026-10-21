#!/usr/bin/env bash
# Runs the compiled OpenCode binary on a booted emulator.
#
# The script is called by the Android emulator runner in CI. It pushes the
# x86_64 build onto the device, makes it executable and asks it for its
# version. A binary that cannot start fails the job.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../../.." && pwd)"
binary="${SPIKE_BINARY:-$root/out/opencode-android-x64}"

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

adb shell 'mkdir -p /data/local/tmp/opencode-spike'
adb push "$binary" /data/local/tmp/opencode
adb shell 'chmod 755 /data/local/tmp/opencode'
adb shell 'cd /data/local/tmp/opencode-spike && /data/local/tmp/opencode --version'
