package com.processVisualisation.virtualKitchen.common.logging;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Static guard rails for the logging policy, checked against the main sources. */
class LoggingHygieneTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");

    @Test
    void applicationCodeLogsThroughSlf4jNotSystemOut() throws IOException {
        // PresetUp is a standalone command-line seeding tool, not part of the running application.
        assertThat(sourcesContaining("System.out.print", "System.err.print", "printStackTrace()"))
                .allMatch(path -> path.endsWith("PresetUp.java"));
    }

    @Test
    void noConnectionStringWithEmbeddedCredentials() throws IOException {
        java.util.regex.Pattern credentialUri = java.util.regex.Pattern.compile("mongodb(\\+srv)?://[^\\s\"/@:]+:[^\\s\"@]+@");
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            List<Path> offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return credentialUri.matcher(Files.readString(p)).find();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
            assertThat(offenders).isEmpty();
        }
    }

    private static List<Path> sourcesContaining(String... needles) throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String source = Files.readString(p);
                            for (String needle : needles) {
                                if (source.contains(needle)) {
                                    return true;
                                }
                            }
                            return false;
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
        }
    }
}
