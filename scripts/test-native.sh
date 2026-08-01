#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR="$ROOT/build/native-host"
CMAKE=${CMAKE:-cmake}
if ! command -v "$CMAKE" >/dev/null 2>&1; then
    CMAKE=/opt/homebrew/share/android-commandlinetools/cmake/3.22.1/bin/cmake
fi
CTEST=$(dirname "$CMAKE")/ctest

"$CMAKE" -S "$ROOT/app/src/main/cpp" -B "$BUILD_DIR" -DREDMIKLAB_BUILD_TESTS=ON
"$CMAKE" --build "$BUILD_DIR" --parallel
"$CTEST" --test-dir "$BUILD_DIR" --output-on-failure
