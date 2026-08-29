package com.processVisualisation.virtualKitchen.storage;

import com.processVisualisation.virtualKitchen.service.ImageStorageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SupabaseStorageService implements ImageStorageService {

    private final RestClient restClient;
    private final SupabaseProperties properties;

//    public SupabaseStorageService(
//            RestClient.Builder builder,
//            SupabaseProperties properties) {
//
//        this.restClient = builder
//                .baseUrl(properties.getUrl())
//                .build();
//
//        this.properties = properties;
//    }

    public SupabaseStorageService(
            @Qualifier("supabaseRestClient") RestClient restClient,
            SupabaseProperties properties) {

        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String upload(byte[] data, String mimeType, String path) {
        restClient.post()
                .uri("/storage/v1/object/{bucket}/{path}",
                        properties.getBucket(), path)
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
