#!/usr/bin/env python3
"""Run a disposable authenticated fixture container and verify clean termination."""
import argparse
import http.client
import json
import secrets
import shutil

# Docker subprocesses use argv, never a shell.
import subprocess  # nosec B404
import tempfile
import time
import unittest
from pathlib import Path


def docker(*args: str) -> str:
    executable = shutil.which("docker")
    if executable is None:
        raise RuntimeError("Docker CLI is required")
    # The image is an intentional CLI argument, not shell text.
    result = subprocess.run([executable, *args], check=True, capture_output=True, text=True, timeout=30)  # nosec B603
    return result.stdout.strip()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", default="painframe/jadx-ai-mcp:local")
    args = parser.parse_args()
    fixture = Path(__file__).resolve().parents[1] / "tests/fixtures/jadx-test-library-1.0.0.jar"
    name = "jadx-headless-smoke-" + secrets.token_hex(6)
    with tempfile.TemporaryDirectory(prefix="jadx-container-") as temporary:
        token = secrets.token_urlsafe(32)
        token_file = Path(temporary) / "token"
        token_file.write_text(token)
        token_file.chmod(0o644)  # Disposable secret; container UID10001 must read the bind mount.
        # Authenticated container bind; its published host port remains loopback.
        container_host = "0.0.0.0"  # nosec B104
        try:
            docker("run", "-d", "--init", "--name", name, "-p", "127.0.0.1::8650",
                   "--mount", f"type=bind,src={fixture},dst=/input/fixture.jar,readonly",
                   "--mount", f"type=bind,src={token_file},dst=/run/secrets/token,readonly",
                   args.image, "--input", "/input/fixture.jar", "--host", container_host, "--token-file", "/run/secrets/token")
            port = int(docker("port", name, "8650/tcp").rsplit(":", 1)[1])

            def request(path: str, authenticated: bool = True) -> tuple[int, bytes]:
                headers = {"Authorization": "Bearer " + token} if authenticated else {}
                connection = http.client.HTTPConnection("127.0.0.1", port, timeout=3)
                try:
                    connection.request("GET", path, headers=headers)
                    response = connection.getresponse()
                    return response.status, response.read(1048576)
                finally:
                    connection.close()

            deadline = time.monotonic() + 30
            while True:
                try:
                    status, body = request("/health")
                    if status == 200:
                        break
                except OSError:
                    pass
                if time.monotonic() >= deadline:
                    raise RuntimeError("Container health did not become ready within 30 seconds")
                time.sleep(0.2)
            check = unittest.TestCase()
            check.assertEqual(json.loads(body)["mode"], "headless")
            check.assertEqual(request("/health", False)[0], 401)
            status, source = request("/class-source?class_name=com.jadxtest.library.Calculator")
            check.assertEqual(status, 200)
            check.assertIn(b"class Calculator", source)
            check.assertEqual(request("/current-class")[0], 501)
            check.assertEqual(docker("exec", name, "id", "-u"), "10001")
            docker("stop", "--time", "10", name)
            code = int(docker("inspect", "--format", "{{.State.ExitCode}}", name))
            check.assertIn(code, (0, 143), f"Unexpected shutdown exit {code}")
            print(json.dumps({"image": args.image, "uid": 10001, "authenticated_health": True,
                              "source_verified": True, "unauthenticated_status": 401, "shutdown_exit": code}))
        finally:
            docker("rm", "-f", name)


if __name__ == "__main__":
    main()
