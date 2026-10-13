# Reproducible plugin builder and headless runtime

Build the runtime using `docker build -t painframe/jadx-ai-mcp:local .`.
The Maven/JDK and JRE base images are digest pinned, and JADX1.5.5 is verified
against a pinned SHA256 before installation. The package build runs Maven tests.
The resulting image runs as UID/GID10001 and contains only the plugin and JADX
runtime artifacts, not Maven, source history or credentials.

Extract the tested plugin without starting a runtime:

```sh
docker build --target artifact --output type=local,dest=./container-artifact .
```

Run a disposable archive with a read-only mount and runtime-only token file:

```sh
docker run --rm --init -p 127.0.0.1:8650:8650 \
  --mount type=bind,src=/absolute/fixture.jar,dst=/input/fixture.jar,readonly \
  --mount type=bind,src=/absolute/backend-token,dst=/run/secrets/backend-token,readonly \
  painframe/jadx-ai-mcp:local --input /input/fixture.jar --host 0.0.0.0 \
  --port 8650 --token-file /run/secrets/backend-token
```

Ensure UID10001 can read the mounts. Docker --init forwards signals and reaps child
processes; Java owns orderly listener/decompiler shutdown. The host port remains
loopback-bound. Remote access requires a TLS proxy and the paired authenticated
Python bridge. No image publishing or production deployment is implied.

Run `python scripts/smoke-headless-container.py` after building. It creates one
randomly named disposable container, checks UID10001, real authenticated health
and fixture source, unauthenticated401 and GUI-only501, then verifies clean
SIGTERM shutdown and removes the container. Credentials are generated at runtime
and deleted with the temporary directory; no image is published.
