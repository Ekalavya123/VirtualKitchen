package com.processVisualisation.virtualKitchen.restclient.client.tts;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Minimal RIFF/WAVE helpers for narration audio: wrapping raw little-endian PCM (what Gemini TTS
 * returns) in a playable WAV container, and reading a WAV file's duration from its header.
 */
public final class WavAudio {

    private static final int HEADER_BYTES = 44;

    private WavAudio() {
    }

    /**
     * Prepends a 44-byte canonical WAV header to raw PCM samples.
     *
     * @param pcm little-endian signed PCM samples
     * @param sampleRateHz samples per second per channel
     * @param channels number of interleaved channels
     * @param bitsPerSample bits per sample (16 for Gemini TTS)
     */
    public static byte[] fromPcm(byte[] pcm, int sampleRateHz, int channels, int bitsPerSample) {
        int blockAlign = channels * bitsPerSample / 8;
        int byteRate = sampleRateHz * blockAlign;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{'R', 'I', 'F', 'F'});
        buffer.putInt(36 + pcm.length);
        buffer.put(new byte[]{'W', 'A', 'V', 'E'});
        buffer.put(new byte[]{'f', 'm', 't', ' '});
        buffer.putInt(16);                 // fmt chunk size
        buffer.putShort((short) 1);        // PCM
        buffer.putShort((short) channels);
        buffer.putInt(sampleRateHz);
        buffer.putInt(byteRate);
        buffer.putShort((short) blockAlign);
        buffer.putShort((short) bitsPerSample);
        buffer.put(new byte[]{'d', 'a', 't', 'a'});
        buffer.putInt(pcm.length);
        buffer.put(pcm);
        return buffer.array();
    }

    /** Playback length of raw PCM, in milliseconds. */
    public static long pcmDurationMs(long pcmBytes, int sampleRateHz, int channels, int bitsPerSample) {
        long bytesPerSecond = (long) sampleRateHz * channels * bitsPerSample / 8;
        return bytesPerSecond <= 0 ? 0 : pcmBytes * 1000 / bytesPerSecond;
    }

    /**
     * Reads a WAV file's duration by walking its chunks for {@code fmt } and {@code data}.
     *
     * @return the duration in milliseconds, or null if {@code wav} is not a parsable WAV
     */
    public static Long durationMs(byte[] wav) {
        if (wav == null || wav.length < 12 || !tagAt(wav, 0, "RIFF") || !tagAt(wav, 8, "WAVE")) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int byteRate = -1;
        long dataBytes = -1;
        long offset = 12;
        while (offset + 8 <= wav.length) {
            int at = (int) offset;
            long chunkSize = Integer.toUnsignedLong(buffer.getInt(at + 4));
            if (tagAt(wav, at, "fmt ") && at + 16 <= wav.length) {
                byteRate = buffer.getInt(at + 16);
            } else if (tagAt(wav, at, "data")) {
                // Streaming encoders write 0 or 0xFFFFFFFF when the length was unknown up front.
                long available = wav.length - (offset + 8);
                dataBytes = chunkSize == 0 || chunkSize > available ? available : chunkSize;
                break;
            }
            offset += 8 + chunkSize + (chunkSize % 2);
        }
        if (byteRate <= 0 || dataBytes < 0) {
            return null;
        }
        return dataBytes * 1000 / byteRate;
    }

    private static boolean tagAt(byte[] data, int offset, String tag) {
        if (offset < 0 || offset + 4 > data.length) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (data[offset + i] != tag.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
