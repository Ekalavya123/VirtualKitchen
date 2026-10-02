package com.processVisualisation.virtualKitchen.ai.dispatch;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.processVisualisation.virtualKitchen.common.logging.LogCapture;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import com.processVisualisation.virtualKitchen.restclient.exception.AICommunicationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoggingAIClientTest {

    private static final String SECRET_PROMPT = "SENTINEL_USER_PROMPT_DO_NOT_LOG";
    private static final String SECRET_SYSTEM = "SENTINEL_SYSTEM_PROMPT_DO_NOT_LOG";
    private static final String SECRET_OUTPUT = "SENTINEL_MODEL_OUTPUT_DO_NOT_LOG";

    private final AIRequest request = AIRequest.builder()
            .model("gemini-test")
            .systemPrompt(SECRET_SYSTEM)
            .userPrompt(SECRET_PROMPT)
            .operation("RECIPE_TO_FLOW")
            .build();

    @Test
    void logsOneCompletedEventWithUsageAndSizes() {
        AIResponse response = AIResponse.builder()
                .model("gemini-test-001")
                .content(SECRET_OUTPUT)
                .promptTokens(742)
                .cachedTokens(100)
                .completionTokens(518)
                .thoughtsTokens(0)
                .finishReason("STOP")
                .build();
        LoggingAIClient client = new LoggingAIClient(r -> response, "gemini");

        try (LogCapture logs = LogCapture.of(LoggingAIClient.class)) {
            assertThat(client.chat(request)).isSameAs(response);

            List<ILoggingEvent> completed = logs.events("ai_call_completed");
            assertThat(completed).hasSize(1);
            assertThat(completed.get(0).getLevel()).isEqualTo(Level.INFO);
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
        AICommunicationException failure = new AICommunicationException("Gemini API request failed",
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null,
                        ("{\"echo\":\"" + SECRET_PROMPT + "\"}").getBytes(), null));
        LoggingAIClient client = new LoggingAIClient(r -> {
            throw failure;
        }, "gemini");

        try (LogCapture logs = LogCapture.of(LoggingAIClient.class)) {
            assertThatThrownBy(() -> client.chat(request)).isSameAs(failure);

            List<ILoggingEvent> failed = logs.events("ai_call_failed");
            assertThat(failed).hasSize(1);
            assertThat(failed.get(0).getLevel()).isEqualTo(Level.WARN);
            assertThat(failed.get(0).getThrowableProxy()).as("stack is logged once, by the handling boundary").isNull();
            assertThat(failed.get(0).getFormattedMessage())
                    .contains("errorType=AICommunicationException", "status=400", "provider=gemini");
            assertNoPayload(logs);
        }
    }

    @Test
    void payloadLoggerIsTheOnlyPlacePromptsAppearAndOnlyWhenEnabled() {
        LoggingAIClient client = new LoggingAIClient(
                r -> AIResponse.builder().content("x".repeat(5_000)).build(), "ollama");

        try (LogCapture main = LogCapture.of(LoggingAIClient.class);
             LogCapture payload = LogCapture.of(LoggingAIClient.PAYLOAD_LOGGER)) {
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
        assertThat(LoggingAIClient.providerOf("geminiAiClient")).isEqualTo("gemini");
        assertThat(LoggingAIClient.providerOf("openAiAiClient")).isEqualTo("openAi");
        assertThat(LoggingAIClient.providerOf("drawThingsImageClient")).isEqualTo("drawThings");
        assertThat(LoggingAIClient.providerOf("geminiClient")).isEqualTo("gemini");
        assertThat(LoggingAIClient.providerOf(null)).isEqualTo("unknown");
    }

    private static void assertNoPayload(LogCapture logs) {
        assertThat(logs.allMessages())
                .doesNotContain(SECRET_PROMPT)
                .doesNotContain(SECRET_SYSTEM)
                .doesNotContain(SECRET_OUTPUT);
    }
}
