package com.processVisualisation.virtualKitchen.restclient.client.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.restclient.client.tts.AudioFormat;
import com.processVisualisation.virtualKitchen.restclient.client.tts.GeneratedAudio;
import com.processVisualisation.virtualKitchen.restclient.client.tts.TtsProvider;
import com.processVisualisation.virtualKitchen.restclient.client.tts.TtsRequest;
import com.processVisualisation.virtualKitchen.restclient.client.tts.WavAudio;
import com.processVisualisation.virtualKitchen.restclient.config.GeminiProperties;
import com.processVisualisation.virtualKitchen.restclient.exception.AIAuthenticationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AICommunicationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AIInvalidResponseException;
import com.processVisualisation.virtualKitchen.restclient.exception.AITimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
     * @throws AIAuthenticationException if Gemini rejects the API key (401/403)
     * @throws AICommunicationException on rate limiting (429), any other HTTP error, or invalid configuration
     * @throws AITimeoutException if the request times out or the host cannot be reached
     * @throws AIInvalidResponseException if the response is not JSON or carries no audio
     */
    @Override
    public GeneratedAudio synthesize(TtsRequest request) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new AICommunicationException("Gemini TTS is not configured (ai.gemini.api-key is empty)");
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
                throw new AIAuthenticationException("Gemini TTS authentication failed", ex);
            }
            if (status == 429) {
                throw new AICommunicationException("Gemini TTS rate limit or quota exceeded (429)", ex);
            }
            throw new AICommunicationException("Gemini TTS request failed with status: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new AITimeoutException("Gemini TTS request timed out or could not connect", ex);
        } catch (JsonProcessingException ex) {
            throw new AIInvalidResponseException("Failed to process Gemini TTS JSON payload", ex);
        } catch (IllegalArgumentException ex) {
            throw new AICommunicationException("Gemini TTS endpoint configuration is invalid", ex);
        }
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
            throw new AIInvalidResponseException("Gemini TTS returned an empty response");
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
                    throw new AIInvalidResponseException("Gemini TTS audio was not valid base64", ex);
                }
                return toGeneratedAudio(audio, inlineData.path("mimeType").asText(""), model, voice);
            }
        }
        throw new AIInvalidResponseException("Gemini TTS response did not contain audio");
    }

    private GeneratedAudio toGeneratedAudio(byte[] audio, String mimeType, String model, String voice) {
        if (audio.length == 0) {
            throw new AIInvalidResponseException("Gemini TTS returned zero bytes of audio");
        }
        AudioFormat container = AudioFormat.fromMimeType(mimeType);
        if (container != null) {
            // Already a playable container; pass it through.
            Long durationMs = container == AudioFormat.WAV ? WavAudio.durationMs(audio) : null;
            return new GeneratedAudio(audio, container.mimeType(), container, durationMs, voice, model);
        }
        // Raw PCM (audio/L16 / audio/pcm): wrap it as WAV.
        int sampleRate = sampleRateOf(mimeType);
        byte[] wav = WavAudio.fromPcm(audio, sampleRate, CHANNELS, BITS_PER_SAMPLE);
        long durationMs = WavAudio.pcmDurationMs(audio.length, sampleRate, CHANNELS, BITS_PER_SAMPLE);
        return new GeneratedAudio(wav, AudioFormat.WAV.mimeType(), AudioFormat.WAV, durationMs, voice, model);
    }

    private int sampleRateOf(String mimeType) {
        Matcher matcher = RATE.matcher(mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : properties.getTts().getSampleRateHz();
    }
}
