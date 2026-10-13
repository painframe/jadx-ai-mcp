package com.zin.jadxaimcp.utils;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Shares work without spawning more threads; completed entries expire after thirty seconds. */
public final class SearchCoordinator<T> {
    private static final int MAX_ENTRIES = 16;
    private final LinkedHashMap<String, Entry<T>> entries = new LinkedHashMap<>();
    public T get(String key, Supplier<T> compute) {
        Entry<T> entry;
        boolean owner = false;
        synchronized (entries) {
            long now = System.nanoTime();
            entries.values().removeIf(value -> value.task.isDone() && now - value.finished > TimeUnit.SECONDS.toNanos(30));
            entry = entries.get(key);
            if (entry == null) {
                if (entries.size() >= MAX_ENTRIES) {
                    Iterator<Entry<T>> candidates = entries.values().iterator();
                    while (candidates.hasNext()) { if (candidates.next().task.isDone()) { candidates.remove(); break; } }
                }
                if (entries.size() >= MAX_ENTRIES) throw new CapacityException();
                entry = new Entry<>(compute); entries.put(key, entry); owner = true;
            }
        }
        if (owner) { entry.task.run(); entry.finished = System.nanoTime(); }
        try { return entry.task.get(10, TimeUnit.MINUTES); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalArgumentException("Search wait cancelled", error); }
        catch (ExecutionException error) {
            synchronized (entries) { entries.remove(key, entry); }
            if (error.getCause() instanceof RuntimeException) throw (RuntimeException) error.getCause();
            throw new IllegalArgumentException("Search failed", error.getCause());
        } catch (TimeoutException error) { throw new WaitingTimeout(error); }
    }
    public void remove(String key, T expected) {
        synchronized (entries) {
            Entry<T> entry = entries.get(key);
            if (entry == null || !entry.task.isDone()) return;
            try { if (entry.task.get() == expected) entries.remove(key, entry); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            catch (ExecutionException error) { entries.remove(key, entry); }
        }
    }
    public static final class CapacityException extends IllegalArgumentException {
        CapacityException() { super("Search capacity exhausted; retry later"); }
    }
    public static final class WaitingTimeout extends IllegalArgumentException {
        WaitingTimeout(Throwable error) { super("Shared search wait timed out", error); }
    }
    private static final class Entry<T> {
        final FutureTask<T> task;
        volatile long finished = Long.MAX_VALUE;
        Entry(Supplier<T> compute) { task = new FutureTask<>(compute::get); }
    }
}
