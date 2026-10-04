package com.processVisualisation.virtualKitchen.restclient.client.tts;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WavAudioTest {

    @Test
    void wrapsPcmInACanonicalHeader() {
        byte[] wav = WavAudio.fromPcm(new byte[48_000], 24000, 1, 16);
        ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(36 + 48_000, header.getInt(4));
        assertEquals("WAVE", new String(wav, 8, 4, StandardCharsets.US_ASCII));
        assertEquals(1, header.getShort(20), "PCM");
        assertEquals(1, header.getShort(22), "mono");
        assertEquals(24000, header.getInt(24));
        assertEquals(48000, header.getInt(28), "byte rate");
        assertEquals(16, header.getShort(34));
        assertEquals("data", new String(wav, 36, 4, StandardCharsets.US_ASCII));
        assertEquals(48_000, header.getInt(40));
    }

    @Test
    void durationRoundTripsThroughTheHeader() {
        assertEquals(1000L, WavAudio.durationMs(WavAudio.fromPcm(new byte[48_000], 24000, 1, 16)));
        assertEquals(250L, WavAudio.durationMs(WavAudio.fromPcm(new byte[8_000], 16000, 1, 16)));
        assertEquals(1000L, WavAudio.pcmDurationMs(48_000, 24000, 1, 16));
    }

    @Test
    void streamedWavWithUnknownDataLengthUsesTheRemainingBytes() {
        byte[] wav = WavAudio.fromPcm(new byte[48_000], 24000, 1, 16);
        ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).putInt(40, 0xFFFFFFFF);
        assertEquals(1000L, WavAudio.durationMs(wav));
    }

    @Test
    void nonWavHasNoDuration() {
        assertNull(WavAudio.durationMs(null));
        assertNull(WavAudio.durationMs(new byte[]{1, 2, 3}));
        assertNull(WavAudio.durationMs("ID3 this is an mp3 file".getBytes(StandardCharsets.US_ASCII)));
    }
}
