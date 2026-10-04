package com.processVisualisation.virtualKitchen.restclient.client.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.restclient.client.tts.AudioFormat;
import com.processVisualisation.virtualKitchen.restclient.client.tts.GeneratedAudio;
import com.processVisualisation.virtualKitchen.restclient.client.tts.TtsRequest;
import com.processVisualisation.virtualKitchen.restclient.client.tts.WavAudio;
import com.processVisualisation.virtualKitchen.restclient.config.GeminiProperties;
import com.processVisualisation.virtualKitchen.restclient.exception.AIAuthenticationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AICommunicationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AIInvalidResponseException;
import com.processVisualisation.virtualKitchen.restclient.exception.AITimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Gemini speech generation against a mocked Gemini endpoint: no real API calls are made. */
class GeminiTtsProviderTest {

    private static final String BASE_URL = "https://gemini.example";
    private static final String MODEL = "gemini-tts-test";
    private static final String GENERATE_URL = BASE_URL + "/v1beta/models/" + MODEL + ":generateContent";

    private GeminiProperties properties;
    private MockRestServiceServer server;
    private GeminiTtsProvider provider;

    @BeforeEach
    void setUp() {
        properties = new GeminiProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl(BASE_URL);
        properties.setChatEndpoint("/v1beta/models/{model}:generateContent");
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GeminiTtsProvider(builder.build(), properties, new ObjectMapper());
    }

    @Test
    void requestsAudioWithTheConfiguredVoiceAndWrapsPcmAsWav() {
        byte[] pcm = new byte[48_000]; // 1 s of 24 kHz 16-bit mono
        pcm[0] = 7;
        server.expect(requestTo(GENERATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(content().string(containsString("\"responseModalities\":[\"AUDIO\"]")))
                .andExpect(content().string(containsString("\"voiceName\":\"Puck\"")))
                .andExpect(content().string(containsString("\"languageCode\":\"en-US\"")))
                .andExpect(content().string(containsString("\"text\":\"Stir well.\"")))
                .andRespond(withSuccess(audioResponse("audio/L16;codec=pcm;rate=24000", pcm), MediaType.APPLICATION_JSON));

        GeneratedAudio audio = provider.synthesize(new TtsRequest("Stir well.", MODEL, "Puck", "en-US", null));

        server.verify();
        assertEquals(AudioFormat.WAV, audio.format());
        assertEquals("audio/wav", audio.mimeType());
        assertEquals(1000L, audio.durationMs());
        assertEquals(1000L, WavAudio.durationMs(audio.data()), "the WAV header must describe the PCM");
        assertEquals(44 + pcm.length, audio.data().length);
        assertEquals(7, audio.data()[44]);
        assertEquals("Puck", audio.voice());
        assertEquals(MODEL, audio.providerModelId());
    }

    @Test
    void reportsTheTokenUsageGeminiReturns() {
        String body = audioResponse("audio/L16;codec=pcm;rate=24000", new byte[480]);
        body = body.substring(0, body.length() - 1)
                + ",\"usageMetadata\":{\"promptTokenCount\":12,\"candidatesTokenCount\":250,\"totalTokenCount\":262}}";
        server.expect(requestTo(GENERATE_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        GeneratedAudio audio = provider.synthesize(request());

        assertEquals(12L, audio.usage().inputTokens());
        assertEquals(250L, audio.usage().outputTokens());
        assertEquals(262L, audio.usage().totalTokens());
    }

    @Test
    void fallsBackToConfiguredModelAndVoiceAndHonoursTheRateInTheMimeType() {
        properties.getTts().setModel(MODEL);
        properties.getTts().setVoice("Kore");
        byte[] pcm = new byte[32_000]; // 1 s at 16 kHz
        server.expect(requestTo(GENERATE_URL))
                .andExpect(content().string(containsString("\"voiceName\":\"Kore\"")))
                .andRespond(withSuccess(audioResponse("audio/pcm;rate=16000", pcm), MediaType.APPLICATION_JSON));

        GeneratedAudio audio = provider.synthesize(new TtsRequest("Stir.", null, null, null, null));

        assertEquals(1000L, audio.durationMs());
        assertEquals("Kore", audio.voice());
    }

    @Test
    void passesThroughAudioThatIsAlreadyInAPlayableContainer() {
        byte[] wav = WavAudio.fromPcm(new byte[8_000], 8000, 1, 16);
        server.expect(requestTo(GENERATE_URL))
                .andRespond(withSuccess(audioResponse("audio/wav", wav), MediaType.APPLICATION_JSON));

        GeneratedAudio audio = provider.synthesize(new TtsRequest("Stir.", MODEL, "Kore", null, null));

        assertArrayEquals(wav, audio.data());
        assertEquals(500L, audio.durationMs());
    }

    @Test
    void rejectedApiKeyIsAnAuthenticationFailure() {
        server.expect(requestTo(GENERATE_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThrows(AIAuthenticationException.class, () -> provider.synthesize(request()));
    }

    @Test
    void rateLimitIsARetryableCommunicationFailure() {
        server.expect(requestTo(GENERATE_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        AICommunicationException ex = assertThrows(AICommunicationException.class, () -> provider.synthesize(request()));
        assertEquals(true, ex.getMessage().contains("429"));
    }

    @Test
    void serverErrorIsACommunicationFailure() {
        server.expect(requestTo(GENERATE_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(AICommunicationException.class, () -> provider.synthesize(request()));
    }

    @Test
    void timeoutIsATimeoutFailure() {
        server.expect(requestTo(GENERATE_URL)).andRespond(req -> {
            throw new SocketTimeoutException("read timed out");
        });
        assertThrows(AITimeoutException.class, () -> provider.synthesize(request()));
    }

    @Test
    void responseWithoutAudioIsInvalid() {
        server.expect(requestTo(GENERATE_URL)).andRespond(withSuccess(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"I cannot speak\"}]}}]}", MediaType.APPLICATION_JSON));
        assertThrows(AIInvalidResponseException.class, () -> provider.synthesize(request()));
    }

    @Test
    void emptyBodyIsInvalid() {
        server.expect(requestTo(GENERATE_URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        assertThrows(AIInvalidResponseException.class, () -> provider.synthesize(request()));
    }

    @Test
    void malformedJsonIsInvalid() {
        server.expect(requestTo(GENERATE_URL)).andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));
        assertThrows(AIInvalidResponseException.class, () -> provider.synthesize(request()));
    }

    @Test
    void missingApiKeyFailsWithoutCallingGemini() {
        properties.setApiKey("");
        assertThrows(AICommunicationException.class, () -> provider.synthesize(request()));
        server.verify(); // no request expected
    }

    private static TtsRequest request() {
        return new TtsRequest("Stir well.", MODEL, "Kore", null, null);
    }

    private static String audioResponse(String mimeType, byte[] data) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"" + mimeType
                + "\",\"data\":\"" + Base64.getEncoder().encodeToString(data) + "\"}}]}}]}";
    }
}
