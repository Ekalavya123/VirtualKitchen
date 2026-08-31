package com.processVisualisation.virtualKitchen.restclient.client;

public interface ImageStorageClient {

    String upload(
            byte[] data,
            String mimeType,
            String path
    );
}
