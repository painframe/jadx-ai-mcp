# Local paired analysis stack

Build `painframe/jadx-ai-mcp:local` here and
`painframe/jadx-mcp-server:local` in the paired Python repository first. Supply:

- `JADX_PRIMARY_INPUT` and `JADX_SECONDARY_INPUT`: absolute archive paths. Each
  file is mounted read-only at the same absolute path to preserve its extension.
- `JADX_BACKEND_TOKEN_FILE`: absolute path to a nonempty backend bearer token,
  readable by container UID10001.
- `ANALYST_MCP_TOKEN`: independent random ASCII client token (at least32 characters)
  supplied through your secret manager/environment. It is not stored in YAML.

Run `docker compose up -d`. Both headless instances share an isolated internal
network with the Python bridge. The bridge and optional GUI additionally join a
frontend network for their host port publishing; the headless backends remain
only on the internal network. Only authenticated MCP is published by default, on
127.0.0.1:8651. The default policy grants analysis reads, not project mutations.
Edit the local administrator policy deliberately if write access is required.
Use `docker compose down` to stop/remove these disposable services; archives are
never copied into images or modified by the stack configuration.

For the optional GUI, build `docker build -f docker/Dockerfile.gui -t
painframe/jadx-ai-mcp-gui:local .`, set `JADX_GUI_PASSWORD_FILE` to a nonempty VNC
password file containing at least eight random printable ASCII characters, readable by UID10001, and run `docker compose --profile gui up -d`.
The GUI uses Xvfb, authenticated x11vnc and noVNC at
http://127.0.0.1:6080/vnc.html. The password is read from its secret file, never
passed as a command-line argument. Classic VNC uses only the first eight password characters; use eight independent random characters and TLS for any remote access. VNC is bound only to container loopback;
there is no passwordless desktop endpoint. The GUI image pins Debian package
repositories to the2026-10-01 snapshot as well as immutable base images.

The registry contains the optional GUI ID even without that profile. It will be
reported unavailable until started. Backend ports are never published. A TLS
reverse proxy is required for access beyond host loopback; this Compose file does
not deploy one. Use `docker compose config --quiet` for validation without printing
resolved secret-bearing environment values.

The GUI explicitly sets JVM `user.home=/tmp` because a numeric container UID has
no passwd entry; setting only the HOME environment variable does not configure
JADX's Java-side settings directory. Both the GUI and bridge need the frontend
network for Docker's published loopback ports to work; an internal-only network
can make otherwise healthy listeners unreachable from the host.
