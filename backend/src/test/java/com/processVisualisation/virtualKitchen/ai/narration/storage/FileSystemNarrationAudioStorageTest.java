package com.processVisualisation.virtualKitchen.ai.narration.storage;

import com.processVisualisation.virtualKitchen.ai.narration.NarrationProperties;
import com.processVisualisation.virtualKitchen.ai.narration.storage.NarrationAudioStorage.StoredAudio;
import com.processVisualisation.virtualKitchen.restclient.client.tts.AudioFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemNarrationAudioStorageTest {

    @TempDir
    Path root;

    private FileSystemNarrationAudioStorage storage;

    @BeforeEach
    void setUp() {
        NarrationProperties properties = new NarrationProperties();
        properties.getStorage().getFilesystem().setRoot(root.toString());
        properties.getStorage().getFilesystem().setPublicBaseUrl("http://localhost:8080/");
        storage = new FileSystemNarrationAudioStorage(properties);
    }

    @Test
    void storesUnderAFreshNameAndServesItBack() throws IOException {
        StoredAudio first = storage.store(new byte[]{1, 2, 3}, AudioFormat.WAV, "10::step-1");
        StoredAudio second = storage.store(new byte[]{4}, AudioFormat.MP3, "10::step-1");

        assertTrue(first.url().startsWith("http://localhost:8080/api/v1/narration-audio/"));
        assertTrue(first.path().endsWith(".wav"));
        assertTrue(second.path().endsWith(".mp3"));
        assertNotEquals(first.path(), second.path(), "regenerated audio never overwrites an existing URL");
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(storage.resolve(first.path()).orElseThrow()));
        try (var files = Files.list(root)) {
            assertEquals(2, files.count(), "no temp files are left behind");
        }
    }

    @Test
    void deleteRemovesTheFileAndToleratesMissingOnes() {
        StoredAudio stored = storage.store(new byte[]{1}, AudioFormat.WAV, "10::step-1");

        storage.delete(stored.path());
        storage.delete(stored.path());

        assertFalse(Files.exists(root.resolve(stored.path())));
    }

    @Test
    void onlyIssuedFileNamesResolve() {
        assertTrue(storage.resolve("../application.properties").isEmpty());
        assertTrue(storage.resolve("..%2Fsecret.wav").isEmpty());
        assertTrue(storage.resolve("notes.txt").isEmpty());
        assertTrue(storage.resolve(null).isEmpty());
        assertTrue(storage.resolve("0b6f9c39-8a43-4f39-9a59-6f2d1d4b1c2e.wav").isPresent());
    }
}
