package com.processVisualisation.virtualKitchen.restclient.client.localtts;

import com.processVisualisation.virtualKitchen.restclient.client.tts.AudioFormat;
import com.processVisualisation.virtualKitchen.restclient.client.tts.GeneratedAudio;
import com.processVisualisation.virtualKitchen.restclient.client.tts.TtsRequest;
import com.processVisualisation.virtualKitchen.restclient.client.tts.WavAudio;
import com.processVisualisation.virtualKitchen.restclient.config.LocalTtsProperties;
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

import java.net.ConnectException;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The OpenAI-compatible local engine contract, against a mocked engine. */
class LocalTtsProviderTest {

    private static final String BASE_URL = "http://tts.local:8880";
    private static final String SPEECH_URL = BASE_URL + "/v1/audio/speech";

    private LocalTtsProperties properties;
    private MockRestServiceServer server;
    private LocalTtsProvider provider;

    @BeforeEach
    void setUp() {
        properties = new LocalTtsProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setModel("kokoro");
        properties.setVoice("af_heart");
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new LocalTtsProvider(builder.build(), properties);
    }

    @Test
    void postsTheOpenAiSpeechContractAndReturnsTheBinaryAudio() {
        byte[] wav = WavAudio.fromPcm(new byte[48_000], 24000, 1, 16);
        server.expect(requestTo(SPEECH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"model\":\"kokoro\"")))
                .andExpect(content().string(containsString("\"input\":\"Chop the onion.\"")))
                .andExpect(content().string(containsString("\"voice\":\"af_heart\"")))
                .andExpect(content().string(containsString("\"response_format\":\"wav\"")))
                .andRespond(withSuccess(wav, MediaType.parseMediaType("audio/wav")));

        GeneratedAudio audio = provider.synthesize(new TtsRequest("Chop the onion.", null, null, null, null));

        server.verify();
        assertArrayEquals(wav, audio.data());
        assertEquals(AudioFormat.WAV, audio.format());
        assertEquals(1000L, audio.durationMs());
        assertEquals("kokoro", audio.providerModelId());
        assertEquals("af_heart", audio.voice());
    }

    @Test
    void registryModelAndRequestedVoiceOverrideTheConfiguredDefaults() {
        server.expect(requestTo(SPEECH_URL))
                .andExpect(content().string(containsString("\"model\":\"other-engine-model\"")))
                .andExpect(content().string(containsString("\"voice\":\"bf_emma\"")))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.parseMediaType("audio/mpeg")));

        GeneratedAudio audio = provider.synthesize(new TtsRequest("Chop.", "other-engine-model", "bf_emma", null, null));

        assertEquals(AudioFormat.MP3, audio.format(), "the engine's actual content type wins");
        assertNull(audio.durationMs());
    }

    @Test
    void sendsTheBearerTokenOnlyWhenConfigured() {
        properties.setApiKey("local-token");
        server.expect(requestTo(SPEECH_URL))
                .andExpect(header("Authorization", "Bearer local-token"))
                .andRespond(withSuccess(new byte[]{1}, MediaType.parseMediaType("audio/wav")));

        provider.synthesize(new TtsRequest("Chop.", null, null, null, null));
        server.verify();
    }

    @Test
    void engineNotRunningIsATimeoutFailure() {
        server.expect(requestTo(SPEECH_URL)).andRespond(req -> {
            throw new ConnectException("Connection refused");
        });
        assertThrows(AITimeoutException.class, () -> provider.synthesize(request()));
    }

    @Test
    void engineErrorIsACommunicationFailure() {
        server.expect(requestTo(SPEECH_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThrows(AICommunicationException.class, () -> provider.synthesize(request()));
    }

    @Test
    void rejectedTokenIsAnAuthenticationFailure() {
        server.expect(requestTo(SPEECH_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThrows(AIAuthenticationException.class, () -> provider.synthesize(request()));
    }

    @Test
    void emptyOrJsonBodyIsInvalid() {
        server.expect(requestTo(SPEECH_URL)).andRespond(withSuccess(new byte[0], MediaType.parseMediaType("audio/wav")));
        assertThrows(AIInvalidResponseException.class, () -> provider.synthesize(request()));

        server.reset();
        server.expect(requestTo(SPEECH_URL)).andRespond(withSuccess("{\"error\":\"voice not found\"}", MediaType.APPLICATION_JSON));
        assertThrows(AIInvalidResponseException.class, () -> provider.synthesize(request()));
    }

    private static TtsRequest request() {
        return new TtsRequest("Chop.", null, null, null, null);
    }
}
