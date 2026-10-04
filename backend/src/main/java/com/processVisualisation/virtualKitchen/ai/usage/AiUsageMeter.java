package com.processVisualisation.virtualKitchen.ai.usage;

import com.processVisualisation.virtualKitchen.restclient.client.ProviderUsage;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;

import java.util.concurrent.Callable;

/**
 * Binds the {@link AiUsage} of the AI request job currently running on this thread, so provider responses can be
 * counted where they are produced (see {@code MeteredAIClient}) without threading a usage object through every
 * service and client signature. Calls made outside a metered job are simply not counted.
 */
public final class AiUsageMeter {

    private static final ThreadLocal<AiUsage> CURRENT = new ThreadLocal<>();

    private AiUsageMeter() {
    }

    /** Runs {@code work} with {@code usage} bound to this thread, restoring whatever was bound before. */
    public static <T> T measure(AiUsage usage, Callable<T> work) throws Exception {
        AiUsage previous = CURRENT.get();
        CURRENT.set(usage);
        try {
            return work.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** Counts one image/speech provider call against the job bound to this thread, if any. */
    public static void record(ProviderUsage providerUsage) {
        AiUsage usage = CURRENT.get();
        if (usage != null) {
            usage.add(providerUsage);
        }
    }

    /** Counts one provider response against the job bound to this thread, if any. */
    public static void record(AIResponse response) {
        AiUsage usage = CURRENT.get();
        if (usage != null) {
            usage.add(response);
        }
    }
}
