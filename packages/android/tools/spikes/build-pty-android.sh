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

# The build runs in the folder of the crate, so the file to write is named
# before the build moves there.
mkdir -p "$(dirname "$outfile")"
outfile="$(cd "$(dirname "$outfile")" && pwd)/$(basename "$outfile")"

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

# The terminal of the Rust code needs the `nix` library, and `nix` needs the
# `termios` crate. That crate knows nothing about Android, although the
# settings of a terminal have the same layout there as on Linux. So the crate
# is fetched, told about Android, and used instead of the one from the
# registry.
termios_dir="$(dirname "$crate")/termios-0.2.2"
if [ ! -d "$termios_dir" ]; then
  archive="$(mktemp -d)/termios.crate"
  curl -sSL "https://crates.io/api/v1/crates/termios/0.2.2/download" -o "$archive"
  tar xzf "$archive" -C "$(dirname "$termios_dir")"
  rm -rf "$(dirname "$archive")"
fi
sed -i 's/#\[cfg(target_os = "linux")\]/#[cfg(any(target_os = "linux", target_os = "android"))]/g' \
  "$termios_dir/src/os/mod.rs"
if ! grep -q "termios = { path" "$crate/Cargo.toml"; then
  printf '\n[patch.crates-io]\ntermios = { path = "../termios-0.2.2" }\n' >> "$crate/Cargo.toml"
fi

cd "$crate"
cargo build --release --target "$target"
cp "target/$target/release/librust_pty.so" "$outfile"
echo "built $outfile for $target"
