# Feature integration ledger

| Feature | Branch | Provenance | Validation | Paired project |
|---|---|---|---|---|
| 01 Analysis fixtures | feat/analysis-test-fixtures | xjoker/jadx-ai-mcp@30337f6f072aa360e9099c99e99fb330d44e8986 | Maven test/package; actual JADX loads APK/JAR fixtures; baseline route inventory | painframe/jadx-mcp-server feat/analysis-test-fixtures |

Tests use disposable APK/JAR fixtures, never a user project. Donor binaries and build sources are preserved together; rebuilding APK requires Android SDK. No device execution is claimed.

| 03 Structured errors | feat/structured-error-contract | ai-repo-save/jadx-ai-mcp@6118c438dd22950b92d606891b7ad89c892c3698 | Maven package; error envelope and invalid pagination tests | painframe/jadx-mcp-server feat/structured-error-contract |

| 05 Search patterns | feat/search-patterns | odell0111@951d6d964ae194a45bd4735bd8839d455d0607ba | case, escapes, invalid pattern and actual matcher timeout tests | Python feat/search-patterns |

| 06 GUI search parity | feat/gui-search-parity | ai-repo-save@6118c438dd22950b92d606891b7ad89c892c3698 | Maven compile/test; safe regex offsets; provider exceptions propagate (no silent partial success) | Java-only |

| 07 Analysis metadata | feat/analysis-metadata | xjoker@30337f6f072aa360e9099c99e99fb330d44e8986 | Maven package; actual method metadata + Frida arrays | Python feat/analysis-metadata |

| 08 Native method search | feat/native-method-search | xjoker@30337f6f072aa360e9099c99e99fb330d44e8986 | package boundaries/native-only test; Maven package | Python feat/native-method-search |

| 09 Human method signatures | feat/human-method-signatures | ngkz@e8d11033a6751f0174ad7b4b00a886d9d3df972d + MrWeigx@c1e4e4e2a8 | real overload matching/descriptor tests; Maven package | Python feat/human-method-signatures |
