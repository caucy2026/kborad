#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)

eval "$("$ROOT_DIR/scripts/setup-local-native-deps.sh" | sed 's/^/export /')"

cd "$ROOT_DIR"
PATH="$GETTEXT_BIN_DIR:$PATH" ECM_DIR="$ECM_DIR" ./gradlew :app:assembleDebug "$@"