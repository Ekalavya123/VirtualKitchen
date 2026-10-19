package com.processVisualisation.virtualKitchen.ai.provider.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.ai.provider.ProviderUsage;
import com.processVisualisation.virtualKitchen.ai.provider.config.GeminiProperties;
import com.processVisualisation.virtualKitchen.ai.provider.exception.AiAuthenticationException;
import com.processVisualisation.virtualKitchen.ai.provider.exception.AiCommunicationException;
import com.processVisualisation.virtualKitchen.ai.provider.exception.AiInvalidResponseException;
import com.processVisualisation.virtualKitchen.ai.provider.exception.AiRateLimitedException;
import com.processVisualisation.virtualKitchen.ai.provider.exception.AiTimeoutException;
import com.processVisualisation.virtualKitchen.ai.provider.tts.AudioFormat;
import com.processVisualisation.virtualKitchen.ai.provider.tts.GeneratedAudio;
import com.processVisualisation.virtualKitchen.ai.provider.tts.TtsProvider;
import com.processVisualisation.virtualKitchen.ai.provider.tts.TtsRequest;
import com.processVisualisation.virtualKitchen.ai.provider.tts.WavAudio;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link TtsProvider} backed by Gemini's speech-generation models, called through the same
 * {@code generateContent} endpoint, API key and {@code geminiRestClient} as {@link GeminiImageClient}.
 * <p>
 * Gemini returns raw 16-bit little-endian mono PCM ({@code audio/L16;codec=pcm;rate=24000}), which
 * browsers cannot play directly, so it is wrapped in a WAV container here. Nothing Gemini-specific
 * leaves this class: callers only ever see a {@link GeneratedAudio}.
 */
@Component("geminiTtsProvider")
public class GeminiTtsProvider implements TtsProvider {

    private static final Pattern RATE = Pattern.compile("rate=(\\d+)");
    private static final Pattern SECONDS = Pattern.compile("(\\d+(?:\\.\\d+)?)s");
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiTtsProvider(
            @Qualifier("geminiRestClient") RestClient restClient,
            GeminiProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    /**
     * Generates speech with Gemini and returns it as WAV.
     *
     * @throws AiAuthenticationException if Gemini rejects the API key (401/403)
     * @throws AiRateLimitedException on rate limiting (429) or overload (503), which the queue retries with backoff
     * @throws AiCommunicationException on a daily quota (429), any other HTTP error, or invalid configuration
     * @throws AiTimeoutException if the request times out or the host cannot be reached
     * @throws AiInvalidResponseException if the response is not JSON or carries no audio
     */
    @Override
    public GeneratedAudio synthesize(TtsRequest request) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new AiCommunicationException("Gemini TTS is not configured (ai.gemini.api-key is empty)");
        }
        String model = StringUtils.hasText(request.providerModelId()) ? request.providerModelId() : properties.getTts().getModel();
        String voice = StringUtils.hasText(request.voice()) ? request.voice() : properties.getTts().getVoice();

