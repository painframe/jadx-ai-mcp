#!/usr/bin/env bash
set -euo pipefail
readonly source_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
readonly stage="$(mktemp -d)"
trap 'rm -rf -- "$stage"' EXIT
find "$source_dir/src" -name '*.java' -print > "$stage/sources"
javac --release 11 -d "$stage/classes" @"$stage/sources"
jar --create --file "$stage/fixture.jar" -C "$stage/classes" .
install -m 644 "$stage/fixture.jar" "$source_dir/../../jadx-test-library-1.0.0.jar"
