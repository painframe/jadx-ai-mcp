package com.zin.jadxaimcp.server;

import java.lang.management.*;
import java.time.Instant;
import java.util.*;

/** Bounded read-only metrics shared by GUI and headless servers. No environment or stack dumps. */
public final class RuntimeDiagnostics {
    private RuntimeDiagnostics() { }
    public static Map<String, Object> snapshot(RequestScheduler scheduler, Map<String, Object> cache) {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        List<Map<String, Object>> collectors = new ArrayList<>();
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            collectors.add(Map.of("name", collector.getName(), "collections", collector.getCollectionCount(), "collection_time_ms", collector.getCollectionTime()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sampled_at", Instant.now().toString());
        result.put("jvm", Map.of("version", System.getProperty("java.version"), "uptime_ms", runtime.getUptime(), "available_processors", Runtime.getRuntime().availableProcessors()));
        result.put("memory", Map.of("heap", usage(memory.getHeapMemoryUsage()), "non_heap", usage(memory.getNonHeapMemoryUsage())));
        result.put("threads", Map.of("live", threads.getThreadCount(), "daemon", threads.getDaemonThreadCount(), "peak", threads.getPeakThreadCount()));
        result.put("garbage_collectors", collectors);
        result.put("scheduler", scheduler.snapshot()); result.put("cache", new LinkedHashMap<>(cache));
        return result;
    }
    private static Map<String, Long> usage(MemoryUsage usage) {
        return Map.of("used_bytes", usage.getUsed(), "committed_bytes", usage.getCommitted(), "max_bytes", usage.getMax());
    }
}
