package com.processVisualisation.virtualKitchen.common.concurrent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class TaskPoolFactoryTest {

    @Test
    void getOrCreate_returnsSameInstanceForSameName() {
        TaskPoolFactory factory = new TaskPoolFactory();

        TaskPool first = factory.getOrCreate("visualization", 4);
        TaskPool second = factory.getOrCreate("visualization", 4);

        assertSame(first, second);
        factory.shutdownAll();
    }

    @Test
    void getOrCreate_returnsDistinctInstancesForDifferentNames() {
        TaskPoolFactory factory = new TaskPoolFactory();

        TaskPool visualization = factory.getOrCreate("visualization", 4);
        TaskPool orchestrator = factory.getOrCreate("visualization-orchestrator", 2);

        assertNotSame(visualization, orchestrator);
        assertEquals(2, orchestrator.nThreads());
        assertEquals(4, visualization.nThreads());
        factory.shutdownAll();
    }
}
