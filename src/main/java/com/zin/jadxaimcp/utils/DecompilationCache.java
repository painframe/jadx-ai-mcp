package com.zin.jadxaimcp.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jadx.gui.ui.MainWindow;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Finite LRU source cache, isolated by GUI instance and project generation. */
public class DecompilationCache {
    private static final Logger logger = LoggerFactory.getLogger(DecompilationCache.class);
    private static final int MAX_ENTRIES = 512;
    private static final int MAX_COMPRESSED_BYTES = 64 * 1024 * 1024;
    private static final int MAX_SOURCE_BYTES = 4 * 1024 * 1024;
    private static final Map<MainWindow, DecompilationCache> INSTANCES = new WeakHashMap<>();
    private static final DecompilationCache INSTANCE = new DecompilationCache(null);
    private final WeakReference<MainWindow> owner;
    private final LinkedHashMap<String, byte[]> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Integer> sizes = new HashMap<>();
    private String generation = "";
    private long hits, misses, compressedBytes, originalBytes;
    private DecompilationCache(MainWindow window) { owner = new WeakReference<>(window); }
    public static DecompilationCache getInstance() { return INSTANCE; }
    public static synchronized DecompilationCache forWindow(MainWindow window) {
        return INSTANCES.computeIfAbsent(window, DecompilationCache::new);
    }
    private void refresh() {
        MainWindow window = owner.get();
        if (window == null) return;
        String current = ProjectState.generation(window);
        if (!current.equals(generation)) { clear(); generation = current; }
    }
    public synchronized String generation() { refresh(); return generation; }
    public synchronized void put(String name, String source) { put(name, source, generation()); }
    public synchronized void put(String name, String source, String expectedGeneration) {
        refresh();
        if (!generation.equals(expectedGeneration)) return;
        if (name == null || source == null || source.length() > MAX_SOURCE_BYTES) return;
        byte[] raw = source.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (raw.length > MAX_SOURCE_BYTES) return;
        byte[] compressed = compress(raw);
        if (compressed == null) return;
        invalidate(name);
        cache.put(name, compressed); sizes.put(name, raw.length);
        compressedBytes += compressed.length; originalBytes += raw.length;
        while (cache.size() > MAX_ENTRIES || compressedBytes > MAX_COMPRESSED_BYTES) invalidate(cache.keySet().iterator().next());
    }
    public synchronized String get(String name) {
        refresh();
        byte[] bytes = cache.get(name);
        if (bytes == null) { misses++; return null; }
        String value = decompress(bytes);
        if (value == null) { invalidate(name); misses++; return null; }
        hits++; return value;
    }
    public synchronized boolean contains(String name) { refresh(); return cache.containsKey(name); }
    public synchronized void invalidate(String name) {
        byte[] removed = cache.remove(name);
        if (removed != null) { compressedBytes -= removed.length; originalBytes -= sizes.remove(name); }
    }
    public synchronized void clear() {
        cache.clear(); sizes.clear(); hits = misses = compressedBytes = originalBytes = 0;
    }
    public synchronized Map<String, Object> getStats() {
        refresh();
        Map<String, Object> result = new HashMap<>();
        result.put("cached_classes", cache.size()); result.put("hits", hits); result.put("misses", misses);
        result.put("hit_rate", hits + misses == 0 ? "N/A" : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * hits / (hits + misses)));
        result.put("compressed_bytes", compressedBytes); result.put("original_bytes", originalBytes);
        result.put("compressed_mb", String.format(java.util.Locale.ROOT, "%.1f", compressedBytes / 1048576.0));
        result.put("original_mb", String.format(java.util.Locale.ROOT, "%.1f", originalBytes / 1048576.0));
        result.put("compression_ratio", compressedBytes == 0 ? "N/A" : String.format(java.util.Locale.ROOT, "%.1fx", (double) originalBytes / compressedBytes));
        result.put("max_entries", MAX_ENTRIES); result.put("max_compressed_bytes", MAX_COMPRESSED_BYTES);
        result.put("max_source_bytes", MAX_SOURCE_BYTES); result.put("generation", generation);
        return result;
    }

    private static byte[] compress(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(256, data.length / 4));
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int compressedSize = deflater.deflate(buffer);
                if (compressedSize > 0) {
                    out.write(buffer, 0, compressedSize);
                }
            }
            return out.toByteArray();
        } catch (Exception e) {
            logger.warn("Failed to compress source: {}", e.getMessage());
            return null;
        } finally {
            deflater.end();
        }
    }

    /**
     * decompress a Deflate-compressed byte array back to a UTF-8 string.
     */
    private static String decompress(byte[] compressed) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(compressed.length * 4);
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count > 0) {
                    if (out.size() + count > MAX_SOURCE_BYTES) return null;
                    out.write(buffer, 0, count);
                } else if (!inflater.finished()) {
                    logger.warn("Failed to decompress source: inflater stalled");
                    return null;
                }
            }
            return out.toString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Failed to decompress source: {}", e.getMessage());
            return null;
        } finally {
            inflater.end();
        }
    }
}
