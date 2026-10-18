#!/usr/bin/env bash
# ByeDPI (ciadpi), hev-socks5-tunnel ve Xray'i hazırlayıp app/src/main/jniLibs altına koyar.
set -euo pipefail

ABI=arm64-v8a
API=21
# CloudStream (çalışan sürüm) ile aynı sabit sürümler
BYEDPI_COMMIT=ba532298de7b28cfe854aea83d061369d13ca290
HEV_COMMIT=4d6c334dbfb68a79d1970c2744e62d09f71df12f
XRAY_TAG=v26.9.30

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-}}"
[ -d "$NDK" ] || { echo "NDK bulunamadı"; exit 1; }
echo "NDK: $NDK"
OUT="$ROOT/app/src/main/jniLibs/$ABI"
mkdir -p "$OUT"
WORK="$(mktemp -d)"
TOOLS="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

# 1) hev-socks5-tunnel (JNI kütüphanesi, varsayılan sınıf adı hev/htproxy/TProxyService)
git clone -q https://github.com/heiher/hev-socks5-tunnel "$WORK/hev"
git -C "$WORK/hev" checkout -q "$HEV_COMMIT"
git -C "$WORK/hev" submodule update --init --recursive -q
mkdir -p "$WORK/hevmk"
printf '%s\n' 'APP_OPTIM := release' "APP_PLATFORM := android-$API" "APP_ABI := $ABI" \
  'APP_CFLAGS := -O3 -DLWIP_ERRNO_STDINCLUDE=1' \
  'NDK_TOOLCHAIN_VERSION := clang' 'APP_LDFLAGS := -Wl,-z,max-page-size=16384' > "$WORK/hevmk/Application.mk"
"$NDK/ndk-build" -j"$(nproc)" NDK_PROJECT_PATH="$WORK/hevbuild" APP_BUILD_SCRIPT="$WORK/hev/Android.mk" \
  NDK_APPLICATION_MK="$WORK/hevmk/Application.mk" NDK_LIBS_OUT="$WORK/hevlibs"
cp "$WORK/hevlibs/$ABI/libhev-socks5-tunnel.so" "$OUT/"

# 2) ByeDPI -> çalıştırılabilir dosya, lib*.so adıyla
git clone -q https://github.com/hufrea/byedpi "$WORK/byedpi"
git -C "$WORK/byedpi" checkout -q "$BYEDPI_COMMIT"
( cd "$WORK/byedpi" && "$TOOLS/aarch64-linux-android$API-clang" -std=c99 -O2 -D_DEFAULT_SOURCE -I. \
    $(ls *.c | grep -v win_service) -Wl,-z,max-page-size=16384 -o ciadpi )
cp "$WORK/byedpi/ciadpi" "$OUT/libciadpi.so"

# 3) Xray (resmi Android derlemesi)
curl -fsSL -o "$WORK/xray.zip" "https://github.com/XTLS/Xray-core/releases/download/$XRAY_TAG/Xray-android-arm64-v8a.zip"
unzip -o -q "$WORK/xray.zip" xray -d "$WORK/xray"
cp "$WORK/xray/xray" "$OUT/libxray.so"

chmod 755 "$OUT"/*.so
ls -la "$OUT"
