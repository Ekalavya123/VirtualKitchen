package com.processVisualisation.virtualKitchen.ai.narration.storage;

import com.processVisualisation.virtualKitchen.restclient.client.tts.AudioFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * Serves narration files written by {@link FileSystemNarrationAudioStorage}. Returning a
 * {@link Resource} lets Spring answer HTTP Range requests, which browsers use for seeking and
 * which some require before they will play audio at all. File names are random and never reused,
 * so responses are cacheable forever.
 */
@RestController
@ConditionalOnProperty(name = "ai.narration.storage.type", havingValue = "filesystem", matchIfMissing = true)
public class NarrationAudioController {

    private final FileSystemNarrationAudioStorage storage;

    public NarrationAudioController(FileSystemNarrationAudioStorage storage) {
        this.storage = storage;
    }

    @GetMapping(FileSystemNarrationAudioStorage.URL_PATH + "{fileName}")
    public ResponseEntity<Resource> audio(@PathVariable String fileName) {
        return storage.resolve(fileName)
                .filter(Files::isRegularFile)
                .<ResponseEntity<Resource>>map(path -> ResponseEntity.ok()
                        .contentType(mediaTypeOf(fileName))
                        .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                        .body(new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static MediaType mediaTypeOf(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
        for (AudioFormat format : AudioFormat.values()) {
            if (format.extension().equals(extension)) {
                return MediaType.parseMediaType(format.mimeType());
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
