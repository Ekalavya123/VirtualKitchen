package com.processVisualisation.virtualKitchen.restclient.client.supabase;

import com.processVisualisation.virtualKitchen.restclient.client.ImageStorageClient;
import com.processVisualisation.virtualKitchen.restclient.config.SupabaseProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link ImageStorageClient} implementation that uploads generated images
 * to Supabase Storage for the Virtual Kitchen application, so that
 * process-visualization images can be persisted and served from a public
 * URL.
 */
@Component
public class SupabaseImageStorageClient implements ImageStorageClient {

    private final RestClient restClient;
    private final SupabaseProperties properties;

    /**
     * Creates a client bound to the Supabase REST client and configuration.
     *
     * @param restClient the pre-configured REST client used to call the Supabase Storage API
     * @param properties the configured Supabase URL, service key, and storage bucket
     */
    public SupabaseImageStorageClient(
            @Qualifier("supabaseRestClient") RestClient restClient,
            SupabaseProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * Uploads image bytes to the configured Supabase Storage bucket at the
     * given path and returns the resulting public URL.
     *
     * @param data the raw image bytes to upload
     * @param mimeType the MIME type of the image data
     * @param path the destination path/key within the storage bucket
     * @return the publicly accessible URL of the uploaded image
     */
    @Override
    public String upload(byte[] data, String mimeType, String path) {
        restClient.post()
                .uri(
                        "/storage/v1/object/{bucket}/{path}",
                        properties.getBucket(),
                        path
                )
                .header(
                        "Authorization",
                        "Bearer " + properties.getServiceKey()
                )
                .header(
                        "apikey",
                        properties.getServiceKey()
                )
                .header(
                        "Content-Type",
                        mimeType
                )
                .body(data)
                .retrieve()
                .toBodilessEntity();
        return properties.getUrl()
                + "/storage/v1/object/public/"
                + properties.getBucket()
                + "/"
                + path;
    }
}