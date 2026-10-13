package com.zin.jadxaimcp;
import com.zin.jadxaimcp.server.routes.BatchRoutes;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BatchTest {
    @Test void preservesIndependentOutcomesAndStructuredData() {
        Context ctx = mock(Context.class, RETURNS_SELF);
        when(ctx.body()).thenReturn("{\"operation\":\"method-by-name\",\"items\":[{\"class_name\":\"A\"},{}]}");
        new BatchRoutes(Map.of("method-by-name", item -> {
            if (item.queryParam("class_name") == null) item.status(404).json(Map.of("error", "missing", "code", "not_found"));
            else item.json(Map.of("class_name", item.queryParam("class_name")));
        }), null).handle(ctx);
        ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class); verify(ctx).json(output.capture());
        List<?> results = (List<?>) ((Map<?, ?>) output.getValue()).get("results");
        assertEquals(true, ((Map<?, ?>) results.get(0)).get("ok"));
        assertInstanceOf(Map.class, ((Map<?, ?>) results.get(0)).get("data"));
        assertEquals(false, ((Map<?, ?>) results.get(1)).get("ok"));
    }
    @Test void methodChunksReconstructAndKeepIdentifiers() {
        jadx.gui.ui.MainWindow window = mock(jadx.gui.ui.MainWindow.class, RETURNS_DEEP_STUBS);
        String code = "a😀\n".repeat(1000);
        BatchRoutes routes = new BatchRoutes(Map.of("method-by-name", item -> item.json(Map.of(
                "class_name", "Example", "method_name", "large", "decl", "large()V", "code", code))), window);
        String cursor = null;
        StringBuilder reconstructed = new StringBuilder();
        do {
            Context ctx = mock(Context.class, RETURNS_SELF);
            Map<String, Object> item = new HashMap<>(); if (cursor != null) item.put("cursor", cursor);
            when(ctx.body()).thenReturn(new com.google.gson.Gson().toJson(Map.of("operation", "method-by-name", "items", List.of(item), "max_bytes", 128)));
            routes.handle(ctx);
            ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class); verify(ctx).json(output.capture());
            Map<?, ?> entry = (Map<?, ?>) ((List<?>) ((Map<?, ?>) output.getValue()).get("results")).get(0);
            assertEquals(true, entry.get("ok"));
            Map<?, ?> data = (Map<?, ?>) entry.get("data"); assertEquals("Example", data.get("class_name"));
            reconstructed.append(data.get("content"));
            cursor = (String) ((Map<?, ?>) data.get("continuation")).get("next_cursor");
        } while (cursor != null);
        assertEquals(code, reconstructed.toString());
    }
    @Test void refusesMutationAndEmptyBatch() {
        Context ctx = mock(Context.class, RETURNS_SELF);
        when(ctx.body()).thenReturn("{\"operation\":\"rename-class\",\"items\":[]}");
        new BatchRoutes(Map.of(), null).handle(ctx); verify(ctx).status(400);
    }
}
