package com.zin.jadxaimcp.server;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

/** Credential-free local discovery records, removed when their owning server stops. */
public final class InstanceRegistry implements AutoCloseable {
    private final Path file;
    public InstanceRegistry(String id, Map<String, Object> metadata) throws IOException {
        String runtime = System.getenv("XDG_RUNTIME_DIR");
        Path directory = runtime == null ? Path.of(System.getProperty("user.home"), ".cache", "jadx-ai-mcp", "instances")
                : Path.of(runtime, "jadx-ai-mcp", "instances");
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) throw new IOException("Instance registry cannot be a symbolic link");
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        file = directory.resolve(id + ".json");
        Path temporary = Files.createTempFile(directory, "instance-", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            Files.writeString(temporary, new Gson().toJson(metadata));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    public Path path() { return file; }
    public void close() throws IOException { Files.deleteIfExists(file); }
}
