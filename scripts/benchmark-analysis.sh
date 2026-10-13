#!/bin/bash
set -euo pipefail
bridge="${JADX_MCP_BRIDGE_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/jadx-mcp-server}"
if [[ ! -f "$bridge/scripts/benchmark_analysis.py" ]]; then
    echo 'Set JADX_MCP_BRIDGE_DIR to the paired Python feat/analysis-benchmarks checkout' >&2
    exit 1
fi
exec uv run --frozen --project "$bridge" python "$bridge/scripts/benchmark_analysis.py" "$@"
