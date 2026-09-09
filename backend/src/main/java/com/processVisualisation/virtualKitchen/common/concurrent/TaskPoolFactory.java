package com.processVisualisation.virtualKitchen.common.concurrent;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mints and caches named {@link TaskPool}s. Any feature that needs bounded
 * concurrent execution can call {@link #getOrCreate(String, int)} without
 * this package needing to change — the reuse point the task-pool framework
 * was designed around.
 */
@Component
public class TaskPoolFactory {

    private final Map<String, TaskPool> pools = new ConcurrentHashMap<>();

    public TaskPool getOrCreate(String name, int nThreads) {
        return pools.computeIfAbsent(name, poolName -> new ThreadPoolTaskPool(nThreads, poolName));
    }

    @PreDestroy
    public void shutdownAll() {
        pools.values().forEach(TaskPool::shutdown);
    }
}
