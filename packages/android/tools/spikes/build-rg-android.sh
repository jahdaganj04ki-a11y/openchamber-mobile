#!/usr/bin/env bash
# Builds ripgrep for Android.
#
# The file search of the server runs `rg`. The desktop ships a binary for
# every desktop platform; the phone needs one for Android, built from the
# same source with the compiler of the NDK.
#
# Usage:
#
#   ANDROID_NDK_HOME=<ndk> bash build-rg-android.sh \
#     aarch64-linux-android <path to the checkout> out/rg
set -euo pipefail

target="${1:?the rust target, for example aarch64-linux-android}"
source="${2:?the folder of the ripgrep checkout}"
outfile="${3:?the file to write the binary to}"
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

# The standard library of the target has to be there. The build installs it
# when it is missing, so the script works with any toolchain.
if ! rustup target list --installed 2>/dev/null | grep -q "^$target$"; then
  rustup target add "$target"
fi

cd "$source"
cargo build --release --target "$target"
cp "target/$target/release/rg" "$outfile"
echo "built $outfile for $target"
