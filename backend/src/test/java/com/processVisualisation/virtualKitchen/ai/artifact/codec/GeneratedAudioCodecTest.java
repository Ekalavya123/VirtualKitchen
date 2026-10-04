package com.processVisualisation.virtualKitchen.ai.artifact.codec;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactPayload;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactPayloadKind;
import com.processVisualisation.virtualKitchen.restclient.client.tts.AudioFormat;
import com.processVisualisation.virtualKitchen.restclient.client.tts.GeneratedAudio;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GeneratedAudioCodecTest {

    private final GeneratedAudioCodec codec = new GeneratedAudioCodec();

    @Test
    void roundTripsBytesAndMetadata() {
        GeneratedAudio audio = new GeneratedAudio(new byte[]{1, 2, 3}, "audio/wav", AudioFormat.WAV, 4200L,
                "Kore; odd=voice", "gemini-2.5-flash-preview-tts");

        AiArtifactPayload payload = codec.encode(audio);
        GeneratedAudio decoded = codec.decode(payload);

        assertEquals(AiArtifactPayloadKind.BINARY, payload.kind());
        assertArrayEquals(audio.data(), decoded.data());
        assertEquals(AudioFormat.WAV, decoded.format());
        assertEquals("audio/wav", decoded.mimeType());
        assertEquals(4200L, decoded.durationMs());
        assertEquals("Kore; odd=voice", decoded.voice());
        assertEquals("gemini-2.5-flash-preview-tts", decoded.providerModelId());
    }

    @Test
    void decodesAPayloadWithoutMetadata() {
        GeneratedAudio decoded = codec.decode(AiArtifactPayload.binary("audio/mpeg", new byte[]{9}));

        assertEquals(AudioFormat.MP3, decoded.format());
        assertNull(decoded.durationMs());
        assertNull(decoded.voice());
    }
}
