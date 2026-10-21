#!/usr/bin/env bash
# Builds the shared library of the pseudoterminal for Android.
#
# The library is the Rust part of the terminal of bun-pty. It links against
# the C library of the device, so the build needs the compiler of the NDK.
# The C library of Android keeps the pseudoterminal in its own header, so no
# other source is needed.
#
# Usage:
#
#   ANDROID_NDK_HOME=<ndk> bash build-pty-android.sh \
#     aarch64-linux-android <path to the crate> out/librust_pty.so
set -euo pipefail

target="${1:?the rust target, for example aarch64-linux-android}"
crate="${2:?the folder of the crate that holds the library}"
outfile="${3:?the file to write the library to}"
api="${ANDROID_API_LEVEL:-21}"
ndk="${ANDROID_NDK_HOME:?the NDK of the Android SDK is needed}"

case "$target" in
  aarch64-linux-android) triple="aarch64-linux-android" ;;
  x86_64-linux-android) triple="x86_64-linux-android" ;;
  *)
    echo "unknown target: $target" >&2
    exit 1
    ;;
esac

bin="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
clang="$bin/${triple}${api}-clang"
if [ ! -x "$clang" ]; then
  echo "the compiler of the NDK is missing: $clang" >&2
  exit 1
fi

export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$clang"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$clang"

cd "$crate"
cargo build --release --target "$target"
cp "target/$target/release/librust_pty.so" "$outfile"
echo "built $outfile for $target"
