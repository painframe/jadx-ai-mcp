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

## 18 — multiple GUI instances

Selective instance supervision from DongShuaike (`7923a331690b8ac0f4e8f392af9b8f6ff4be3365`). Window-specific plugin ownership and socket cleanup replace the JVM-global singleton shutdown. Occupied ports try at most 16 adjacent ports; `JADX_MCP_PORT`/`jadx.mcp.port` selects the initial port. Health now returns its actual JSON response with service identity, unique instance ID, GUI mode, project name and supported endpoint names. Credential-free PID/host/port discovery records use private local files and are removed on stop. Source caches and progress are window-scoped; serialized search admission prevents progress overlap within an instance. Live HTTP tests start two independent servers and prove stopping one preserves the other; tracker state isolation is checked. This does not coordinate separate desktop users or make manually initiated GUI edits participate in HTTP scheduling.

## 20 — runtime diagnostics

Selective diagnostics from DongShuaike (`7923a331690b8ac0f4e8f392af9b8f6ff4be3365`). Authenticated `GET /runtime-diagnostics` uses the control lane to report sampled JVM uptime/version, heap/nonheap byte usage, thread counts, garbage collection totals, scheduler occupancy and the current instance's bounded cache statistics. GUI and headless share `RuntimeDiagnostics.snapshot`. No environment variables, command-line arguments, stack traces or credentials are returned. Diagnostics remain read-only and do not expose forced GC, resets or shutdown. Tests sample metrics while a mutation lease is held and check memory units/state plus excluded sensitive configuration. This is an instantaneous JVM sample, not distributed tracing or a claim of workload-level throughput.

## 21 — headless analysis backend

Selective headless architecture from ngkz (`e8d11033a6751f0174ad7b4b00a886d9d3df972d`). A persistent JADX API backend shares the existing route implementations via suppliers; original GUI constructors and success shapes remain. Standalone search uses the existing isolated regex matcher, while GUI search keeps its providers. Health advertises precise endpoint capabilities; GUI selection/debug/edit routes return 501. Auth, scheduling, diagnostics, per-instance progress, project generations and bounded source caches are shared. One asynchronous replacement loader preserves the old project on failed input, uses an exclusive project lease through swap, and invalidates cursors/caches only after successful replacement. ZIP structure is validated because JADX can otherwise treat malformed archives as generic resources.

Validation: Java17 Maven package passes 48 tests. Three actual headless HTTP tests cover authenticated GET/POST, GUI-only 501, source and UTF-8 continuation reconstruction, signatures, metadata, real compiled native declarations with pagination/package boundaries, batches with mixed outcomes, xrefs, JVM information, APK resources, two-server progress isolation, failed replacement preservation and successful APK/JAR swaps with stale-cursor rejection. Existing GUI-focused tests also pass after supplier refactoring. No real desktop interaction or representative large APK throughput is claimed. Search/load cancellation remains cooperative at JADX API boundaries; expired loads cannot replace the project, but admission remains blocked until an in-flight load exits. See `docs/HEADLESS.md`.

## 22 — reproducible builder and headless runtime image

Selective delivery from xjoker (`30337f6f072aa360e9099c99e99fb330d44e8986`) and ngkz (`e8d11033a6751f0174ad7b4b00a886d9d3df972d`). Digest-pinned Maven/JDK and runtime JRE, SHA256-checked JADX1.5.5 download, tested plugin artifact target, separate runtime stage, strict Docker context allowlist and UID10001 runtime. Runtime secrets and analysis archives are read-only mounts, outside the image. The host smoke script uses an ephemeral token and loopback-published port; its expected Docker subprocess/bind security boundaries are documented beside narrowly scoped scanner annotations.

Validation: actual Docker build runs all48 Maven tests, then image `sha256:e6a188d962d8b1cdb37cd029b51a32bc26ec160955e596e5382a373ff51622dd` passes `python scripts/smoke-headless-container.py`: UID10001, authenticated health and Calculator source, unauthenticated401, GUI-only501 and clean SIGTERM exit143. Ruff and Bandit pass the new Python smoke script. Docker --init handles child reaping. No image registry publication, deployment, cross-architecture testing or general workload performance is claimed; dependencies must remain available at their pinned upstream sources.

## 23 — paired Compose stack and optional authenticated GUI

Selective deployment structure from xjoker (`30337f6f072aa360e9099c99e99fb330d44e8986`). Compose connects two headless instances and the authenticated Python MCP bridge using a canonical local registry and read-only analyst policy. Headless backends remain on an internal network; the bridge and optional GUI additionally use a frontend network for published loopback ports. The optional UID10001 GUI image uses pinned Debian snapshot packages, Xvfb, password-protected x11vnc and noVNC. Startup requires both backend and GUI secret files; passwords are not embedded in YAML, image layers or command arguments. JVM user.home is explicitly set because numeric UIDs lack passwd entries.

Validation: Docker GUI build and ShellCheck pass; missing-secret startup exits1. Actual isolated Compose validation uses real Java GUI startup, both real headless backends, final Python bridge image and authenticated remote FastMCP calls. Evidence covers five GUI fixture classes, backend bearer authentication, RFB password-only negotiation and incorrect-password rejection, primary JAR/secondary APK source separation, read-policy mutation rejection, diagnostics, read-only mounts and cleanup. The first runtime probes found and corrected the numeric-UID Java home and internal-only published-port issues. No TLS proxy, public deployment, cross-platform desktop behavior or production sizing is claimed.

## 25 — paired reproducible benchmark launcher and final validation

Selective benchmark/fixture intent from xjoker (`30337f6f072aa360e9099c99e99fb330d44e8986`). A small fail-fast shell launcher invokes the paired Python repository's frozen-uv benchmark harness, preserving the caller's fixture/output paths. One implementation owns bounded request concurrency, source digest equivalence, raw failures/partial results and complete-success latency statistics. The documented JAR/APK matrix records matching runtime conditions and links the shared checked-in evidence instead of copying divergent result files. Initial through14 and through20 real-JADX/test-window measurements are fixture observations, not a general speedup claim; the coordinator records the final stack run separately.

Final review also strengthens fixture format checks (class magic, APK DEX and manifest), excludes commented-out routes from the inventory with a regression test, separates shell assignments so failed substitutions are not masked by readonly/local builtins, and documents sha256sum as a fixture verification dependency. These address concrete review findings without changing production analysis behavior.

Validation: Java17 Maven package passes50 tests; ShellCheck passes the launcher, build helper and fixture builder; the launcher --help works against the paired Python checkout. The prior actual Docker artifact export and authenticated headless/container/Compose probes remain recorded in features21–23. No representative large-APK benchmark, device execution, cold-start gain, total RSS bound or universal performance improvement is claimed.
