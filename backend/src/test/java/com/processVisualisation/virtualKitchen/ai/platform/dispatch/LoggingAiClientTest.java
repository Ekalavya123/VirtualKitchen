package com.processVisualisation.virtualKitchen.ai.platform.dispatch;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.processVisualisation.virtualKitchen.ai.provider.dto.AiRequest;
import com.processVisualisation.virtualKitchen.ai.provider.dto.AiResponse;
import com.processVisualisation.virtualKitchen.ai.provider.exception.AiCommunicationException;
import com.processVisualisation.virtualKitchen.common.logging.LogCapture;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoggingAiClientTest {

    private static final String SECRET_PROMPT = "SENTINEL_USER_PROMPT_DO_NOT_LOG";
    private static final String SECRET_SYSTEM = "SENTINEL_SYSTEM_PROMPT_DO_NOT_LOG";
    private static final String SECRET_OUTPUT = "SENTINEL_MODEL_OUTPUT_DO_NOT_LOG";

    private final AiRequest request = AiRequest.builder()
            .model("gemini-test")
            .systemPrompt(SECRET_SYSTEM)
            .userPrompt(SECRET_PROMPT)
            .operation("RECIPE_TO_FLOW")
            .build();

    @Test
    void logsOneCompletedEventWithUsageAndSizes() {
        AiResponse response = AiResponse.builder()
                .model("gemini-test-001")
                .content(SECRET_OUTPUT)
                .promptTokens(742)
                .cachedTokens(100)
                .completionTokens(518)
                .thoughtsTokens(0)
                .finishReason("STOP")
                .build();
        LoggingAiClient client = new LoggingAiClient(r -> response, "gemini");

        try (LogCapture logs = LogCapture.of(LoggingAiClient.class)) {
            assertThat(client.chat(request)).isSameAs(response);

            List<ILoggingEvent> completed = logs.events("ai_call_completed");
            assertThat(completed).hasSize(1);
            // Per-call detail is DEBUG (debug.log); the operator summary is ai_request_completed.
            assertThat(completed.get(0).getLevel()).isEqualTo(Level.DEBUG);
            assertThat(completed.get(0).getFormattedMessage()).contains(
                    "operation=RECIPE_TO_FLOW", "provider=gemini", "model=gemini-test-001", "durationMs=",
                    "inputTokens=742", "cachedTokens=100", "outputTokens=518", "finishReason=STOP",
                    "inputChars=" + (SECRET_SYSTEM.length() + SECRET_PROMPT.length()),
                    "outputChars=" + SECRET_OUTPUT.length());
            assertThat(logs.events("ai_call_started")).hasSize(1);
            assertNoPayload(logs);
        }
    }

    @Test
    void logsFailureOnceWithoutStackAndRethrows() {
        AiCommunicationException failure = new AiCommunicationException("Gemini API request failed",
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null,
                        ("{\"echo\":\"" + SECRET_PROMPT + "\"}").getBytes(), null));
        LoggingAiClient client = new LoggingAiClient(r -> {
            throw failure;
        }, "gemini");

        try (LogCapture logs = LogCapture.of(LoggingAiClient.class)) {
            assertThatThrownBy(() -> client.chat(request)).isSameAs(failure);

            List<ILoggingEvent> failed = logs.events("ai_call_failed");
            assertThat(failed).hasSize(1);
            assertThat(failed.get(0).getLevel()).isEqualTo(Level.DEBUG);
            assertThat(failed.get(0).getThrowableProxy()).as("stack is logged once, by the handling boundary").isNull();
            assertThat(failed.get(0).getFormattedMessage())
                    .contains("errorType=AiCommunicationException", "status=400", "provider=gemini");
            assertNoPayload(logs);
        }
    }

    @Test
    void payloadLoggerIsTheOnlyPlacePromptsAppearAndOnlyWhenEnabled() {
        LoggingAiClient client = new LoggingAiClient(
                r -> AiResponse.builder().content("x".repeat(5_000)).build(), "ollama");

        try (LogCapture main = LogCapture.of(LoggingAiClient.class);
             LogCapture payload = LogCapture.of(LoggingAiClient.PAYLOAD_LOGGER)) {
            client.chat(request);

            assertNoPayload(main);
            assertThat(payload.allMessages()).contains(SECRET_PROMPT).doesNotContain(SECRET_SYSTEM);
            assertThat(payload.events("ai_call_response").get(0).getFormattedMessage())
                    .contains("[truncated 3000 chars]");
        }
    }

    @Test
    void requestToStringNeverContainsPrompts() {
        assertThat(request.toString()).doesNotContain(SECRET_PROMPT).doesNotContain(SECRET_SYSTEM);
    }

    @Test
    void providerLabelFromBeanName() {
        assertThat(LoggingAiClient.providerOf("geminiAiClient")).isEqualTo("gemini");
        assertThat(LoggingAiClient.providerOf("openAiAiClient")).isEqualTo("openAi");
        assertThat(LoggingAiClient.providerOf("drawThingsImageClient")).isEqualTo("drawThings");
        assertThat(LoggingAiClient.providerOf("geminiClient")).isEqualTo("gemini");
        assertThat(LoggingAiClient.providerOf(null)).isEqualTo("unknown");
    }

    private static void assertNoPayload(LogCapture logs) {
        assertThat(logs.allMessages())
                .doesNotContain(SECRET_PROMPT)
                .doesNotContain(SECRET_SYSTEM)
                .doesNotContain(SECRET_OUTPUT);
    }
}
