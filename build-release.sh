#!/data/data/com.termux/files/usr/bin/bash

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
DOWNLOAD_DIR="$HOME/storage/downloads"

echo "========================================"
echo "      Big Calendar - Release Build"
echo "========================================"
echo

cd "$PROJECT_DIR"

echo "[1/3] Gerando APK Release..."
./gradlew assembleRelease

echo
echo "[2/3] Verificando APK..."

if [ ! -f "$APK" ]; then
    echo "ERRO: APK não encontrado:"
    echo "$APK"
    exit 1
fi

echo "APK encontrado:"
echo "$APK"

echo
echo "[3/3] Copiando para Downloads..."

mkdir -p "$DOWNLOAD_DIR"

cp -f "$APK" "$DOWNLOAD_DIR/Big_Calendar-release.apk"

echo
echo "========================================"
echo "             BUILD CONCLUÍDA"
echo "========================================"
echo
echo "APK:"
echo "$DOWNLOAD_DIR/Big_Calendar-release.apk"
echo
ls -lh "$DOWNLOAD_DIR/Big_Calendar-release.apk"
echo
