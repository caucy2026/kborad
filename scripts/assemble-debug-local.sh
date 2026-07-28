#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
DEPS_ENV=$(mktemp "${TMPDIR:-/tmp}/kemi-native-deps.XXXXXX")
trap 'rm -f "$DEPS_ENV"' EXIT HUP INT TERM

if ! "$ROOT_DIR/scripts/setup-local-native-deps.sh" > "$DEPS_ENV"; then
	echo "Unable to prepare local native dependencies." >&2
	exit 1
fi
eval "$(sed 's/^/export /' "$DEPS_ENV")"

cd "$ROOT_DIR"
GRADLE_COMMAND=${KEMI_GRADLE_COMMAND:-./gradlew}
GRADLE_EXTRA_ARGS=${KEMI_GRADLE_EXTRA_ARGS:-}
GRADLE_LOG=$(mktemp "${TMPDIR:-/tmp}/kemi-gradle.XXXXXX")
attempt=1
while [ "$attempt" -le 3 ]; do
	# shellcheck disable=SC2086
	set +e
	PATH="$GETTEXT_BIN_DIR:$PATH" ECM_DIR="$ECM_DIR" "$GRADLE_COMMAND" :app:assembleDebug $GRADLE_EXTRA_ARGS "$@" >"$GRADLE_LOG" 2>&1
	rc=$?
	set -e
	if [ "$rc" -eq 0 ]; then
		rm -f "$GRADLE_LOG"
		exit 0
	fi
	echo "::group::Gradle attempt $attempt output (last 80 lines)"
	tail -n 80 "$GRADLE_LOG" || true
	echo "::endgroup::"
	echo "::group::Gradle attempt $attempt errors"
	grep -iE 'error|FAILED|failure|What went wrong|BUILD FAILED|Execution failed|cause|stacktrace|Caused by' "$GRADLE_LOG" | while IFS= read -r errline; do
		echo "::error::${errline}"
	done || true
	echo "::endgroup::"
	if [ "$attempt" -eq 3 ]; then
		echo "Gradle build failed after $attempt attempts." >&2
		rm -f "$GRADLE_LOG"
		exit 1
	fi
	echo "Gradle build failed; retrying ($attempt/3)..." >&2
	attempt=$((attempt + 1))
done