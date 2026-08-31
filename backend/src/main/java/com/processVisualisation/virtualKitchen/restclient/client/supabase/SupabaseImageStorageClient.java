package com.processVisualisation.virtualKitchen.restclient.client.supabase;

import com.processVisualisation.virtualKitchen.restclient.client.ImageStorageClient;
import com.processVisualisation.virtualKitchen.restclient.config.SupabaseProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SupabaseImageStorageClient implements ImageStorageClient {

    private final RestClient restClient;
    private final SupabaseProperties properties;

    public SupabaseImageStorageClient(
            @Qualifier("supabaseRestClient") RestClient restClient,
            SupabaseProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

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