        try {
            String response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.getChatEndpoint())
                            .build(model))
                    .header("x-goog-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildPayload(request.text(), voice, request.languageCode()))
                    .retrieve()
                    .body(String.class);

            return parseAudio(response, model, voice);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new AiAuthenticationException("Gemini TTS authentication failed", ex);
            }
            if (status == 429 || status == 503) {
                throw throttled(ex, status);
            }
            throw new AiCommunicationException("Gemini TTS request failed with status: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new AiTimeoutException("Gemini TTS request timed out or could not connect", ex);
        } catch (JsonProcessingException ex) {
            throw new AiInvalidResponseException("Failed to process Gemini TTS JSON payload", ex);
        } catch (IllegalArgumentException ex) {
            throw new AiCommunicationException("Gemini TTS endpoint configuration is invalid", ex);
        }
    }

    /**
     * Maps a 429/503 to a retryable {@link AiRateLimitedException} carrying Gemini's requested wait
     * (the {@code Retry-After} header, else the body's {@code RetryInfo.retryDelay}). A 429 caused
     * by a per-day quota stays a plain {@link AiCommunicationException}: waiting seconds won't help.
     */
    private AiCommunicationException throttled(RestClientResponseException ex, int status) {
        JsonNode error = readError(ex.getResponseBodyAsString());
        if (status == 429 && error.toString().contains("PerDay")) {
            return new AiCommunicationException("Gemini TTS daily quota exhausted (429)", ex);
        }
        Duration retryAfter = retryAfterHeader(ex);
        if (retryAfter == null) {
            for (JsonNode detail : error.path("details")) {
                retryAfter = parseSeconds(detail.path("retryDelay").asText(""));
                if (retryAfter != null) {
                    break;
                }
            }
        }
        String reason = status == 429 ? "rate limit or quota exceeded (429)" : "model overloaded (503)";
        return new AiRateLimitedException("Gemini TTS " + reason, retryAfter, ex);
    }

    private JsonNode readError(String body) {
        if (!StringUtils.hasText(body)) {
            return objectMapper.missingNode();
        }
        try {
            return objectMapper.readTree(body).path("error");
        } catch (JsonProcessingException ignored) {
            return objectMapper.missingNode();
        }
    }

    private static Duration retryAfterHeader(RestClientResponseException ex) {
        HttpHeaders headers = ex.getResponseHeaders();
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        return value == null ? null : parseSeconds(value.trim() + "s");
    }

    /** Parses Google's duration form ({@code "37s"}, {@code "1.5s"}); null when absent or malformed. */
    private static Duration parseSeconds(String value) {
        Matcher matcher = SECONDS.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) {
            return null;
        }
        return Duration.ofMillis(Math.round(Double.parseDouble(matcher.group(1)) * 1000));
    }

    private Map<String, Object> buildPayload(String text, String voice, String languageCode) {
        String stylePrompt = properties.getTts().getStylePrompt();
        String spoken = StringUtils.hasText(stylePrompt) ? stylePrompt.trim() + " " + text : text;

        Map<String, Object> speechConfig = new LinkedHashMap<>();
        speechConfig.put("voiceConfig", Map.of("prebuiltVoiceConfig", Map.of("voiceName", voice)));
        if (StringUtils.hasText(languageCode)) {
            speechConfig.put("languageCode", languageCode);
        }

        return Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", spoken)))),
                "generationConfig", Map.of(
                        "responseModalities", List.of("AUDIO"),
                        "speechConfig", speechConfig
                )
        );
    }

    private GeneratedAudio parseAudio(String response, String model, String voice) throws JsonProcessingException {
        if (!StringUtils.hasText(response)) {
            throw new AiInvalidResponseException("Gemini TTS returned an empty response");
        }
        JsonNode root = objectMapper.readTree(response);
        for (JsonNode candidate : root.path("candidates")) {
            for (JsonNode part : candidate.path("content").path("parts")) {
                JsonNode inlineData = part.path("inlineData");
                String base64 = inlineData.path("data").asText("");
                if (inlineData.isMissingNode() || base64.isEmpty()) {
                    continue;
                }
                byte[] audio;
                try {
                    audio = Base64.getDecoder().decode(base64);
                } catch (IllegalArgumentException ex) {
                    throw new AiInvalidResponseException("Gemini TTS audio was not valid base64", ex);
                }
                return toGeneratedAudio(audio, inlineData.path("mimeType").asText(""), model, voice, GeminiUsage.from(root));
            }
        }
        throw new AiInvalidResponseException("Gemini TTS response did not contain audio");
    }

    private GeneratedAudio toGeneratedAudio(byte[] audio, String mimeType, String model, String voice, ProviderUsage usage) {
        if (audio.length == 0) {
            throw new AiInvalidResponseException("Gemini TTS returned zero bytes of audio");
        }
        AudioFormat container = AudioFormat.fromMimeType(mimeType);
        if (container != null) {
            // Already a playable container; pass it through.
            Long durationMs = container == AudioFormat.WAV ? WavAudio.durationMs(audio) : null;
            return new GeneratedAudio(audio, container.mimeType(), container, durationMs, voice, model, usage);
        }
        // Raw PCM (audio/L16 / audio/pcm): wrap it as WAV.
        int sampleRate = sampleRateOf(mimeType);
        byte[] wav = WavAudio.fromPcm(audio, sampleRate, CHANNELS, BITS_PER_SAMPLE);
        long durationMs = WavAudio.pcmDurationMs(audio.length, sampleRate, CHANNELS, BITS_PER_SAMPLE);
        return new GeneratedAudio(wav, AudioFormat.WAV.mimeType(), AudioFormat.WAV, durationMs, voice, model, usage);
    }

    private int sampleRateOf(String mimeType) {
        Matcher matcher = RATE.matcher(mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : properties.getTts().getSampleRateHz();
    }
}
