# Standalone analysis backend

The headless entrypoint owns a persistent `JadxDecompiler` and exposes the same read handlers as the GUI plugin. It requires Java 17 and the supported JADX 1.5.5 all-in-one JAR; no display, `MainWindow`, or Swing event loop is created.

```sh
java -Djava.awt.headless=true \
  -cp 'target/jadx-ai-mcp-6.4.1.jar:/opt/jadx/lib/jadx-1.5.5-all.jar' \
  com.zin.jadxaimcp.headless.HeadlessServer \
  --input /absolute/path/application.apk --host 127.0.0.1 --port 8650
```

Add `--token-file /run/secrets/backend-token` to authenticate every endpoint. `JADX_MCP_TOKEN`, `JADX_MCP_TOKEN_FILE`, and `JADX_MCP_HOST` use the same transport security policy as the GUI server. Binding a non-loopback address requires authentication. Keep credentials in environment or a mounted file, not command arguments.

`/health` reports `mode=headless`, a stable server instance ID, current project generation, and exact endpoint capabilities. Source, smali, metadata, native method discovery, signatures, UTF-8 continuations, batches, xrefs, resources, JVM metadata, bounded caches, search progress, and runtime diagnostics use the shared API handlers. Selection, debugging, GUI rename/comment editing and comment search require a GUI project. GUI-only endpoints return 501 `unsupported_capability`; they are absent from the health capabilities list. Headless class/method/field/code searches use the bounded regex matcher directly over JADX API objects; GUI searches retain their existing provider implementation. Deadlines are checked between classes, so one in-flight JADX decompilation can outlast a search deadline.

`POST /open-apk` accepts `{ "path": "/absolute/backend/path", "replace": true }`. A currently open project requires explicit replacement. The 202 response contains an operation ID for `/project-operation?operation_id=...`. One loader and one retained operation bound admission and status memory. Loading holds an exclusive project lease through private decompiler construction and swap. Failed input preserves the previous project and generation. Successful replacement changes generation, invalidating caches and continuation cursors. A five-minute expired operation is not allowed to replace the active project; analysis remains blocked while an uncancellable JADX load exits. Health and operation status remain available through the control lane.

Shutdown stops HTTP admission, drains the HTTP server, interrupts the loader, waits up to ten seconds, and closes the active decompiler when safe. A still-running loader is a daemon; deferred cleanup closes its preserved decompiler when it exits, without closing it concurrently. This is a cooperative boundary, not a claim that the JVM can forcibly cancel arbitrary third-party decompiler work.
