# Feature integration ledger

| Feature | Branch | Provenance | Validation | Paired project |
|---|---|---|---|---|
| 01 Analysis fixtures | feat/analysis-test-fixtures | xjoker/jadx-ai-mcp@30337f6f072aa360e9099c99e99fb330d44e8986 | Maven test/package; actual JADX loads APK/JAR fixtures; baseline route inventory | painframe/jadx-mcp-server feat/analysis-test-fixtures |

Tests use disposable APK/JAR fixtures, never a user project. Donor binaries and build sources are preserved together; rebuilding APK requires Android SDK. No device execution is claimed.

| 03 Structured errors | feat/structured-error-contract | ai-repo-save/jadx-ai-mcp@6118c438dd22950b92d606891b7ad89c892c3698 | Maven package; error envelope and invalid pagination tests | painframe/jadx-mcp-server feat/structured-error-contract |
