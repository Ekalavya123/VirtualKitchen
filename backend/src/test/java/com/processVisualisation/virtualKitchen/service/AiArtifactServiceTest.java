package com.processVisualisation.virtualKitchen.service;

import com.mongodb.client.result.UpdateResult;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifact;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactHit;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactPayload;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactPayloadKind;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactProperties;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactRepository;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactService;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactSpec;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactStatus;
import com.processVisualisation.virtualKitchen.ai.artifact.codec.AiArtifactCodec;
import com.processVisualisation.virtualKitchen.ai.artifact.codec.AiArtifactCodecRegistry;
import com.processVisualisation.virtualKitchen.ai.artifact.codec.GeneratedImageCodec;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import com.processVisualisation.virtualKitchen.ai.routing.FallbackReason;
import com.processVisualisation.virtualKitchen.ai.routing.ModelSelectionOutcome;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient.GeneratedImage;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiArtifactServiceTest {

    private static final String ARTIFACT_KEY = "TEXT_TO_IMAGE::visualization-image::recipe-1::step-1::abc";
    private static final ObjectId BLOB_ID = new ObjectId();

    private MongoTemplate mongoTemplate;
    private GridFsTemplate gridFsTemplate;
    private AiArtifactRepository artifactRepository;
    private AiArtifactProperties properties;
    private AiArtifactService service;
    private AiArtifactSpec<GeneratedImage> spec;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        gridFsTemplate = mock(GridFsTemplate.class);
        artifactRepository = mock(AiArtifactRepository.class);
        properties = new AiArtifactProperties();

        GeneratedImageCodec imageCodec = new GeneratedImageCodec();
        AiArtifactCodecRegistry codecRegistry = new AiArtifactCodecRegistry(List.of(imageCodec));

        when(artifactRepository.findByUserIdAndArtifactKey(any(), anyString())).thenReturn(Optional.empty());
        when(gridFsTemplate.store(any(), anyString(), anyString(), any())).thenReturn(BLOB_ID);

        service = new AiArtifactService(mongoTemplate, gridFsTemplate, artifactRepository, codecRegistry, properties);
        spec = AiArtifactSpec.of(ARTIFACT_KEY, imageCodec, "visualization-image-upload");
    }

    @Test
    void stage_binaryPayload_storesTheBlobAndLeavesTheArtifactPendingWithNoExpiry() throws Exception {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(AiArtifact.class)))
                .thenReturn(new AiArtifact());

        service.stage(7L, AiCapability.TEXT_TO_IMAGE, "visualization-image", "recipe-1::step-1",
                spec, new GeneratedImage("image/png", new byte[]{1, 2, 3}), selection(), "job-1");

        verify(gridFsTemplate).store(any(), anyString(), eq("image/png"), any());

        Document update = capturedUpdate();
        Document set = update.get("$set", Document.class);
        assertEquals(AiArtifactStatus.PENDING, set.get("status"));
        assertEquals(BLOB_ID.toHexString(), set.get("gridFsId"));
        assertEquals("job-1", set.get("producingJobId"));
        assertFalse(set.containsKey("expiresAt"), "a pending artifact must never carry a TTL anchor");
        assertTrue(update.get("$unset", Document.class).containsKey("expiresAt"),
                "reviving a consumed slot must lift its expiry back off");
        assertTrue(update.get("$setOnInsert", Document.class).containsKey("createdAt"),
                "createdAt must survive a re-stage so the sweeper's age filter stays meaningful");
    }

    @Test
    void stage_oversizedInlinePayload_isPromotedToGridFs() throws Exception {
        properties.setMaxInlineBytes(8);
        AiArtifactCodec<String> textCodec = textCodec();
        AiArtifactService textService = new AiArtifactService(
                mongoTemplate, gridFsTemplate, artifactRepository,
                new AiArtifactCodecRegistry(List.of(textCodec)), properties);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(AiArtifact.class)))
                .thenReturn(new AiArtifact());

        textService.stage(7L, AiCapability.TEXT_TO_TEXT, "flow-generation", "recipe-1",
                AiArtifactSpec.of(ARTIFACT_KEY, textCodec, "c"), "a payload well over eight bytes",
                selection(), "job-1");

        verify(gridFsTemplate).store(any(), anyString(), anyString(), any());
        Document set = capturedUpdate().get("$set", Document.class);
        assertEquals(AiArtifactPayloadKind.BINARY, set.get("payloadKind"));
        assertEquals(BLOB_ID.toHexString(), set.get("gridFsId"));
    }

    @Test
    void findReusable_onlyMatchesPendingArtifactsAndTakesALease() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(AiArtifact.class)))
                .thenReturn(null);

        Optional<AiArtifactHit<GeneratedImage>> hit = service.findReusable(7L, spec);

        assertTrue(hit.isEmpty());
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(queryCaptor.capture(), updateCaptor.capture(), any(), eq(AiArtifact.class));

        Document criteria = queryCaptor.getValue().getQueryObject();
        assertEquals(AiArtifactStatus.PENDING, criteria.get("status"),
                "a consumed row still holds the dedup slot, so it must read as a miss");
        assertEquals(ARTIFACT_KEY, criteria.get("artifactKey"));

        Document update = updateCaptor.getValue().getUpdateObject();
        assertTrue(update.get("$inc", Document.class).containsKey("reuseCount"));
        assertTrue(update.get("$set", Document.class).containsKey("leaseUntil"),
                "the lease is what keeps the sweeper off an artifact a request is consuming");
    }

    @Test
    void findReusable_whenTheBlobIsMissing_missesRatherThanThrowing() {
        AiArtifact artifact = pendingArtifact();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(AiArtifact.class)))
                .thenReturn(artifact);
        when(gridFsTemplate.findOne(any(Query.class))).thenReturn(null);

        assertTrue(service.findReusable(7L, spec).isEmpty(),
                "an unreadable payload must degrade to a regeneration, not fail the request");
    }

    @Test
    void markConsumed_guardsOnPendingAndArmsTheTtl() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(AiArtifact.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(mongoTemplate.findOne(any(Query.class), eq(AiArtifact.class))).thenReturn(pendingArtifact());

        assertTrue(service.markConsumed("artifact-1"));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, org.mockito.Mockito.atLeastOnce())
                .updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(AiArtifact.class));

        Document criteria = queryCaptor.getAllValues().get(0).getQueryObject();
        assertEquals(AiArtifactStatus.PENDING, criteria.get("status"),
                "the status guard is what elects a single winner under a lease race");
        Document set = updateCaptor.getAllValues().get(0).getUpdateObject().get("$set", Document.class);
        assertEquals(AiArtifactStatus.CONSUMED, set.get("status"));
        assertTrue(set.containsKey("expiresAt"), "retiring an artifact must arm its retention window");
    }

    @Test
    void markConsumed_whenAnotherWorkerAlreadyWon_doesNotDeleteTheBlob() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(AiArtifact.class)))
                .thenReturn(UpdateResult.acknowledged(1, 0L, null));

        assertFalse(service.markConsumed("artifact-1"));
        verify(gridFsTemplate, never()).delete(any(Query.class));
    }

    @Test
    void whenDisabled_lookupAndStagingAreNoOps() throws Exception {
        properties.setEnabled(false);

        assertTrue(service.findReusable(7L, spec).isEmpty());
        assertEquals(null, service.stage(7L, AiCapability.TEXT_TO_IMAGE, "visualization-image", "recipe-1::step-1",
                spec, new GeneratedImage("image/png", new byte[]{1}), selection(), "job-1"));
        verify(gridFsTemplate, never()).store(any(), anyString(), anyString(), any());
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(), eq(AiArtifact.class));
    }

    private Document capturedUpdate() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), updateCaptor.capture(), any(), eq(AiArtifact.class));
        return updateCaptor.getValue().getUpdateObject();
    }

    private AiArtifact pendingArtifact() {
        AiArtifact artifact = new AiArtifact();
        artifact.setId("artifact-1");
        artifact.setArtifactKey(ARTIFACT_KEY);
        artifact.setStatus(AiArtifactStatus.PENDING);
        artifact.setPayloadKind(AiArtifactPayloadKind.BINARY);
        artifact.setGridFsId(BLOB_ID.toHexString());
        artifact.setContentType("image/png");
        return artifact;
    }

    private ModelSelectionOutcome selection() {
        ModelDefinition model = new ModelDefinition();
        model.setKey("test-image");
        model.setCapability(AiCapability.TEXT_TO_IMAGE);
        model.setTier(ModelTier.OPEN_SOURCE);
        return new ModelSelectionOutcome(model, false, FallbackReason.NONE, null);
    }

    private AiArtifactCodec<String> textCodec() {
        return new AiArtifactCodec<>() {
            @Override
            public String id() {
                return "test-text-v1";
            }

            @Override
            public AiArtifactPayload encode(String value) {
                return AiArtifactPayload.inline("text/plain", value);
            }

            @Override
            public String decode(AiArtifactPayload payload) {
                return new String(payload.asBytes(), StandardCharsets.UTF_8);
            }
        };
    }
}
