#!/usr/bin/env bash
# Runs the compiled Bun binary on a booted emulator.
#
# The script is called by the Android emulator runner in CI. It pushes the
# x86_64 build onto the device, makes it executable and runs it there. The
# checks inside the binary decide the exit code, so a failed check fails the
# job.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../../.." && pwd)"
cd "$root"

adb root || true
adb wait-for-device

adb shell 'mkdir -p /data/local/tmp/bun-spike'
adb push out/bun-android-x64 /data/local/tmp/bun-android
adb shell 'chmod 755 /data/local/tmp/bun-android'
adb shell 'SPIKE_WORK_DIR=/data/local/tmp/bun-spike /data/local/tmp/bun-android'
