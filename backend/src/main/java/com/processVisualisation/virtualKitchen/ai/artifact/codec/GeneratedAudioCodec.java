package com.processVisualisation.virtualKitchen.ai.artifact.codec;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactPayload;
import com.processVisualisation.virtualKitchen.restclient.client.tts.AudioFormat;
import com.processVisualisation.virtualKitchen.restclient.client.tts.GeneratedAudio;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores generated narration audio so a storage outage after a paid synthesis never forces a
 * second paid synthesis. The audio's metadata (voice, provider model, duration) rides along as
 * parameters on the stored content type, e.g. {@code audio/wav;vk-voice=Kore;vk-duration=4200}.
 */
@Component
public class GeneratedAudioCodec implements AiArtifactCodec<GeneratedAudio> {

    /** Versioned like {@link GeneratedImageCodec#ID}: register a v2 rather than change this shape. */
    public static final String ID = "generated-audio-v1";

    private static final String VOICE = "vk-voice";
    private static final String MODEL = "vk-model";
    private static final String DURATION = "vk-duration";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public AiArtifactPayload encode(GeneratedAudio value) {
        StringBuilder contentType = new StringBuilder(value.format().mimeType());
        append(contentType, VOICE, value.voice());
        append(contentType, MODEL, value.providerModelId());
        append(contentType, DURATION, value.durationMs() == null ? null : String.valueOf(value.durationMs()));
        return AiArtifactPayload.binary(contentType.toString(), value.data());
    }

    @Override
    public GeneratedAudio decode(AiArtifactPayload payload) {
        String contentType = payload.contentType() == null ? AudioFormat.WAV.mimeType() : payload.contentType();
        Map<String, String> params = new HashMap<>();
        String[] parts = contentType.split(";");
        for (int i = 1; i < parts.length; i++) {
            String[] pair = parts[i].trim().split("=", 2);
            if (pair.length == 2) {
                params.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        }
        AudioFormat format = AudioFormat.fromMimeType(parts[0]);
        if (format == null) {
            format = AudioFormat.WAV;
        }
        String duration = params.get(DURATION);
        return new GeneratedAudio(payload.asBytes(), format.mimeType(), format,
                duration == null ? null : Long.valueOf(duration), params.get(VOICE), params.get(MODEL));
    }

    private static void append(StringBuilder contentType, String name, String value) {
        if (value != null && !value.isBlank()) {
            contentType.append(';').append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
    }
}
