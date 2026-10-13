# Paired plugin benchmark matrix

The Python companion owns the single benchmark implementation, avoiding divergent
statistics code in the two repositories. `scripts/benchmark-analysis.sh` locates
the sibling `jadx-mcp-server` checkout, or the explicit JADX_MCP_BRIDGE_DIR path,
and runs its bounded harness through uv. The invoking directory is retained, so
fixture and output paths refer to this repository.

Load the exact same disposable fixture into baseline and candidate processes.
Use matching JADX1.5.5/JVM17, machine and heap settings; record exact source SHAs.
Run both these rows at concurrency1 and4, with ten measured requests after warmup:

| Fixture | Target class | Coverage |
| --- | --- | --- |
| tests/fixtures/jadx-test-library-1.0.0.jar | com.jadxtest.library.Calculator | source, class search, batch, cache/diagnostics |
| tests/fixtures/jadx-test-app-1.0.0.apk | com.jadxtest.app.MainActivity | Android class/source and search |

```sh
scripts/benchmark-analysis.sh \
  --baseline-url http://127.0.0.1:8650 --baseline-revision BASE_SHA \
  --candidate-url http://127.0.0.1:8652 --candidate-revision CANDIDATE_SHA \
  --fixture tests/fixtures/jadx-test-library-1.0.0.jar \
  --class-name com.jadxtest.library.Calculator \
  --iterations 10 --concurrency 4 --output comparison.json
```

Runtime credentials come from JADX_BASELINE_TOKEN/JADX_CANDIDATE_TOKEN, never CLI
values. The harness is read-only and never clears caches or replaces loaded input.
It records fixture hashes, revision labels, complete-success median/p95 latency,
failed/partial samples, content byte counts and available JVM metrics. Equal source
hashes are required; missing/partial source cannot count as a successful comparison.

An unsupported baseline endpoint is not a fast successful request. The matrix
measures warm repeated work on small controlled fixtures, not cold startup,
large-APK scaling, peak RSS, device execution or production capacity. Do not infer
an overall speedup from an isolated case. Preserve raw reports and describe the
runtime setup (real GUI, mocked GUI, or actual headless mode) alongside results.

The checked-in raw reports and runtime provenance live in the paired repository's
[benchmark evidence directory](https://github.com/painframe/jadx-mcp-server/tree/feat/analysis-benchmarks/docs/benchmarks).
That directory distinguishes the initial through14 fixture run from subsequent
full-stack validation. The Java repository deliberately links those raw samples
rather than copying reports that can drift apart. Only compare reports with the
recorded matching fixture digest, JVM/JADX versions, runtime mode and revisions.
