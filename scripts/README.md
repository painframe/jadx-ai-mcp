# Reproducible Java checks

Requires Docker, curl and unzip. The Maven/JDK17 image is pinned by digest in
`test-java.sh`; Java output retains the project's source/target level 11.
Bootstrap a writable external cache once (example from repository root):

```bash
export JADX_BUILD_CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/jadx-ai-mcp-build"
mkdir -p "$JADX_BUILD_CACHE"
curl -fL --max-time 120 https://github.com/skylot/jadx/releases/download/v1.5.5/jadx-1.5.5.zip -o "$JADX_BUILD_CACHE/jadx-1.5.5.zip"
printf '%s  %s\n' 38a5766d3c8170c41566b4b13ea0ede2430e3008421af4927235c2880234d51a "$JADX_BUILD_CACHE/jadx-1.5.5.zip" | sha256sum --check
unzip -q "$JADX_BUILD_CACHE/jadx-1.5.5.zip" -d "$JADX_BUILD_CACHE/jadx-1.5.5"
scripts/test-java.sh -q install:install-file -Dfile=/cache/jadx-1.5.5/lib/jadx-1.5.5-all.jar -DgroupId=io.github.skylot -DartifactId=jadx-all -Dversion=1.5.5 -Dpackaging=jar
scripts/test-java.sh -q package
```

The fixture tests load APK/JAR inputs through JADX 1.5.5; they do not launch the
GUI. Javalin 6 requires a Java 17 runtime despite the source/target setting.
