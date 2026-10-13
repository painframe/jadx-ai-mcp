#!/usr/bin/env bash
set -euo pipefail
: "${JADX_BUILD_CACHE:?Set JADX_BUILD_CACHE to a writable cache containing jadx-1.5.5/lib/jadx-1.5.5-all.jar}"
readonly image='maven@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc'
readonly root="$(cd -- "$(dirname -- "$0")/.." && pwd)"
docker run --rm --user "$(id -u):$(id -g)" -e MAVEN_CONFIG=/tmp/maven \
  -v "$JADX_BUILD_CACHE:/cache" -v "$root:/work" -w /work "$image" \
  mvn -Dmaven.repo.local=/cache/m2 "$@"
