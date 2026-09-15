package com.processVisualisation.virtualKitchen.ai.registry;

import lombok.Data;

/**
 * One entry in the AI model registry, bound from the {@code ai.models[*]}
 * configuration list. Mirrors the existing per-provider {@code *Properties}
 * classes in {@code restclient.config} rather than replacing them:
 * {@link #getProviderModelId()} is the same model id already stored in e.g.
 * {@code GeminiProperties.defaultModel}/{@code OllamaProperties.defaultModel},
 * and {@link #getProviderBean()} names the {@code AIClient}/{@code
 * ImageGenerationClient} Spring bean (see {@code ai.dispatch.AiClientResolver})
 * that should be invoked for this model.
 */
@Data
public class ModelDefinition {

    /** Stable registry key, e.g. {@code "gemini-flash"}, {@code "ollama-qwen"}. */
    private String key;

    private AiCapability capability;

    private ModelTier tier;

    /** Bean name of the {@code AIClient} or {@code ImageGenerationClient} to invoke. */
    private String providerBean;

    /** The model id passed to the provider (e.g. {@code "qwen3:4b"}, {@code "gemini-3.5-flash"}). */
    private String providerModelId;

    /** Credits consumed per successful request. Always {@code 0} for {@link ModelTier#OPEN_SOURCE}. */
    private int creditCost;

    /** Registry key of the model to fall back to when this one's credits are exhausted, or {@code null}. */
    private String fallbackModelKey;

    /** Operator kill-switch; a disabled model is never selected. */
    private boolean enabled = true;
}
