#!/bin/bash
set -euo pipefail
if [[ ! -s /run/secrets/gui-password || ! -s /run/secrets/backend-token ]]; then
    echo 'GUI requires nonempty gui-password and backend-token secret files' >&2
    exit 1
fi
if ! LC_ALL=C awk 'NR == 1 {exit(length($0) < 8 || $0 ~ /[^[:print:]]/)}' /run/secrets/gui-password; then
    echo 'GUI password must start with at least eight printable ASCII characters' >&2
    exit 1
fi
children=()
cleanup() {
    trap - EXIT TERM INT
    if ((${#children[@]})); then
        kill "${children[@]}" 2>/dev/null || true
        wait "${children[@]}" 2>/dev/null || true
    fi
}
trap cleanup EXIT TERM INT
Xvfb :99 -screen 0 1280x800x24 -nolisten tcp &
children+=("$!")
for ((attempt=0; attempt<50; attempt++)); do
    [[ -S /tmp/.X11-unix/X99 ]] && break
    sleep 0.1
done
[[ -S /tmp/.X11-unix/X99 ]] || exit 1
x11vnc -display :99 -forever -shared -localhost -rfbport 5900 -passwdfile /run/secrets/gui-password -quiet &
children+=("$!")
websockify --web=/usr/share/novnc 6080 localhost:5900 &
children+=("$!")
java -Duser.home=/tmp -Djadx.mcp.host=0.0.0.0 -Djadx.mcp.port=8650 -Djadx.mcp.tokenFile=/run/secrets/backend-token \
    -cp '/opt/plugin/jadx-ai-mcp-6.4.1.jar:/opt/jadx/lib/jadx-1.5.5-all.jar' jadx.gui.JadxGUI "$@" &
children+=("$!")
wait -n "${children[@]}"
