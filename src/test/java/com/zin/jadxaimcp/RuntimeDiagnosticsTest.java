package com.zin.jadxaimcp;
import com.zin.jadxaimcp.server.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeDiagnosticsTest {
    @Test void reportsBoundedMetricsDuringMutationWithoutPrivateConfiguration() throws Exception {
        RequestScheduler scheduler = new RequestScheduler();
        try (RequestScheduler.Lease mutation = scheduler.acquire("/rename-class")) {
            Map<String,Object> metrics = RuntimeDiagnostics.snapshot(scheduler, Map.of("entries", 2));
            assertEquals(true, ((Map<?,?>) metrics.get("scheduler")).get("mutation_active"));
            assertEquals(2, ((Map<?,?>) metrics.get("cache")).get("entries"));
            assertTrue((long) ((Map<?,?>) metrics.get("jvm")).get("uptime_ms") >= 0);
            Map<?,?> heap = (Map<?,?>) ((Map<?,?>) metrics.get("memory")).get("heap");
            assertTrue((long) heap.get("used_bytes") <= (long) heap.get("committed_bytes"));
            String json = new com.google.gson.Gson().toJson(metrics);
            assertFalse(json.contains("java.class.path")); assertFalse(json.contains("Authorization"));
        }
    }
}
