package com.processVisualisation.virtualKitchen.ai.globalasset;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifact;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationAsset;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient.GeneratedImage;
import com.processVisualisation.virtualKitchen.restclient.client.ImageStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalAssetImageArtifactConsumerTest {

    private static final String KEY = "global::EQUIPMENT::3";

    private ImageStorageClient storage;
    private GlobalAssetStore store;
    private GlobalResourceResolver resolver;
    private GlobalAssetImageArtifactConsumer consumer;

    @BeforeEach
    void setUp() {
        storage = mock(ImageStorageClient.class);
        store = mock(GlobalAssetStore.class);
        resolver = mock(GlobalResourceResolver.class);
        consumer = new GlobalAssetImageArtifactConsumer(storage, store, resolver);
    }

    @Test
    void consume_uploadsMarksReadyAndPublishesToTheCatalog() {
        VisualizationAsset asset = new VisualizationAsset();
        asset.setId(11L);
        asset.setVisualizationKey(KEY);
        asset.setResourceType(GlobalResourceType.EQUIPMENT);
        asset.setResourceId(3L);
        when(store.find(KEY)).thenReturn(Optional.of(asset));
        when(storage.upload(any(), eq("image/png"), anyString())).thenReturn("https://cdn/kettle.png");

        String url = consumer.consume(artifact(), new GeneratedImage("image/png", new byte[]{9}));

        assertThat(url).isEqualTo("https://cdn/kettle.png");
        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(any(), eq("image/png"), path.capture());
        assertThat(path.getValue()).startsWith("global-assets/equipment/3/11/").endsWith(".png");
        verify(store).markReady(KEY, "https://cdn/kettle.png", "drawthings-sdxl", ModelTier.OPEN_SOURCE, true);
        verify(resolver).publishImage(GlobalResourceType.EQUIPMENT, 3L, "https://cdn/kettle.png");
    }

    @Test
    void consume_failsWithoutPublishingWhenTheAssetIsMissing() {
        when(store.find(KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consumer.consume(artifact(), new GeneratedImage("image/png", new byte[]{9})))
                .isInstanceOf(IllegalStateException.class);
        verify(storage, never()).upload(any(), any(), any());
        verify(resolver, never()).publishImage(any(), any(), any());
    }

    private static AiArtifact artifact() {
        AiArtifact artifact = new AiArtifact();
        artifact.setId("art-1");
        artifact.setCorrelationId(KEY);
        artifact.setProducedByModelKey("drawthings-sdxl");
        artifact.setProducedByTier(ModelTier.OPEN_SOURCE);
        artifact.setUsedFallback(true);
        return artifact;
    }
}
