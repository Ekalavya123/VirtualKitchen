package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.analytics.AiAnalyticsRecorder;
import com.processVisualisation.virtualKitchen.ai.analytics.model.AnalyticsStatus;
import com.processVisualisation.virtualKitchen.ai.analytics.model.OperationContext;
import com.processVisualisation.virtualKitchen.ai.dispatch.LoggingAIClient;
import com.processVisualisation.virtualKitchen.ai.dispatch.MeteredAIClient;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.AiModelRegistry;
import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.usage.AiUsage;
import com.processVisualisation.virtualKitchen.ai.usage.AiUsageMeter;
import com.processVisualisation.virtualKitchen.common.logging.MdcKeys;
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.config.GeminiProperties;
import com.processVisualisation.virtualKitchen.restclient.config.OpenAIProperties;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Default {@link IAIService} implementation that builds a chat request for
 * the configured AI provider (OpenAI or Gemini, selected via the
 * {@code ai.provider} property) and delegates the actual call to
 * {@link AIClient}.
 * <p>
 * Chat does not go through the AI request queue (no credits are reserved for it), but every call is still recorded
 * in AI analytics, with its output kept in {@code ai_responses}.
 */
@Service
public class AIServiceImpl implements IAIService {

    private final AIClient aiClient;
    private final OpenAIProperties openAIProperties;
    private final GeminiProperties geminiProperties;
    private final String provider;
    private final AiAnalyticsRecorder analytics;
    private final AiModelRegistry modelRegistry;
    private final IAIResponseService aiResponseService;

    public AIServiceImpl(AIClient aiClient,
                         OpenAIProperties openAIProperties,
                         ObjectProvider<GeminiProperties> geminiPropertiesProvider,
                         @Value("${ai.provider:gemini}") String provider,
                         AiAnalyticsRecorder analytics,
                         AiModelRegistry modelRegistry,
                         IAIResponseService aiResponseService) {
        // The injected client is the @Primary provider bean, not one resolved from the model registry.
        String providerLabel = LoggingAIClient.providerOf(StringUtils.uncapitalize(aiClient.getClass().getSimpleName()));
        this.aiClient = new MeteredAIClient(new LoggingAIClient(aiClient, providerLabel), providerLabel);
        this.openAIProperties = openAIProperties;
        this.geminiProperties = geminiPropertiesProvider.getIfAvailable();
        this.provider = provider == null ? "gemini" : provider;
        this.analytics = analytics;
        this.modelRegistry = modelRegistry;
        this.aiResponseService = aiResponseService;
    }

    /**
     * Builds a chat request with a fixed system prompt and the configured
     * provider's default model, then sends it via {@link AIClient}.
     *
     * @param prompt the free-form user prompt
     * @return the AI provider's response; {@code analyticsRequestId} is set when the call and its output were
     *         recorded (the caller then needs no {@code ai_responses} record of its own)
     */
    @Override
    public AIResponse chat(String prompt) {
        String defaultModel = openAIProperties.getDefaultModel();
        if ("gemini".equalsIgnoreCase(provider) && geminiProperties != null && geminiProperties.getDefaultModel() != null) {
            defaultModel = geminiProperties.getDefaultModel();
        }

        AIRequest request = AIRequest.builder()
                .systemPrompt("You are a helpful assistant for Virtual Kitchen.")
                .userPrompt(prompt)
                .model(defaultModel)
                .temperature(0.2d)
                .maxTokens(300)
                .operation("CHAT")
                .build();

        String analyticsId = analytics.begin(new AiAnalyticsRecorder.Begin(null, currentUserId(),
                AiCapability.TEXT_TO_TEXT, OperationContext.CHAT, "chat", null, null, null, null, null));
        AiAnalyticsRecorder.RequestTracker tracker = analytics.tracker(analyticsId, OperationContext.CHAT);
        // Priced with the registry entry for the model id actually sent, when there is one.
        Optional<ModelDefinition> model = registryModelFor(request.getModel());
        model.ifPresent(definition -> {
            tracker.model(definition);
            analytics.modelResolved(analyticsId, definition, false, "NONE", 0, null, null, null);
        });

        AiUsage usage = new AiUsage();
        usage.setCallListener(tracker);
        tracker.markWorkStarted();
        AIResponse response;
        try {
            response = AiUsageMeter.measure(usage, () -> aiClient.chat(request));
        } catch (RuntimeException e) {
            analytics.fail(analyticsId, e, 1, tracker.workStartedAt());
            throw e;
        } catch (Exception e) {
            analytics.fail(analyticsId, e, 1, tracker.workStartedAt());
            throw new IllegalStateException(e);
        }
        analytics.complete(analyticsId, AnalyticsStatus.SUCCEEDED, null, 1, tracker.workStartedAt());

        String outputId = tracker.lastTextOutputId();
        if (response != null && outputId != null) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("rawResponse", response.getRawResponse());
            boolean success = response.getContent() != null && !response.getContent().isEmpty();
            if (aiResponseService.annotate(outputId, "chat", success, extra).isPresent()) {
                response.setAnalyticsRequestId(analyticsId);
            }
        }
        return response;
    }

    private Optional<ModelDefinition> registryModelFor(String providerModelId) {
        if (providerModelId == null) {
            return Optional.empty();
        }
        return modelRegistry.byCapability(AiCapability.TEXT_TO_TEXT).stream()
                .filter(model -> providerModelId.equals(model.getProviderModelId()))
                .findFirst();
    }

    private static Long currentUserId() {
        String userId = MDC.get(MdcKeys.USER_ID);
        try {
            return userId == null ? null : Long.valueOf(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
