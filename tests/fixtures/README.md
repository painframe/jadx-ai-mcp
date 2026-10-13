# Analysis fixtures

Copied from xjoker/jadx-ai-mcp at 30337f6f072aa360e9099c99e99fb330d44e8986.
The JAR contains five application classes; the APK exposes six classes to JADX
1.5.5 (including generated resource metadata). These are disposable synthetic
samples for loading, source, fields/methods, resources and cross-reference tests;
they do not cover every tool, GUI lifecycle, native execution or real devices.

`test-app-sources/jar/build_jar.sh` rebuilds the JAR with JDK 11+ and replaces the
fixture only after successful compilation. APK sources are included for audit;
an APK rebuild requires Android SDK tooling, not included here.

The `API_KEY` and `sk_test_...` strings in the samples are intentionally fake
search targets, not credentials. Do not replace them with real credentials.
