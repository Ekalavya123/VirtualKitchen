package com.processVisualisation.virtualKitchen.restclient.client.localtts;

import com.processVisualisation.virtualKitchen.restclient.client.tts.AudioFormat;
import com.processVisualisation.virtualKitchen.restclient.client.tts.GeneratedAudio;
import com.processVisualisation.virtualKitchen.restclient.client.tts.TtsProvider;
import com.processVisualisation.virtualKitchen.restclient.client.tts.TtsRequest;
import com.processVisualisation.virtualKitchen.restclient.client.tts.WavAudio;
import com.processVisualisation.virtualKitchen.restclient.config.LocalTtsProperties;
import com.processVisualisation.virtualKitchen.restclient.exception.AIAuthenticationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AICommunicationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AIInvalidResponseException;
import com.processVisualisation.virtualKitchen.restclient.exception.AITimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link TtsProvider} for a self-hosted engine speaking the OpenAI-compatible
 * {@code POST /v1/audio/speech} contract: a JSON request of {@code model}, {@code input},
 * {@code voice} and {@code response_format}, answered with the audio bytes themselves.
 * <p>
 * The engine runs as its own process (e.g. Kokoro-FastAPI in a container), so the application
 * never depends on a model library. Replacing Kokoro with another engine that serves the same
 * contract is purely an {@code ai.local-tts.*} configuration change.
 */
@Component("localTtsProvider")
public class LocalTtsProvider implements TtsProvider {

    private final RestClient restClient;
    private final LocalTtsProperties properties;

    public LocalTtsProvider(
            @Qualifier("localTtsRestClient") RestClient restClient,
            LocalTtsProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "local";
    }

    /**
     * Requests speech from the local engine.
     *
     * @throws AIAuthenticationException if the engine rejects the configured token (401/403)
     * @throws AICommunicationException on any other HTTP error or invalid configuration
     * @throws AITimeoutException if the engine is not running, unreachable, or too slow
     * @throws AIInvalidResponseException if the engine answers with no audio
     */
    @Override
    public GeneratedAudio synthesize(TtsRequest request) {
        String model = StringUtils.hasText(request.providerModelId()) ? request.providerModelId() : properties.getModel();
        String voice = StringUtils.hasText(request.voice()) ? request.voice() : properties.getVoice();
        AudioFormat requested = AudioFormat.fromName(properties.getResponseFormat());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", request.text());
        body.put("voice", voice);
        body.put("response_format", formatName(requested));
        if (StringUtils.hasText(request.languageCode())) {
            // Ignored by engines that infer language from the voice; honoured by those that accept it.
            body.put("language", request.languageCode());
        }

        try {
            ResponseEntity<byte[]> response = restClient.post()
                    .uri(properties.getSpeechEndpoint())
                    .headers(headers -> {
                        if (StringUtils.hasText(properties.getApiKey())) {
                            headers.setBearerAuth(properties.getApiKey());
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(byte[].class);

            return toGeneratedAudio(response, requested, model, voice);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new AIAuthenticationException("Local TTS authentication failed", ex);
            }
            throw new AICommunicationException("Local TTS request failed with status: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new AITimeoutException("Local TTS engine timed out or could not be reached", ex);
        } catch (IllegalArgumentException ex) {
            throw new AICommunicationException("Local TTS endpoint configuration is invalid", ex);
        }
    }

    private GeneratedAudio toGeneratedAudio(ResponseEntity<byte[]> response, AudioFormat requested, String model, String voice) {
        byte[] audio = response.getBody();
        if (audio == null || audio.length == 0) {
            throw new AIInvalidResponseException("Local TTS engine returned no audio");
        }
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType != null && "application".equals(contentType.getType())
                && contentType.getSubtype().contains("json")) {
            throw new AIInvalidResponseException("Local TTS engine returned JSON instead of audio");
        }
        AudioFormat actual = contentType == null ? null : AudioFormat.fromMimeType(contentType.toString());
        AudioFormat format = actual != null ? actual : requested;
        Long durationMs = format == AudioFormat.WAV ? WavAudio.durationMs(audio) : null;
        return new GeneratedAudio(audio, format.mimeType(), format, durationMs, voice, model);
    }

    private static String formatName(AudioFormat format) {
        return switch (format) {
            case WAV -> "wav";
            case MP3 -> "mp3";
            case OGG_OPUS -> "opus";
        };
    }
}
