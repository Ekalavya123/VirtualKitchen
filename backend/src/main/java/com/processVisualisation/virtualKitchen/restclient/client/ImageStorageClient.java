package com.processVisualisation.virtualKitchen.restclient.client;

/**
 * Common contract implemented by storage backends (Supabase) used to
 * persist generated images for the Virtual Kitchen application and obtain a
 * publicly accessible URL for them.
 */
public interface ImageStorageClient {

    /**
     * Uploads image data to the storage backend at the given path.
     *
     * @param data the raw image bytes to upload
     * @param mimeType the MIME type of the image data
     * @param path the destination path/key within the storage backend
     * @return the publicly accessible URL of the uploaded image
     */
    String upload(
            byte[] data,
            String mimeType,
            String path
    );
}
