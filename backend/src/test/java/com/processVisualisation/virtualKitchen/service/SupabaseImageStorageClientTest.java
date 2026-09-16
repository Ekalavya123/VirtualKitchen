package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.restclient.client.supabase.SupabaseImageStorageClient;
import com.processVisualisation.virtualKitchen.restclient.config.SupabaseProperties;
import com.processVisualisation.virtualKitchen.restclient.exception.AIAuthenticationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AICommunicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Pins the typed-exception contract of the storage client.
 * <p>
 * This is not only about error messages. {@code AiRequestQueueService.isRetryable} matches on
 * {@code AITimeoutException}/{@code AICommunicationException}, and the visualization pipeline
 * records the failure's type on the asset — so an unwrapped {@code HttpClientErrorException} here
 * would leave both blind, which is the state this replaced.
 */
class SupabaseImageStorageClientTest {

    private static final String BASE_URL = "https://storage.example";
    private static final byte[] DATA = new byte[]{1, 2, 3};

    private SupabaseProperties properties;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        properties = new SupabaseProperties();
        properties.setUrl(BASE_URL);
        properties.setServiceKey("service-key");
        properties.setBucket("recipe-images");
        properties.setTimeoutMs(1000L);

        builder = RestClient.builder().baseUrl(BASE_URL);
    }

    /**
     * Pins current behaviour, including one asymmetry worth knowing about: {@code path} is passed
     * as a URI template variable, so its separators are percent-encoded to {@code %2F} on the way
     * out, while the returned public URL is string-concatenated with raw {@code /}. Supabase
     * decodes the encoded form, so the two agree in practice — but this test is where that
     * assumption is recorded, and it will fail loudly if the URI building on either side changes.
     */
    @Test
    void upload_success_returnsThePublicUrl() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/storage/v1/object/recipe-images/visualizations%2Fa%2F1%2Fx.png"))
                .andRespond(withSuccess());
        SupabaseImageStorageClient client = new SupabaseImageStorageClient(builder.build(), properties);

        String url = client.upload(DATA, "image/png", "visualizations/a/1/x.png");

        assertEquals(BASE_URL + "/storage/v1/object/public/recipe-images/visualizations/a/1/x.png", url);
        server.verify();
    }

    @Test
    void upload_unauthorized_throwsAuthenticationException() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/storage/v1/object/recipe-images/path.png"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        SupabaseImageStorageClient client = new SupabaseImageStorageClient(builder.build(), properties);

        assertThrows(AIAuthenticationException.class, () -> client.upload(DATA, "image/png", "path.png"));
    }

    @Test
    void upload_serverError_throwsCommunicationExceptionSoTheFailureIsClassifiedAsTransient() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/storage/v1/object/recipe-images/path.png"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        SupabaseImageStorageClient client = new SupabaseImageStorageClient(builder.build(), properties);

        AICommunicationException error = assertThrows(AICommunicationException.class,
                () -> client.upload(DATA, "image/png", "path.png"));
        assertEquals(true, error.getMessage().contains("503"));
    }

    @Test
    void upload_missingConfiguration_failsFastRatherThanAsAnOpaque4xx() {
        properties.setServiceKey("  ");
        // Bound so the builder uses the mock request factory; the expectation is that no request
        // is ever made, because the configuration check runs before the client is touched.
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseImageStorageClient client = new SupabaseImageStorageClient(builder.build(), properties);

        AICommunicationException error = assertThrows(AICommunicationException.class,
                () -> client.upload(DATA, "image/png", "path.png"));
        assertEquals(true, error.getMessage().contains("not configured"));
        server.verify();
    }
}
