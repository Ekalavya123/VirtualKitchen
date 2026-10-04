package com.processVisualisation.virtualKitchen.restclient.client.tts;

import java.util.Locale;

/** Audio encodings narration can be stored in, with the MIME type and file extension of each. */
public enum AudioFormat {
    WAV("audio/wav", "wav"),
    MP3("audio/mpeg", "mp3"),
    OGG_OPUS("audio/ogg", "ogg");

    private final String mimeType;
    private final String extension;

    AudioFormat(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }

    /** Resolves a configured name such as {@code wav}, {@code mp3} or {@code opus}; defaults to WAV. */
    public static AudioFormat fromName(String name) {
        if (name == null) {
            return WAV;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "mp3", "mpeg" -> MP3;
            case "ogg", "opus", "ogg_opus" -> OGG_OPUS;
            default -> WAV;
        };
    }

    /** Resolves a MIME type (parameters ignored), or null if it is not one we store. */
    public static AudioFormat fromMimeType(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        String base = mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return switch (base) {
            case "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> WAV;
            case "audio/mpeg", "audio/mp3" -> MP3;
            case "audio/ogg", "audio/opus" -> OGG_OPUS;
            default -> null;
        };
    }
}
