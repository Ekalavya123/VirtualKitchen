package com.processVisualisation.virtualKitchen.ai.dispatch;

import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientResponseException;

/**
 * Wraps a provider {@link AIClient} so every provider call is logged once, with the same fields whichever
 * provider served it: {@code ai_call_completed} (INFO) with duration, token usage and input/output sizes, or
 * {@code ai_call_failed} (WARN, no stack — the exception propagates to whoever handles it).
 * <p>
 * Prompts and responses are never logged here. They go only to the {@value #PAYLOAD_LOGGER} logger, which is
 * off unless explicitly enabled, truncated, and never includes the system prompt.
 */
public class LoggingAIClient implements AIClient {

    public static final String PAYLOAD_LOGGER = "virtualKitchen.ai.payload";

    private static final Logger log = LoggerFactory.getLogger(LoggingAIClient.class);
    private static final Logger payloadLog = LoggerFactory.getLogger(PAYLOAD_LOGGER);
    private static final int MAX_PAYLOAD_CHARS = 2_000;

    private final AIClient delegate;
    private final String provider;

    public LoggingAIClient(AIClient delegate, String provider) {
        this.delegate = delegate;
        this.provider = provider;
    }

    @Override
    public AIResponse chat(AIRequest request) {
        String operation = request.getOperation() != null ? request.getOperation() : "UNSPECIFIED";
        int inputChars = length(request.getSystemPrompt()) + length(request.getUserPrompt());
        log.debug("event=ai_call_started operation={} provider={} model={} inputChars={} cacheSystemPrompt={}",
                operation, provider, request.getModel(), inputChars, request.isCacheSystemPrompt());
        if (payloadLog.isDebugEnabled()) {
            payloadLog.debug("event=ai_call_prompt operation={} userPrompt={}", operation, truncate(request.getUserPrompt()));
        }

        long startedAt = System.nanoTime();
        AIResponse response;
        try {
            response = delegate.chat(request);
        } catch (RuntimeException ex) {
            log.warn("event=ai_call_failed operation={} provider={} model={} durationMs={} errorType={} status={}",
                    operation, provider, request.getModel(), elapsedMs(startedAt),
                    ex.getClass().getSimpleName(), httpStatusOf(ex));
            throw ex;
        }

        long durationMs = elapsedMs(startedAt);
        if (response == null) {
            log.info("event=ai_call_completed operation={} provider={} model={} durationMs={} inputChars={} outputChars=0",
                    operation, provider, request.getModel(), durationMs, inputChars);
            return null;
        }
        log.info("event=ai_call_completed operation={} provider={} model={} durationMs={} inputTokens={} cachedTokens={} "
                        + "outputTokens={} thoughtsTokens={} inputChars={} outputChars={} finishReason={}",
                operation, provider, response.getModel(), durationMs, response.getPromptTokens(),
                response.getCachedTokens(), response.getCompletionTokens(), response.getThoughtsTokens(),
                inputChars, length(response.getContent()), response.getFinishReason());
        if (payloadLog.isDebugEnabled()) {
            payloadLog.debug("event=ai_call_response operation={} content={}", operation, truncate(response.getContent()));
        }
        return response;
    }

    /** Provider label for a client bean name, e.g. {@code geminiAiClient} or {@code geminiImageClient} to {@code gemini}. */
    public static String providerOf(String beanName) {
        if (beanName == null) {
            return "unknown";
        }
        for (String suffix : new String[]{"AiClient", "ImageClient", "Client"}) {
            if (beanName.endsWith(suffix) && beanName.length() > suffix.length()) {
                return beanName.substring(0, beanName.length() - suffix.length());
            }
        }
        return beanName;
    }

    /** The HTTP status behind a provider failure, if there was one (the typed AI exceptions wrap it as the cause). */
    static Integer httpStatusOf(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof RestClientResponseException rre) {
                return rre.getStatusCode().value();
            }
        }
        return null;
    }

    static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_PAYLOAD_CHARS) {
            return value;
        }
        return value.substring(0, MAX_PAYLOAD_CHARS) + "...[truncated " + (value.length() - MAX_PAYLOAD_CHARS) + " chars]";
    }
}
