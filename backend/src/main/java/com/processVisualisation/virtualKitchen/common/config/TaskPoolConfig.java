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

    /**
     * Registers the {@link TaskPool} that runs individual visualization
     * generation tasks, sized via the {@code app.taskpool.visualization.n-threads}
     * property (defaults to 4 threads).
     *
     * @param taskPoolFactory factory used to create or reuse the named pool
     * @param nThreads number of worker threads to size this pool with
     * @return the {@code "visualization"} {@link TaskPool}
     */
    @Bean
    public TaskPool visualizationTaskPool(
            TaskPoolFactory taskPoolFactory,
            @Value("${app.taskpool.visualization.n-threads:4}") int nThreads) {
        return taskPoolFactory.getOrCreate("visualization", nThreads);
    }

    /**
     * Registers the {@link TaskPool} that runs the coordinating
     * (orchestrator) tasks which wait on the visualization pool's workers,
     * sized via the {@code app.taskpool.orchestrator.n-threads} property
     * (defaults to 2 threads). Kept as a separate named pool from
     * {@link #visualizationTaskPool} so orchestrator tasks can never deadlock
     * waiting on a saturated visualization pool.
     *
     * @param taskPoolFactory factory used to create or reuse the named pool
     * @param nThreads number of worker threads to size this pool with
     * @return the {@code "visualization-orchestrator"} {@link TaskPool}
     */
    @Bean
    public TaskPool visualizationOrchestratorTaskPool(
            TaskPoolFactory taskPoolFactory,
            @Value("${app.taskpool.orchestrator.n-threads:2}") int nThreads) {
        return taskPoolFactory.getOrCreate("visualization-orchestrator", nThreads);
    }

    /**
     * Registers the {@link TaskPool} that bounds text-to-text AI calls
     * (recipe generation, chat) submitted via {@code
     * ai.queue.AiRequestQueueService#executeBounded}, sized via the {@code
     * app.taskpool.ai-text.n-threads} property (defaults to 4 threads). Text
     * generation previously ran entirely unbounded on the servlet thread;
     * this is the pool that closes that gap.
     *
     * @param taskPoolFactory factory used to create or reuse the named pool
     * @param nThreads number of worker threads to size this pool with
     * @return the {@code "ai-text"} {@link TaskPool}
     */
    @Bean
    public TaskPool aiTextTaskPool(
            TaskPoolFactory taskPoolFactory,
            @Value("${app.taskpool.ai-text.n-threads:4}") int nThreads) {
        return taskPoolFactory.getOrCreate("ai-text", nThreads);
    }

    /**
     * Registers the {@link TaskPool} that runs the coordinating task for an
     * async recipe-flow generation job, sized via the {@code
     * app.taskpool.flow-generation-orchestrator.n-threads} property (defaults
     * to 2 threads). Kept separate from {@link #aiTextTaskPool} for the same
     * reason {@link #visualizationOrchestratorTaskPool} is kept separate from
     * {@link #visualizationTaskPool}: the orchestrator task blocks waiting on
     * {@code AiRequestQueueService#executeBounded}, which itself submits to
     * and blocks on the "ai-text" pool — running both on the same pool could
     * deadlock a saturated pool against itself.
     *
     * @param taskPoolFactory factory used to create or reuse the named pool
     * @param nThreads number of worker threads to size this pool with
     * @return the {@code "flow-generation-orchestrator"} {@link TaskPool}
     */
    @Bean
    public TaskPool flowGenerationOrchestratorTaskPool(
            TaskPoolFactory taskPoolFactory,
            @Value("${app.taskpool.flow-generation-orchestrator.n-threads:2}") int nThreads) {
        return taskPoolFactory.getOrCreate("flow-generation-orchestrator", nThreads);
    }
}
