#!/usr/bin/env bash
# Runs a shell on a booted emulator through the shared library of the
# pseudoterminal.
#
# The script is called by the Android emulator runner in CI. It pushes the
# library and the check onto the device and runs the check. A library that
# does not load, or a shell that does not answer, fails the job.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../../../.." && pwd)"
library="${SPIKE_PTY_LIBRARY:-$root/out/librust_pty.so}"
program="${SPIKE_PTY_PROGRAM:-$root/out/pty-android-x64}"
work_dir=/data/local/tmp/pty-spike

if [ ! -f "$library" ]; then
  echo "The library is missing: $library" >&2
  exit 1
fi
if [ ! -f "$program" ]; then
  echo "The program is missing: $program" >&2
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
adb push "$library" /data/local/tmp/pty-spike/librust_pty.so
adb push "$program" /data/local/tmp/pty-spike/pty-android
adb shell 'chmod 755 /data/local/tmp/pty-spike/pty-android /data/local/tmp/pty-spike/librust_pty.so'

output="$(adb shell "cd $work_dir && PTY_LIBRARY=$work_dir/librust_pty.so ./pty-android" 2>&1 | tr -d '\r')"
echo "$output"

case "$output" in
  *"the pseudoterminal runs a shell on Android"*) ;;
  *)
    echo "The pseudoterminal did not run a shell" >&2
    exit 1
    ;;
esac
