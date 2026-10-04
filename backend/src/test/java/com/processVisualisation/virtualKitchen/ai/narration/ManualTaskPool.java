package com.processVisualisation.virtualKitchen.ai.narration;

import com.processVisualisation.virtualKitchen.common.concurrent.BatchResult;
import com.processVisualisation.virtualKitchen.common.concurrent.NamedTask;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskPool;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** A {@link TaskPool} whose tasks only run when the test says so, making interleavings deterministic. */
class ManualTaskPool implements TaskPool {

    private final List<NamedTask<?>> queued = new ArrayList<>();

    @Override
    public <R> CompletableFuture<TaskResult<R>> submit(NamedTask<R> task) {
        queued.add(task);
        return new CompletableFuture<>();
    }

    @Override
    public <R> BatchResult<R> submitAll(List<NamedTask<R>> tasks, Consumer<TaskResult<R>> onTaskComplete) {
        throw new UnsupportedOperationException("not used by narration");
    }

    @Override
    public int nThreads() {
        return 1;
    }

    @Override
    public void shutdown() {
    }

    int queuedCount() {
        return queued.size();
    }

    /** Runs the queued task at {@code index} (removing it) on the calling thread. */
    void run(int index) throws Exception {
        queued.remove(index).task().execute();
    }

    /** Runs every queued task, including ones queued while running, in submission order. */
    void runAll() throws Exception {
        while (!queued.isEmpty()) {
            run(0);
        }
    }
}
