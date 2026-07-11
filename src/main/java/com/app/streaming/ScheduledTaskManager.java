package com.app.streaming;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

@Component
public class ScheduledTaskManager {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    /**
     * Schedule a debounced task under a given key.
     * Cancels any existing task with the same key before scheduling the new one.
     */
    public void debounce(String key, Runnable task, long delayMs) {
        ScheduledFuture<?> existing = timers.remove(key);
        if (existing != null) existing.cancel(false);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            timers.remove(key);
            task.run();
        }, delayMs, TimeUnit.MILLISECONDS);

        timers.put(key, future);
    }

    public void cancel(String key) {
        ScheduledFuture<?> existing = timers.remove(key);
        if (existing != null) existing.cancel(false);
    }
}
