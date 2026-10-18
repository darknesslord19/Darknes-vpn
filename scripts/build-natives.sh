#!/usr/bin/env bash
# ByeDPI (ciadpi), hev-socks5-tunnel ve Xray'i hazırlayıp app/src/main/jniLibs altına koyar.
set -euo pipefail

ABI=arm64-v8a
API=26
BYEDPI_TAG=v0.17.3
HEV_TAG=2.19.0
XRAY_TAG=v26.10.17

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-}}"
[ -d "$NDK" ] || { echo "NDK bulunamadı"; exit 1; }
echo "NDK: $NDK"
OUT="$ROOT/app/src/main/jniLibs/$ABI"
mkdir -p "$OUT"
WORK="$(mktemp -d)"
TOOLS="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

# 1) hev-socks5-tunnel (JNI kütüphanesi)
git clone --depth 1 --branch "$HEV_TAG" --recursive https://github.com/heiher/hev-socks5-tunnel "$WORK/hev/jni"
( cd "$WORK/hev" && "$NDK/ndk-build" APP_ABI="$ABI" APP_PLATFORM="android-$API" -j"$(nproc)" )
cp "$WORK/hev/libs/$ABI/libhev-socks5-tunnel.so" "$OUT/"

# 2) ByeDPI -> çalıştırılabilir dosya, lib*.so adıyla
git clone --depth 1 --branch "$BYEDPI_TAG" https://github.com/hufrea/byedpi "$WORK/byedpi"
( cd "$WORK/byedpi" && "$TOOLS/aarch64-linux-android$API-clang" \
    -D_DEFAULT_SOURCE -D_GNU_SOURCE -I. -std=gnu99 -O2 -fPIE -pie -Wno-everything \
    -Wl,-z,max-page-size=16384 \
    -o ciadpi packets.c main.c conev.c proxy.c desync.c mpool.c extend.c )
"$TOOLS/llvm-strip" "$WORK/byedpi/ciadpi"
cp "$WORK/byedpi/ciadpi" "$OUT/libciadpi.so"

# 3) Xray (resmi Android derlemesi)
curl -fsSL -o "$WORK/xray.zip" "https://github.com/XTLS/Xray-core/releases/download/$XRAY_TAG/Xray-android-arm64-v8a.zip"
unzip -o -q "$WORK/xray.zip" xray -d "$WORK/xray"
cp "$WORK/xray/xray" "$OUT/libxray.so"

chmod 755 "$OUT"/*.so
ls -la "$OUT"
