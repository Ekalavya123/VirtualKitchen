package com.processVisualisation.virtualKitchen.ai.narration.storage;

import com.processVisualisation.virtualKitchen.ai.narration.NarrationProperties;
import com.processVisualisation.virtualKitchen.restclient.client.tts.AudioFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Development storage: narration files in a local directory, served back by
 * {@code NarrationAudioController}. Single-instance only, and lost on redeploy unless the directory
 * is a mounted volume — use {@code supabase} (or another object store) for shared deployments.
 */
@Component
@ConditionalOnProperty(name = "ai.narration.storage.type", havingValue = "filesystem", matchIfMissing = true)
public class FileSystemNarrationAudioStorage implements NarrationAudioStorage {

    public static final String BACKEND = "filesystem";
    public static final String URL_PATH = "/api/v1/narration-audio/";

    /** Only names this class generated can be read back, which also rules out path traversal. */
    private static final Pattern FILE_NAME = Pattern.compile("^[a-f0-9-]{36}\\.(wav|mp3|ogg)$");

    private static final Logger log = LoggerFactory.getLogger(FileSystemNarrationAudioStorage.class);

    private final Path root;
    private final String publicBaseUrl;

    public FileSystemNarrationAudioStorage(NarrationProperties properties) {
        this.root = Path.of(properties.getStorage().getFilesystem().getRoot()).toAbsolutePath().normalize();
        String baseUrl = properties.getStorage().getFilesystem().getPublicBaseUrl();
        this.publicBaseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    @Override
    public String backendName() {
        return BACKEND;
    }

    @Override
    public StoredAudio store(byte[] data, AudioFormat format, String narrationKey) {
        String fileName = UUID.randomUUID() + "." + format.extension();
        try {
            Files.createDirectories(root);
            // Write then move, so a reader never sees a half-written file.
            Path temp = Files.createTempFile(root, "narration-", ".part");
            Files.write(temp, data);
            Files.move(temp, root.resolve(fileName), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write narration audio to " + root, e);
        }
        log.debug("event=narration_file_written narrationKey={} file={} bytes={}", narrationKey, fileName, data.length);
        return new StoredAudio(publicBaseUrl + URL_PATH + fileName, BACKEND, fileName);
    }

    @Override
    public void delete(String storagePath) {
        resolve(storagePath).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("event=narration_file_delete_failed file={} errorType={}", storagePath, e.getClass().getSimpleName());
            }
        });
    }

    /** The file for a name this storage issued, or empty for anything else (including traversal attempts). */
    public Optional<Path> resolve(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        Path path = root.resolve(fileName).normalize();
        return path.startsWith(root) ? Optional.of(path) : Optional.empty();
    }
}
