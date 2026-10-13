package com.zin.jadxaimcp;
import com.zin.jadxaimcp.server.PluginServer;
import com.zin.jadxaimcp.utils.SearchProgressTracker;
import jadx.gui.ui.MainWindow;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MultiInstanceTest {
    @Test void independentServersSurviveOtherInstanceShutdown() throws Exception {
        MainWindow first = mock(MainWindow.class, RETURNS_DEEP_STUBS), second = mock(MainWindow.class, RETURNS_DEEP_STUBS);
        PluginServer a = new PluginServer(first, 0), b = new PluginServer(second, 0);
        try {
            a.start(); b.start(); assertNotEquals(a.getPort(), b.getPort()); assertNotEquals(a.instanceId(), b.instanceId());
            String one = health(a); assertTrue(one.contains(a.instanceId())); assertTrue(one.contains("class-info"));
            a.stop(); assertTrue(health(b).contains(b.instanceId()));
            SearchProgressTracker.forWindow(first).startSearch("first", 100);
            assertEquals("idle", SearchProgressTracker.forWindow(second).getProgress().get("state"));
        } finally { a.stop(); b.stop(); }
    }
    private String health(PluginServer server) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getPort() + "/health")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); return response.body();
    }
}
