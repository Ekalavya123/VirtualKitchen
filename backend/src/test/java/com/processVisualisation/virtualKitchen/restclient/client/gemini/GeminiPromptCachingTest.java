package com.processVisualisation.virtualKitchen.restclient.client.gemini;

import com.processVisualisation.virtualKitchen.restclient.config.GeminiProperties;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Explicit Gemini context caching: one cache per (model, system prompt), reused until shortly before it expires,
 * never failing a request — creation errors and rejected caches both fall back to sending the prompt inline.
 */
class GeminiPromptCachingTest {

    private static final String BASE_URL = "https://gemini.example";
    private static final String CACHE_URL = BASE_URL + "/v1beta/cachedContents";
    private static final String MODEL = "gemini-test";
    private static final String GENERATE_URL = BASE_URL + "/v1beta/models/" + MODEL + ":generateContent";
    private static final String SYSTEM_PROMPT = "static rules and vocabulary";
    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    private GeminiProperties properties;
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        properties = new GeminiProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl(BASE_URL);
        properties.setChatEndpoint("/v1beta/models/{model}:generateContent");
        properties.setDefaultModel(MODEL);
        properties.getCache().setEnabled(true);

        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock(NOW);
    }

    @Test
    void createsOneCachePerModelAndPromptAndReusesIt() {
        server.expect(once(), requestTo(CACHE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"model\":\"models/" + MODEL + "\"")))
                .andExpect(content().string(containsString("\"ttl\":\"3600s\"")))
                .andRespond(cacheCreated("cachedContents/abc", NOW.plusSeconds(3600)));
        server.expect(once(), requestTo(CACHE_URL))
                .andExpect(content().string(containsString("models/other-model")))
                .andRespond(cacheCreated("cachedContents/other", NOW.plusSeconds(3600)));
        GeminiPromptCacheService cache = cacheService();

        assertEquals(Optional.of("cachedContents/abc"), cache.cacheNameFor(MODEL, SYSTEM_PROMPT));
        assertEquals(Optional.of("cachedContents/abc"), cache.cacheNameFor(MODEL, SYSTEM_PROMPT), "second call must reuse, not create");
        assertEquals(Optional.of("cachedContents/other"), cache.cacheNameFor("other-model", SYSTEM_PROMPT), "a cache is bound to its model");
        server.verify();
    }

    @Test
    void recreatesTheCacheShortlyBeforeItExpires() {
        server.expect(once(), requestTo(CACHE_URL)).andRespond(cacheCreated("cachedContents/first", NOW.plusSeconds(3600)));
        server.expect(once(), requestTo(CACHE_URL)).andRespond(cacheCreated("cachedContents/second", NOW.plusSeconds(7200)));
        GeminiPromptCacheService cache = cacheService();

        assertEquals(Optional.of("cachedContents/first"), cache.cacheNameFor(MODEL, SYSTEM_PROMPT));
        clock.now = NOW.plusSeconds(3600 - properties.getCache().getRefreshMarginSeconds());
        assertEquals(Optional.of("cachedContents/second"), cache.cacheNameFor(MODEL, SYSTEM_PROMPT));
        server.verify();
    }

    @Test
    void failedCreationFallsBackAndIsNotRetriedDuringTheBackoff() {
        server.expect(once(), requestTo(CACHE_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(once(), requestTo(CACHE_URL)).andRespond(cacheCreated("cachedContents/later", NOW.plusSeconds(7200)));
        GeminiPromptCacheService cache = cacheService();

        assertEquals(Optional.empty(), cache.cacheNameFor(MODEL, SYSTEM_PROMPT));
        assertEquals(Optional.empty(), cache.cacheNameFor(MODEL, SYSTEM_PROMPT), "no second attempt inside the back-off");
        clock.now = NOW.plusSeconds(properties.getCache().getFailureBackoffSeconds());
        assertEquals(Optional.of("cachedContents/later"), cache.cacheNameFor(MODEL, SYSTEM_PROMPT));
        server.verify();
    }

    @Test
    void disabledCachingNeverCallsGemini() {
        properties.getCache().setEnabled(false);
        assertEquals(Optional.empty(), cacheService().cacheNameFor(MODEL, SYSTEM_PROMPT));
        server.verify();
    }

    @Test
    void cachedPayloadReferencesTheCacheInsteadOfTheSystemPrompt() {
        GeminiClient client = new GeminiClient(builder.build(), properties, cacheService());
        AIRequest request = request();

        Map<String, Object> cached = client.buildRequestPayload(request, "cachedContents/abc");
        assertEquals("cachedContents/abc", cached.get("cachedContent"));
        assertFalse(cached.containsKey("systemInstruction"), "Gemini rejects systemInstruction alongside cachedContent");

        Map<String, Object> inline = client.buildRequestPayload(request, null);
        assertTrue(inline.containsKey("systemInstruction"));
        assertFalse(inline.containsKey("cachedContent"));
    }

    @Test
    void chatUsesTheCacheAndReportsCachedTokens() {
        server.expect(once(), requestTo(CACHE_URL)).andRespond(cacheCreated("cachedContents/abc", NOW.plusSeconds(3600)));
        server.expect(once(), requestTo(GENERATE_URL))
                .andExpect(content().string(containsString("\"cachedContent\":\"cachedContents/abc\"")))
                .andExpect(content().string(not(containsString(SYSTEM_PROMPT))))
                .andRespond(generated(9000));
        GeminiClient client = new GeminiClient(builder.build(), properties, cacheService());

        AIResponse response = client.chat(request());

        assertEquals(9000, response.getCachedTokens());
        server.verify();
    }

    @Test
    void rejectedCacheIsEvictedAndTheCallRetriedWithThePromptInline() {
        server.expect(once(), requestTo(CACHE_URL)).andRespond(cacheCreated("cachedContents/gone", NOW.plusSeconds(3600)));
        server.expect(once(), requestTo(GENERATE_URL))
                .andExpect(content().string(containsString("cachedContents/gone")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(once(), requestTo(GENERATE_URL))
                .andExpect(content().string(containsString(SYSTEM_PROMPT)))
                .andExpect(content().string(not(containsString("cachedContent"))))
                .andRespond(generated(null));
        // The eviction means the next request creates a fresh cache.
        server.expect(once(), requestTo(CACHE_URL)).andRespond(withServerError());
        GeminiPromptCacheService cache = cacheService();
        GeminiClient client = new GeminiClient(builder.build(), properties, cache);

        AIResponse response = client.chat(request());

        assertNull(response.getCachedTokens());
        assertEquals(Optional.empty(), cache.cacheNameFor(MODEL, SYSTEM_PROMPT));
        server.verify();
    }

    private GeminiPromptCacheService cacheService() {
        return new GeminiPromptCacheService(builder.build(), properties, clock);
    }

    private static AIRequest request() {
        return AIRequest.builder()
                .model(MODEL)
                .systemPrompt(SYSTEM_PROMPT)
                .userPrompt("RECIPE: boil pasta")
                .cacheSystemPrompt(true)
                .build();
    }

    private static org.springframework.test.web.client.ResponseCreator cacheCreated(String name, Instant expireTime) {
        return withSuccess("{\"name\":\"" + name + "\",\"expireTime\":\"" + expireTime + "\",\"usageMetadata\":{\"totalTokenCount\":9000}}",
                MediaType.APPLICATION_JSON);
    }

    private static org.springframework.test.web.client.ResponseCreator generated(Integer cachedTokens) {
        String usage = "\"promptTokenCount\":9100,\"candidatesTokenCount\":50,\"totalTokenCount\":9150"
                + (cachedTokens != null ? ",\"cachedContentTokenCount\":" + cachedTokens : "");
        return withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{}\"}]},\"finishReason\":\"STOP\"}],"
                + "\"usageMetadata\":{" + usage + "}}", MediaType.APPLICATION_JSON);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
