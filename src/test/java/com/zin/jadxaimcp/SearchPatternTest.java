package com.zin.jadxaimcp;
import com.zin.jadxaimcp.utils.SearchPattern;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class SearchPatternTest {
    @Test void respectsCaseAndPreservesRegexEscapes() {
        try (SearchPattern regex = new SearchPattern("\\S+", true, false)) { assertTrue(regex.matches("Text")); }
        assertFalse(new SearchPattern("Ab", false, true).matches("ab"));
        assertTrue(new SearchPattern("Ab", false, false).matches("ab"));
        assertThrows(IllegalArgumentException.class, () -> new SearchPattern("[", true, false));
    }
    @Test void killsBacktrackingAndZeroWidthLoops() {
        for (String expression : new String[]{"(a+)+$", "(?:()){2147483647}"}) {
            assertTimeout(Duration.ofSeconds(5), () -> {
                try (SearchPattern pattern = new SearchPattern(expression, true, true, TimeUnit.MILLISECONDS.toNanos(20))) {
                    assertThrows(IllegalArgumentException.class, () -> pattern.matches("a".repeat(100000) + "!"));
                }
            });
        }
    }
    @Test void rejectsInvalidCompileAndReapsChild() {
        assertThrows(IllegalArgumentException.class, () -> new SearchPattern("(".repeat(512), true, true));
        long before = ProcessHandle.current().children().count();
        try (SearchPattern pattern = new SearchPattern("a", true, true)) { assertTrue(pattern.matches("a")); }
        assertEquals(before, ProcessHandle.current().children().count());
    }
    @Test void boundsInputAndRejectsInvalidFlags() {
        assertThrows(IllegalArgumentException.class, () -> SearchPattern.flag("yes"));
        try (SearchPattern pattern = new SearchPattern("a", true, true)) {
            assertThrows(IllegalArgumentException.class, () -> pattern.matches("a".repeat(4194305)));
        }
    }
}
