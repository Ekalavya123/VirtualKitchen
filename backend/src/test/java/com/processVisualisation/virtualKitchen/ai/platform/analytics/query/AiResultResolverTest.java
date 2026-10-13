package com.processVisualisation.virtualKitchen.ai.platform.analytics.query;

import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics.Attempt;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics.OutputRef;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AnalyticsStatus;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiResultResolverTest {

    private static final Instant COMPLETED = Instant.parse("2026-10-01T10:00:00Z");

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final AiResultResolver resolver = new AiResultResolver(mongo);

    private AiRequestAnalytics record(String id, OutputRef ref) {
        AiRequestAnalytics record = new AiRequestAnalytics();
        record.setId(id);
        record.setStatus(AnalyticsStatus.SUCCEEDED);
        record.setCompletedAt(COMPLETED);
        record.setOutputRef(ref);
        when(mongo.findById(id, AiRequestAnalytics.class)).thenReturn(record);
        return record;
    }

    private static OutputRef ref(String kind, String collection, String id, String url) {
        OutputRef ref = new OutputRef();
        ref.setKind(kind);
        ref.setCollection(collection);
        ref.setId(id);
        ref.setUrl(url);
        return ref;
    }

    private void aiResponse(String id, String content, String input) {
        Document doc = new Document("_id", new ObjectId(id)).append("input", input)
                .append("responseData", new Document("content", content));
        when(mongo.findOne(argThat((Query q) -> q != null && q.getQueryObject().toJson().contains(id)),
                eq(Document.class), eq("ai_responses"))).thenReturn(doc);
    }

    @Test
    void newTextResult_isReadFromItsRetainedResponse_withThePromptOnRequest() {
        String responseId = new ObjectId().toHexString();
        record("r1", ref("TEXT", "ai_responses", responseId, null));
        aiResponse(responseId, "{\"mainProcess\":{\"steps\":[]}}", "Make pancakes");

        AiResultResolver.Result result = resolver.resolve("r1", null, false, true).orElseThrow();

        assertThat(result.retained()).isTrue();
        assertThat(result.kind()).isEqualTo("JSON");
        assertThat(result.content()).contains("mainProcess");
        assertThat(result.prompt()).isEqualTo("Make pancakes");
        assertThat(result.provenance()).isEqualTo(AiResultResolver.Provenance.RETAINED);
    }

    @Test
    void eachAttemptsOutput_canBeInspected() {
        String firstId = new ObjectId().toHexString();
        AiRequestAnalytics record = record("r2", ref("TEXT", "ai_responses", new ObjectId().toHexString(), null));
        Attempt first = new Attempt();
        first.setAttemptId("a1");
        first.setOutputId(firstId);
        first.setValidationPassed(false);
        record.getAttempts().add(first);
        aiResponse(firstId, "not json", "p");

        AiResultResolver.Result result = resolver.resolve("r2", "a1", false, false).orElseThrow();

        assertThat(result.content()).isEqualTo("not json");
        assertThat(result.kind()).isEqualTo("TEXT");
        assertThat(result.attemptId()).isEqualTo("a1");
        assertThat(result.attempts()).singleElement().satisfies(a -> assertThat(a.validationPassed()).isFalse());
    }

    @Test
    void imageUrlRecordedAtUpload_isServedEvenIfTheAssetWasRegenerated() {
        record("r3", ref("IMAGE", "visualization_asset", "42", "https://cdn/original.png"));

        AiResultResolver.Result result = resolver.resolve("r3", null, false, false).orElseThrow();

        assertThat(result.kind()).isEqualTo("IMAGE");
        assertThat(result.url()).isEqualTo("https://cdn/original.png");
        assertThat(result.provenance()).isEqualTo(AiResultResolver.Provenance.RETAINED);
    }

    @Test
    void historicalImage_isFlaggedWhenTheAssetChangedAfterTheRequest() {
        record("r4", ref("IMAGE", "visualization_asset", "55::s1", null));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("visualization_asset"))).thenReturn(
                new Document("imageUrl", "https://cdn/current.png")
                        .append("updatedAt", Date.from(COMPLETED.plusSeconds(3600))));

        AiResultResolver.Result result = resolver.resolve("r4", null, false, false).orElseThrow();

        assertThat(result.url()).isEqualTo("https://cdn/current.png");
        assertThat(result.provenance()).isEqualTo(AiResultResolver.Provenance.CURRENT_ASSET_MAY_BE_NEWER);
        assertThat(result.provenanceNote()).contains("regenerated");
    }

    @Test
    void historicalFlowResult_isReadFromItsGenerationJob() {
        record("r5", ref("JSON", "process_generation_job", "gen-1", null));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("process_generation_job"))).thenReturn(
                new Document("_id", "gen-1").append("result", new Document("mainProcess", new Document("name", "Pancakes"))));

        AiResultResolver.Result result = resolver.resolve("r5", null, false, false).orElseThrow();

        assertThat(result.kind()).isEqualTo("JSON");
        assertThat(result.content()).contains("Pancakes");
        assertThat(result.provenance()).isEqualTo(AiResultResolver.Provenance.JOB_RESULT);
    }

    @Test
    void unretainedOutput_saysSo_andShowsTheError() {
        AiRequestAnalytics record = record("r6", null);
        record.setStatus(AnalyticsStatus.FAILED);
        record.setErrorMessage("Provider returned 503");

        AiResultResolver.Result result = resolver.resolve("r6", null, false, false).orElseThrow();

        assertThat(result.retained()).isFalse();
        assertThat(result.message()).isEqualTo("Output not retained or unavailable.");
        assertThat(result.errorMessage()).isEqualTo("Provider returned 503");
    }

    @Test
    void longText_isTruncatedUnlessFullIsRequested() {
        String responseId = new ObjectId().toHexString();
        record("r7", ref("TEXT", "ai_responses", responseId, null));
        aiResponse(responseId, "x".repeat(AiResultResolver.DEFAULT_MAX_CHARS + 500), "p");

        AiResultResolver.Result partial = resolver.resolve("r7", null, false, false).orElseThrow();
        AiResultResolver.Result full = resolver.resolve("r7", null, true, false).orElseThrow();

        assertThat(partial.truncated()).isTrue();
        assertThat(partial.content()).hasSize(AiResultResolver.DEFAULT_MAX_CHARS);
        assertThat(partial.contentLength()).isEqualTo(AiResultResolver.DEFAULT_MAX_CHARS + 500);
        assertThat(full.truncated()).isFalse();
        assertThat(full.content()).hasSize(AiResultResolver.DEFAULT_MAX_CHARS + 500);
    }

    @Test
    void reusedRequest_showsTheOriginalRequestsOutput() {
        AiRequestAnalytics reused = record("r8", null);
        reused.setStatus(AnalyticsStatus.REUSED);
        reused.setReusedFromRequestId("r3");
        record("r3", ref("IMAGE", "visualization_asset", "42", "https://cdn/original.png"));

        AiResultResolver.Result result = resolver.resolve("r8", null, false, false).orElseThrow();

        assertThat(result.url()).isEqualTo("https://cdn/original.png");
        assertThat(result.provenance()).isEqualTo(AiResultResolver.Provenance.REUSED_FROM);
    }

    @Test
    void unknownRequest_isEmpty() {
        assertThat(resolver.resolve("nope", null, false, false)).isEmpty();
    }
}
