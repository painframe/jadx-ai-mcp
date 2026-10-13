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

| 10 Response continuations | feat/response-continuations | xjoker@30337f6f072aa360e9099c99e99fb330d44e8986; mugiwarix limit intent | UTF-8 roundtrip/stale generation/source tests; Maven package | Python feat/response-continuations |

| 11 Batch analysis | feat/batch-analysis | xjoker@30337f6f072aa360e9099c99e99fb330d44e8986 | existing-handler capture; mixed outcomes/deny mutation tests; Maven package | Python feat/batch-analysis |

| 12 Bounded analysis caches | feat/bounded-analysis-caches | xjoker cache intent@30337f6f072aa360e9099c99e99fb330d44e8986 | finite LRU, window isolation,generation invalidation,exact size tests; Maven package | Python feat/bounded-analysis-caches |

| 13 APK loading | feat/apk-loading | hnhcsl@50781c0987f008703ee0f0c735b8e3efb611b5cd | unsaved-project replacement denial,actual load callback contract; Maven package | Python feat/apk-loading |

| 14 Search coalescing | feat/search-coalescing | xjoker@30337f6f072aa360e9099c99e99fb330d44e8986 | identical concurrent work executes once; generation key separation; Maven package | Python feat/search-coalescing |

## 15 — bounded request scheduling

Selective architecture port from DongShuaike (`7923a331690b8ac0f4e8f392af9b8f6ff4be3365`). Each server admits 4 heavy reads, 8 metadata reads and 1 search; pending admission is bounded to 64 with a 5 second deadline. Fair project read/write exclusion drains reads before rename/comment/cache/project mutations. The project-loading guard is checked again after admission and survives the asynchronous GUI load. Health, operation status and progress bypass analysis queues. Handler-scoped leases release on all exceptions; shutdown stops admission. No detached executors are introduced. Tests cover exclusion, independent metadata, saturation, actual HTTP loading/control behavior and error cleanup. GUI decompilation already running is cooperative and can delay a write; no hard deadline for JADX internals is claimed.

15 review correction: baseline rename events dispatch asynchronously twice. HTTP renames now invoke the same supported JRenameNode code-rename builder, store sorted project renames, reload code data and unload cached source synchronously while holding the write lease. Visible editor buffers and package tree refresh synchronously on the EDT before the mutation lease ends. GUI-initiated edits remain controlled by JADX and are not claimed to take the HTTP admission lock.
## 16 — JVM analysis tools

Selective JVM inspection from DongShuaike (`7923a331690b8ac0f4e8f392af9b8f6ff4be3365`). `GET /jvm-info?section=` supports manifest, services, entry-points, dependencies, properties and packages, with existing offset/limit pagination. Main-class attributes and service providers are read without executing or extracting entries; property values are decoded using Java Properties semantics. Archive work stops at 10,000 entries, 64 KiB per metadata entry and 2 MiB total; oversized inputs return explicit 413. Archive-only sections return unsupported for APK-only projects. Dependencies describe JADX's loaded-class dependency graph; they do not claim Maven dependency resolution or a full external library inventory. Tests use constructed service/manifest/properties JARs and compressed oversized entries.

## 17 — authenticated transport

Selective transport architecture from DongShuaike (`7923a331690b8ac0f4e8f392af9b8f6ff4be3365`). Default bind is now explicitly `127.0.0.1`. Set `JADX_MCP_HOST` (or `-Djadx.mcp.host`) to select an interface; non-loopback binds require a bearer secret. Configure `JADX_MCP_TOKEN` or `JADX_MCP_TOKEN_FILE` (`-Djadx.mcp.tokenFile`) with 16..4096 printable ASCII characters. File tokens are read with a size bound on every request, so atomic file replacement rotates credentials without restarting; missing/malformed files fail closed. Every route, including health/progress and mutations, uses the same constant-time credential comparison. No tokens enter startup messages. Keep token files readable only to the service account. HTTP is intended behind an authenticated private network or TLS proxy; this feature does not itself implement TLS. Live HTTP tests cover missing/wrong credentials, GET/POST, rotation and fail-closed file removal.
