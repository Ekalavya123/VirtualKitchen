package com.processVisualisation.virtualKitchen.common.concurrent;

import com.processVisualisation.virtualKitchen.common.logging.MdcKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** The submitting thread's MDC (request ID etc.) must follow work onto pool threads, and never stay behind. */
class ThreadPoolTaskPoolMdcTest {

    private final ThreadPoolTaskPool pool = new ThreadPoolTaskPool(1, "mdc-test");

    @AfterEach
    void tearDown() {
        pool.shutdown();
        MDC.clear();
    }

    @Test
    void submitCarriesCallerMdcToWorker() throws Exception {
        MDC.put(MdcKeys.REQUEST_ID, "req-aaaa1111");
        MDC.put(MdcKeys.JOB_ID, "job-1");

        TaskResult<Map<String, String>> result = pool.submit(new NamedTask<>("t1", MDC::getCopyOfContextMap))
                .get(5, TimeUnit.SECONDS);

        assertThat(result.value())
                .containsEntry(MdcKeys.REQUEST_ID, "req-aaaa1111")
                .containsEntry(MdcKeys.JOB_ID, "job-1");
    }

    @Test
    void workerDoesNotKeepPreviousTaskMdc() throws Exception {
        MDC.put(MdcKeys.REQUEST_ID, "req-aaaa1111");
        pool.submit(new NamedTask<>("a", () -> {
            // A task may add keys of its own; they must not survive it either.
            MDC.put(MdcKeys.JOB_ID, "job-from-task-a");
            return null;
        })).get(5, TimeUnit.SECONDS);

        MDC.clear();
        // Same single worker thread, submitted from a thread with no MDC.
        TaskResult<Map<String, String>> second = pool.submit(new NamedTask<>("b", MDC::getCopyOfContextMap))
                .get(5, TimeUnit.SECONDS);

        assertThat(second.value()).isNullOrEmpty();
    }

    @Test
    void failingTaskStillRestoresWorkerMdc() throws Exception {
        MDC.put(MdcKeys.REQUEST_ID, "req-failing1");
        TaskResult<Object> failed = pool.submit(new NamedTask<>("boom", () -> {
            throw new IllegalStateException("boom");
        })).get(5, TimeUnit.SECONDS);
        assertThat(failed.isSuccess()).isFalse();

        MDC.clear();
        TaskResult<Map<String, String>> next = pool.submit(new NamedTask<>("next", MDC::getCopyOfContextMap))
                .get(5, TimeUnit.SECONDS);
        assertThat(next.value()).isNullOrEmpty();
    }

    @Test
    void submitAllCarriesMdcIntoTasksAndCompletionCallback() {
        MDC.put(MdcKeys.REQUEST_ID, "req-batch001");
        Map<String, String> seenByCallback = new ConcurrentHashMap<>();

        BatchResult<String> batch = pool.submitAll(
                List.of(new NamedTask<>("s1", () -> MDC.get(MdcKeys.REQUEST_ID)),
                        new NamedTask<>("s2", () -> MDC.get(MdcKeys.REQUEST_ID))),
                result -> seenByCallback.put(result.taskId(), String.valueOf(MDC.get(MdcKeys.REQUEST_ID))));

        assertThat(batch.results()).extracting(TaskResult::value).containsOnly("req-batch001");
        assertThat(seenByCallback).containsOnly(Map.entry("s1", "req-batch001"), Map.entry("s2", "req-batch001"));
    }

    /** Requests submitted concurrently from different threads each get their own ID on the shared workers. */
    @Test
    void concurrentSubmittersKeepTheirOwnIds() throws Exception {
        ThreadPoolTaskPool shared = new ThreadPoolTaskPool(2, "mdc-shared");
        ExecutorService callers = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        Map<String, String> mismatches = new ConcurrentHashMap<>();
        List<java.util.concurrent.Future<Object>> futures = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 100; i++) {
                String id = "req-" + String.format("%08d", i);
                futures.add(callers.submit(() -> {
                    start.await();
                    MDC.put(MdcKeys.REQUEST_ID, id);
                    try {
                        String seen = shared.submit(new NamedTask<>(id, () -> MDC.get(MdcKeys.REQUEST_ID)))
                                .get(10, TimeUnit.SECONDS).value();
                        if (!id.equals(seen)) {
                            mismatches.put(id, String.valueOf(seen));
                        }
                    } finally {
                        MDC.clear();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<Object> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            assertThat(new HashMap<>(mismatches)).isEmpty();
        } finally {
            callers.shutdownNow();
            shared.shutdown();
        }
    }
}
