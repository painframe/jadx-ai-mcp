package com.zin.jadxaimcp;
import com.zin.jadxaimcp.utils.ResponseContinuations;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class ContinuationTest {
    @Test void reconstructsUtf8AndRejectsStaleCursor() {
        String text = "a😀á漢b";
        String cursor = null;
        StringBuilder result = new StringBuilder();
        do {
            Map<String, Object> page = ResponseContinuations.chunk(text, "class:A", "generation", 4, cursor);
            result.append(page.get("content"));
            cursor = (String) ((Map<?, ?>) page.get("continuation")).get("next_cursor");
        } while (cursor != null);
        assertEquals(text, result.toString());
        Map<String, Object> page = ResponseContinuations.chunk(text, "class:A", "old", 4, null);
        String stale = (String) ((Map<?, ?>) page.get("continuation")).get("next_cursor");
        assertThrows(IllegalArgumentException.class, () -> ResponseContinuations.chunk(text, "class:A", "new", 4, stale));
        assertThrows(IllegalArgumentException.class, () -> ResponseContinuations.chunk(text, "class:B", "old", 4, stale));
        assertThrows(IllegalArgumentException.class, () -> ResponseContinuations.chunk("😀", "A", "g", 1, null));
    }
}
