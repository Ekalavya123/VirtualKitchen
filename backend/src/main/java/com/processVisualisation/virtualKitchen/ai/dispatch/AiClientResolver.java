package com.processVisualisation.virtualKitchen.ai.dispatch;

import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.restclient.client.tts.TtsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Maps a {@link ModelDefinition} to the concrete {@code AIClient}/{@code
 * ImageGenerationClient}/{@code TtsProvider} bean that should handle it. Spring
 * auto-populates the constructor-injected maps below with every bean of that
 * type, keyed by bean name — each provider client is registered under a stable
 * name (see {@code GeminiClient}, {@code OpenAIClient}, {@code OllamaClient},
 * {@code GeminiImageClient}, {@code DrawThingsImageClient}, {@code
 * GeminiTtsProvider}, {@code LocalTtsProvider}) specifically so they can all
 * coexist here instead of only one being selectable at boot time.
 */
@Component
public class AiClientResolver {

    private final Map<String, AIClient> textClients;
    private final Map<String, ImageGenerationClient> imageClients;
    private final Map<String, TtsProvider> ttsProviders;

    @Autowired
    public AiClientResolver(Map<String, AIClient> textClients,
                            Map<String, ImageGenerationClient> imageClients,
                            Map<String, TtsProvider> ttsProviders) {
        this.textClients = textClients;
        this.imageClients = imageClients;
        this.ttsProviders = ttsProviders;
    }

    /** For callers (mostly tests) that never resolve a speech model. */
    public AiClientResolver(Map<String, AIClient> textClients, Map<String, ImageGenerationClient> imageClients) {
        this(textClients, imageClients, Map.of());
    }

    public AIClient resolveTextClient(ModelDefinition model) {
        AIClient client = textClients.get(model.getProviderBean());
        if (client == null) {
            throw new IllegalStateException(
                    "No AIClient bean named '" + model.getProviderBean() + "' for model '" + model.getKey() + "'");
        }
        return new MeteredAIClient(new LoggingAIClient(client, LoggingAIClient.providerOf(model.getProviderBean())));
    }

    public ImageGenerationClient resolveImageClient(ModelDefinition model) {
        ImageGenerationClient client = imageClients.get(model.getProviderBean());
        if (client == null) {
            throw new IllegalStateException(
                    "No ImageGenerationClient bean named '" + model.getProviderBean() + "' for model '" + model.getKey() + "'");
        }
        return new LoggingImageGenerationClient(
                client, LoggingAIClient.providerOf(model.getProviderBean()), model.getProviderModelId());
    }

    public TtsProvider resolveTtsProvider(ModelDefinition model) {
        TtsProvider provider = ttsProviders.get(model.getProviderBean());
        if (provider == null) {
            throw new IllegalStateException(
                    "No TtsProvider bean named '" + model.getProviderBean() + "' for model '" + model.getKey() + "'");
        }
        return new LoggingTtsProvider(provider, model.getKey());
    }
}
