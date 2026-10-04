package com.processVisualisation.virtualKitchen.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.processVisualisation.virtualKitchen.common.exception.RecipeProcessAiException;
import com.processVisualisation.virtualKitchen.restclient.exception.AITimeoutException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class FailureLoggerTest {

    private static final Logger log = LoggerFactory.getLogger(FailureLoggerTest.class);

    @Test
    void businessFailureIsWarnWithoutStack() {
        ILoggingEvent event = logOne(new RecipeProcessAiException("invalid process"));
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getFormattedMessage())
                .isEqualTo("event=job_failed jobKind=test errorType=RecipeProcessAiException error=\"invalid process\"");
    }

    @Test
    void knownExternalFailureIsErrorWithoutStack() {
        ILoggingEvent event = logOne(new AITimeoutException("timed out", new java.net.SocketTimeoutException()));
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void unexpectedFailureIsErrorWithStack() {
        ILoggingEvent event = logOne(new NullPointerException("bug"));
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getFormattedMessage()).isEqualTo("event=job_failed jobKind=test errorType=NullPointerException");
    }

    private ILoggingEvent logOne(Throwable error) {
        try (LogCapture logs = LogCapture.of(FailureLoggerTest.class)) {
            FailureLogger.logFailure(log, "job_failed", error, "jobKind=test");
            assertThat(logs.events()).hasSize(1);
            return logs.events().get(0);
        }
    }
}
