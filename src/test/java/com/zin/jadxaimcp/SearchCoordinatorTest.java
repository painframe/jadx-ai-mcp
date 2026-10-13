package com.zin.jadxaimcp;
import com.zin.jadxaimcp.utils.SearchCoordinator;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class SearchCoordinatorTest {
    @Test void staleWaiterCannotEvictReplacementEntry() {
        SearchCoordinator<Object> coordinator = new SearchCoordinator<>();
        Object old = coordinator.get("key", Object::new); coordinator.remove("key", old);
        Object replacement = coordinator.get("key", Object::new); coordinator.remove("key", old);
        assertSame(replacement, coordinator.get("key", Object::new));
    }
    @Test void concurrentIdenticalSearchRunsOnceAndGenerationChangesKey() throws Exception {
        SearchCoordinator<Integer> coordinator = new SearchCoordinator<>(); AtomicInteger count = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> coordinator.get("generationA", () -> {
                started.countDown(); try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new IllegalStateException(e); }
                return count.incrementAndGet();
            }));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            Future<Integer> second = pool.submit(() -> coordinator.get("generationA", count::incrementAndGet));
            release.countDown(); assertEquals(1, first.get()); assertEquals(1, second.get());
            assertEquals(2, coordinator.get("generationB", count::incrementAndGet));
        } finally { pool.shutdownNow(); }
    }
}
