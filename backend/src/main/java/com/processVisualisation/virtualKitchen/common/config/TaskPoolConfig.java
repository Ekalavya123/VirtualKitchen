package com.processVisualisation.virtualKitchen.common.config;

import com.processVisualisation.virtualKitchen.common.concurrent.TaskPool;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskPoolFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the named {@link TaskPool}s used by the async visualization
 * pipeline. Two separate pools are used deliberately: the orchestrator pool
 * only ever runs coordinating tasks that block waiting on the visualization
 * pool's workers, so sharing one pool between them could deadlock a
 * saturated pool against itself.
 */
@Configuration
public class TaskPoolConfig {

    @Bean
    public TaskPool visualizationTaskPool(
            TaskPoolFactory taskPoolFactory,
            @Value("${app.taskpool.visualization.n-threads:4}") int nThreads) {
        return taskPoolFactory.getOrCreate("visualization", nThreads);
    }

    @Bean
    public TaskPool visualizationOrchestratorTaskPool(
            TaskPoolFactory taskPoolFactory,
            @Value("${app.taskpool.orchestrator.n-threads:2}") int nThreads) {
        return taskPoolFactory.getOrCreate("visualization-orchestrator", nThreads);
    }
}
