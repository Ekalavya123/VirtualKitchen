package com.processVisualisation.virtualKitchen.service;

public interface ImageStorageService {

    String upload(byte[] data, String mimeType, String path);
}