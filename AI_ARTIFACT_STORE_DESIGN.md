# AI Artifact Store — Durable Retention of Paid AI Output — Design Document

Status: Implemented (all phases; `ai.artifact.enabled` gates the runtime behaviour)
Scope: `backend` (Spring Boot / MongoDB). No frontend change.
Target scale: 10–50 registered users, 5–10 concurrent users; TEXT_TO_IMAGE payloads of ~0.5–3 MB per step, 5–15 steps per recipe

---

## 1. Executive Summary

Every AI generation in Virtual Kitchen is paid for — in credits, in provider spend, and in latency. Today, once a provider returns, that output exists **only as a local variable** on the worker thread until whatever dependent work consumes it succeeds. If the dependent work fails, the output is destroyed and the only route back is another paid generation.

The live instance of this is image visualization. `AIRecipeVisualizationService.generateImage` receives `GeneratedImage(mimeType, byte[])` from a credit-consuming provider call, then immediately hands the bytes to `SupabaseImageStorageClient.upload(...)`. A Supabase outage — or any 4xx/5xx, timeout, or misconfiguration — throws, and a blanket `catch` sets `imageUrl = null` and persists the asset anyway. The credits are already spent (`AiRequestQueueService` consumed them the moment the provider call returned), the bytes are gone, and the real cause survives only as a log line. When the user retries that step, the system pays a second time for an image it already had.

This document proposes a general **AI artifact store**: a durable landing zone that every AI payload passes through the instant the provider returns, *before* any dependent work runs, and which is consulted *before* any provider call is made on a retry.

Two properties define it:

- **Write-before-dependency.** The payload is persisted to MongoDB (GridFS for binary, inline for small text) before the consuming work is allowed to touch it. A crash, an outage, or a JVM restart between generation and consumption can no longer destroy paid output.
- **Read-before-spend.** The reuse check is placed *upstream of credit reservation*, so a hit costs zero credits and makes no provider call. This placement is not cosmetic — see Section 7.

A new package `ai/artifact` holds the generic mechanism; only the TEXT_TO_IMAGE visualization path is wired to it in this iteration. Other capabilities opt in later by passing one extra argument at their call site. Recovery is two-sided: the online request path reuses a stored payload naturally, and a scheduled sweeper re-drives dependent work for payloads whose consumer never completed — including after a process crash.

No new infrastructure is introduced. GridFS is already auto-configured by the existing `spring-boot-starter-data-mongodb`; no new dependency is added to `pom.xml`. The entire feature is gated behind `ai.artifact.enabled`, so rollback is a property flip, not a redeploy.

---

## 2. Current Failure Analysis

### 2.1 The data-loss window

`ai/service/AIRecipeVisualizationService.java:343-385`:

```java
private VisualizationAsset generateImage(Long userId, VisualizationAsset asset) {
    String visualizationKey = asset.getVisualizationKey();              // 344 — outside the try
    try {
        AiRequestOutcome<GeneratedImage> outcome = queueService.executeInline(  // 348 — PAID
                userId, AiCapability.TEXT_TO_IMAGE, null, null,
                "visualization-image", visualizationKey,
                selection -> clientResolver.resolveImageClient(selection.model())
                                           .generate(asset.getImagePrompt()));

        GeneratedImage generatedImage = outcome.value();                // 358 — bytes live ONLY here
        ...
        String imageUrl = imageStorageClient.upload(                    // 368 — throws
                generatedImage.data(), generatedImage.mimeType(), imagePath);
        asset.setImageUrl(imageUrl);
        ...
    } catch (Exception e) {                                             // 379
        asset.setImageUrl(null);                                        // 380 — bytes discarded
        logger.error("Failed to generate or upload image for visualizationKey: {}, prompt: {}",
                     visualizationKey, asset.getImagePrompt(), e);      // 381 — only record of the cause
    }
    asset.setVideoUrl(null);
    return AIVisualizationAssetRepository.save(asset);                  // 384 — saved with null URL
}
```

The lifetime of `generatedImage` is the `try` block. Nothing outside this stack frame ever sees it.

### 2.2 What each layer does with a Supabase failure

| Layer | Behaviour when `upload(...)` throws |
|---|---|
| `SupabaseImageStorageClient.upload` (`:43-71`) | No try/catch, no `onStatus`, no retry. Raw `HttpClientErrorException` / `HttpServerErrorException` / `ResourceAccessException` propagates. |
| `AIRecipeVisualizationService.generateImage` (`:379-382`) | **Data-loss point.** Bytes discarded, `imageUrl=null`, exception swallowed, asset persisted anyway. |
| `AiRequestQueueService.runWithRetries` | Never sees it. Already returned `COMPLETED` and ran `creditService.consume(...)` at `:197`. |
| `ThreadPoolTaskPool.runSafely` | Sees a *successful* `TaskResult`. |
| `VisualizationJobService.recordStepResult` (`:149-173`) | Records `success=false` with the hardcoded `"Image generation failed for this step"` (`:162`). Real cause lost. |
| `VisualizationJobService.runPipeline` (`:130-134`) | Job ends `COMPLETED_WITH_ERRORS`; the flow node gets `imageUrl: null`. |

The bridge that turns a swallowed exception into a job-level failure is `isEffectivelySuccessful` (`:181-183`), which infers failure purely from a null `imageUrl`. The system therefore knows *that* the step failed and never *why*.

### 2.3 Why retries do not help today

`AiRequestQueueService.isRetryable` (`:230-232`):

```java
return error instanceof AITimeoutException || error instanceof AICommunicationException;
```

`GeminiImageClient` and `DrawThingsImageClient` do **not** wrap `RestClient` exceptions into those types — unlike `GeminiClient`, which does so at `:137,142,146`. So `isRetryable` returns false for every real HTTP failure from an image provider, the loop breaks on attempt 1, and `ai.request.max-retries` is **dead configuration for TEXT_TO_IMAGE**.

The Supabase upload is worse: it sits *outside* the `AiWork` lambda entirely, so it is not inside the retry boundary at any layer and receives zero retries by construction.

### 2.4 Why the existing cache is not enough

`resolveVisualizationAsset` (`:208-230`) is already cache-first and already does per-field partial resume:

```java
if (asset.getImagePrompt() == null || asset.getImagePrompt().isEmpty()) asset = generatePrompt(...);
if (asset.getImageUrl()    == null || asset.getImageUrl().isEmpty())    asset = generateImage(...);
```

This is exactly the right shape — reuse what survived, re-run only what is missing. Its limitation is that `VisualizationAsset` can only hold a *URL*. There is nowhere to put the bytes, so "the image exists but isn't uploaded yet" is inexpressible, and the only recovery for a missing URL is a full paid regeneration.

The artifact store is the missing tier: a place for output that has been *produced* but not yet *placed*.

### 2.5 Adjacent defects found in the same paths

| Defect | Location | Consequence |
|---|---|---|
| `generatePrompt` returns `null` on failure and callers dereference it unguarded | `:337-340` consumed at `:223` and `:388-389` | A prompt-model failure surfaces as a raw `NullPointerException` in the step result |
| `executeInline` ignores `ai.request.timeout-ms.TEXT_TO_IMAGE` | only `executeBounded` reads `requestProperties.timeoutFor(...)` | A hung provider call parks a visualization worker indefinitely |
| `runWithRetries` never persists the incremented attempt | `job.setAttempt(1)` written once in `beginProcessing` | Stored attempt count is always 1, hampering diagnosis |
| Re-prompting an existing asset inserts a second row under the same unique `visualizationKey` | `:220-222` with `:330-336` | `DuplicateKeyException` on a blank-prompt reprocess |

The first three are fixed as part of Phase 1 because the artifact work inherits them. The fourth is logged as out of scope (Section 14).

---

## 3. Proposed Architecture

A new package `ai/artifact`, organised the way `ai/queue` and `ai/credit` already are, plus one domain-specific consumer beside the service that owns it.

| Class | Package | Responsibility |
|---|---|---|
| `AiArtifact` | `ai/artifact` | Entity; collection `ai_artifacts` |
| `AiArtifactStatus` | `ai/artifact` | `PENDING` / `CONSUMED` / `ABANDONED` |
| `AiArtifactPayloadKind` | `ai/artifact` | `BINARY` / `INLINE` |
| `AiArtifactRepository` | `ai/artifact` | `MongoRepository`, derived finders only |
| `AiArtifactKeyBuilder` | `ai/artifact` | Deterministic input fingerprint |
| `AiArtifactSpec<R>` | `ai/artifact` | Record: artifact key + codec instance + consumer id |
| `AiArtifactHit<R>` | `ai/artifact` | Record: the artifact row + its decoded payload |
| `AiArtifactPayload` | `ai/artifact` | Record: encoded bytes/text + kind + content type |
| `AiArtifactService` | `ai/artifact` | find / stage / markConsumed / markRecoveryFailure / load / acquireLease |
| `AiArtifactProperties` | `ai/artifact` | `@ConfigurationProperties("ai.artifact")` |
| `AiArtifactRecoveryJob` | `ai/artifact` | `@Scheduled` sweeper (orphan recovery + blob GC) |
| `AiArtifactCodec<R>` | `ai/artifact/codec` | SPI: encode/decode a payload type |
| `AiArtifactCodecRegistry` | `ai/artifact/codec` | Resolve a codec by `codecId` (sweeper path) |
| `GeneratedImageCodec` | `ai/artifact/codec` | `GeneratedImage` ⇄ binary payload |
| `AiArtifactConsumer` | `ai/artifact/consumer` | SPI: the dependent work, re-drivable |
| `AiArtifactConsumerRegistry` | `ai/artifact/consumer` | Resolve a consumer by `consumerId` |
| `VisualizationImageArtifactConsumer` | `ai/service` | Uploads to Supabase and records the URL |

Data flow, with the new tier in the middle:

```
                    ┌──────────────── read-before-spend ────────────────┐
                    │                                                    │
  caller ──► AiRequestQueueService.executeInline(spec, work)             │
                    │                                                    │
                    ├─ findReusable(userId, spec.artifactKey) ───────────┘   HIT ⇒ 0 credits,
                    │                                                         no provider call,
                    │                                                         no AiRequestJob row
                    │  MISS
                    ▼
             withAdmissionControl ─► createJob ─► beginProcessing ─► runWithRetries
                    │                              (credits reserved here)
                    │                                        provider returns
                    ▼
             stage(payload) ──► ai_artifacts (PENDING) + GridFS blob    ◄── write-before-dependency
                    │
                    ▼  outcome returned to caller
        AiArtifactConsumer.consume(artifact, payload)   ── Supabase upload
                    │
          success ──┴── markConsumed ─► status=CONSUMED, blob deleted, expiresAt armed
          failure ───── artifact stays PENDING, blob intact, no expiresAt
                                  │
                                  └─► reused by the next request, or by AiArtifactRecoveryJob
```

---

## 4. Artifact Entity & Indexes

Collection `ai_artifacts`. `String` UUID id, matching the job-shaped entities (`AiRequestJob`, `VisualizationJob`, `RecipeFlowGenerationJob`). All timestamps are `Instant` set **explicitly on every write** — `@EnableMongoAuditing` is absent from this codebase, so `@CreatedDate`/`@LastModifiedDate` are silently null wherever they appear (a trap `VisualizationAsset` has already fallen into).

| Field | Type | Purpose |
|---|---|---|
| `id` | `String` | `UUID.randomUUID().toString()` |
| `userId` | `Long` | Owner; part of the dedup key |
| `artifactKey` | `String` | Deterministic input fingerprint (Section 5) |
| `capability` | `AiCapability` | `TEXT_TO_IMAGE`, etc. |
| `correlationType` / `correlationId` | `String` | `"visualization-image"` / `recipeId::stepId` |
| `status` | `AiArtifactStatus` | `PENDING` / `CONSUMED` / `ABANDONED` |
| `payloadKind` | `AiArtifactPayloadKind` | `BINARY` (GridFS) or `INLINE` |
| `codecId` | `String` | e.g. `"generated-image-v1"`; how the sweeper decodes it |
| `consumerId` | `String` | e.g. `"visualization-image-upload"`; what the sweeper re-drives |
| `contentType` / `payloadSize` | `String` / `long` | Media type and byte length |
| `gridFsId` | `String` | Hex `ObjectId`; null for `INLINE` |
| `inlinePayload` | `String` | Text/JSON; null for `BINARY` |
| `producedByModelKey` / `producedByTier` / `usedFallback` / `creditCost` | — | Producing-model metadata; **never part of the key** |
| `producingJobId` | `String` | The `AiRequestJob.id` that was actually paid for |
| `reuseCount` / `recoveryAttempts` / `lastError` | — | Observability and give-up budget |
| `leaseUntil` | `Instant` | Soft lease keeping sweeper and request path apart |
| `createdAt` / `updatedAt` / `consumedAt` | `Instant` | Lifecycle |
| `expiresAt` | `Instant` | TTL anchor; **null while `PENDING`** |

### 4.1 Indexes

| Index | Definition | Why |
|---|---|---|
| `artifact_dedup_idx` | `{userId: 1, artifactKey: 1}`, unique | The reuse lookup and the race-safe dedup guarantee |
| `artifact_sweep_idx` | `{status: 1, createdAt: 1}` | Orphan scan |
| `artifact_blob_gc_idx` | `{status: 1, gridFsId: 1}` | Blob GC pass |
| `artifact_ttl_idx` | `expiresAt`, `expireAfter = "0s"` | Metadata retention after consumption |

**`userId` is in the dedup key deliberately.** Cross-user payload sharing would leak one user's generated content to another and misattribute the billing that produced it. The cost — two users with an identical prompt each pay once — is the correct trade.

**The TTL index is safe precisely because `expiresAt` stays null while `PENDING`.** MongoDB's TTL monitor skips documents whose indexed field is absent or non-date, so an artifact still awaiting its dependent work can never be expired, no matter how long it waits. `expiresAt` is set only on the transition to `CONSUMED` or `ABANDONED`.

This is the **first TTL index in the codebase**. MongoDB will not alter an existing TTL index's options on a subsequent boot, so changing the retention semantics later requires dropping the index by hand. This must be stated in the entity's javadoc.

### 4.2 Status semantics

| Status | Payload | `expiresAt` | Reusable | Swept |
|---|---|---|---|---|
| `PENDING` | Present (GridFS or inline) | null — never expires | Yes | Yes |
| `CONSUMED` | Deleted | `now + consumedRetention` (7d) | No | GC pass only |
| `ABANDONED` | Deleted | `now + abandonedRetention` (30d) | No | GC pass only |

There is deliberately **no `RECOVERING` status**. Concurrency is guarded by leases, not statuses, so a lease held by an instance that crashes self-heals by expiry rather than needing a status repair pass.

---

## 5. Key / Fingerprint Design

```java
AiArtifactKeyBuilder.build(capability, correlationType, correlationId, inputs...)
```

produces

```
TEXT_TO_IMAGE::visualization-image::recipe-42::step-7::9f2c8a1d4e6b0375c1a8e4f27b3d6092
```

### 5.1 Scope first, content second

`common/utils/VisualizationKeyBuilder`'s javadoc records exactly the failure mode this must not repeat: a *purely content-derived* key made two unrelated sparse steps normalize to the same generic string — `"mix::unspecified::unspecified"` — so one step's image was served for a completely different step, possibly in a different recipe.

The structural fix here is that **the content hash is never the whole key**. It is always a suffix beneath `capability + correlationType + correlationId`, and for the visualization path `correlationId` is `VisualizationKeyBuilder.build(recipeId, stepId)` — already globally unique per (recipe, step). Two different steps with identical or blank prompts therefore land on different artifact keys and **cannot** share a payload.

The hash exists only to *invalidate within a scope*, never to *merge across scopes*. This paragraph belongs in the class javadoc with an `@see VisualizationKeyBuilder`.

### 5.2 Construction rules

- SHA-256 via `MessageDigest`, hex via `java.util.HexFormat` (Java 17), truncated to 32 hex characters — 128 bits, index-friendly.
- Inputs normalized (`null` → `""`, `trim()`) and joined with ` `, so `("ab","c")` and `("a","bc")` cannot collide.
- Never `String.hashCode()`, and never fewer than 128 retained bits — 32-bit collisions within a shared scope are the same class of bug the javadoc warns about.

### 5.3 The model is not in the key

A `gemini-image` call that fell back to `drawthings-sdxl` still produced a real payload. Keying on the resolved model would orphan that payload the moment routing configuration changed, forcing a regeneration of something already in hand. The model is recorded as metadata instead — and replayed into `VisualizationAsset` on a cache hit, so the UI still reports which model produced the image the user is looking at.

**Corollary on savings:** `ModelSelectionService.select` returns early with no reservation for `OPEN_SOURCE` models, so a hit on a `drawthings-sdxl`-produced artifact saves latency rather than credits. With `ai.default-model.TEXT_TO_IMAGE=gemini-image` (PAID, cost 5) the credit-saving case is the common one.

---

## 6. Codec SPI

```java
public interface AiArtifactCodec<R> {
    String id();                                    // stored as AiArtifact.codecId
    AiArtifactPayload encode(R value) throws Exception;
    R decode(AiArtifactPayload payload) throws Exception;
}
```

**Binary vs inline is the codec's decision**, since only the codec knows whether its type is bytes or text. `AiArtifactService` enforces one guardrail on top: an `INLINE` payload exceeding `ai.artifact.max-inline-bytes` (default 16 000) is promoted to GridFS with the same content type, so a runaway LLM response can never push an artifact document toward MongoDB's 16 MB limit. `AiArtifactPayload` exposes `asBytes()` / `asText()` so a promoted payload round-trips transparently.

`GeneratedImageCodec` (`id = "generated-image-v1"`) maps `ImageGenerationClient.GeneratedImage(mimeType, data)` to and from a binary payload. The `v1` suffix is the forward-compatibility hook: if `GeneratedImage` gains a field, register `generated-image-v2` and leave `v1` decodable, so `PENDING` artifacts written by the previous deployment still recover after a rolling update.

`AiArtifactCodecRegistry` indexes codecs by `id()` from a constructor-injected `List<AiArtifactCodec<?>>` — the same pattern `AiClientResolver` uses — failing fast on duplicate ids. The **request path never needs the registry**: it holds the codec instance inside its `AiArtifactSpec`, so `R` is checked at compile time at the call site. The registry exists for the **sweeper**, which starts from a database row that knows only a `codecId` string. That is the single place an unchecked cast occurs, and it is confined behind the registry.

---

## 7. Queue Integration & the Zero-Credit Guarantee

### 7.1 Why placement is the whole design

Credits are reserved inside `ModelSelectionService.select(...)`, which is called from `AiRequestQueueService.beginProcessing(...)` — that is, **before the `AiWork` lambda ever runs**:

```
executeInline ─► withAdmissionControl ─► createJob ─► beginProcessing ─► runWithRetries
                                                       │                    │
                                                  select() reserves    AiWork runs here
```

A reuse check placed inside the `AiWork` lambda — the intuitive spot — would therefore still reserve and consume a credit on every cache hit, defeating the entire purpose. **The check must short-circuit before `executeInline`/`executeBounded` enters `withAdmissionControl`.**

### 7.2 The overloads

Add 8-argument overloads of `executeInline` and `executeBounded` taking an `AiArtifactSpec<R>`. The existing 7-argument signatures delegate with `artifactSpec = null`, so **no current caller changes behaviour and no un-wired capability is affected**.

```java
public <R> AiRequestOutcome<R> executeInline(
        Long userId, AiCapability capability, String preferredModelKey, String idempotencyKey,
        String correlationType, String correlationId, AiArtifactSpec<R> artifactSpec, AiWork<R> work) {

    // Read-before-spend. beginProcessing -> ModelSelectionService.select is where credits are
    // reserved, so any check downstream of it would already have cost the user a credit.
    if (artifactSpec != null) {
        Optional<AiArtifactHit<R>> hit = artifactService.findReusable(userId, artifactSpec);
        if (hit.isPresent()) {
            return AiRequestOutcome.reused(hit.get(), reusedSelection(hit.get().artifact()));
        }
    }

    return withAdmissionControl(AI_IMAGE_POOL, capability, () -> {
        AiRequestJob job = createJob(...);
        ModelSelectionOutcome selection = beginProcessing(job, capability, preferredModelKey);
        AiRequestOutcome<R> outcome = runWithRetries(job, selection, () -> work.run(selection));
        return withStagedArtifact(userId, capability, correlationType, correlationId,
                                  artifactSpec, outcome);   // write-before-dependency
    });
}
```

Four properties worth stating explicitly:

- **The check is outside `withAdmissionControl`.** A hit performs no provider work, so it must not be rejected with `AiQueueFullException` while the queue is saturated. A burst of retried-after-failure requests should be *absorbed* by the store, not bounced by it.
- **No `AiRequestJob` row is created on a hit**, so `ai_request_jobs` keeps meaning "a provider call we actually made". Reuse is observable through `AiArtifact.reuseCount` instead.
- **`withStagedArtifact` persists before returning to the caller**, so dependent work can never run against a payload that is not yet durable. It wraps staging in its own try/catch: a Mongo failure logs `ERROR` and returns the un-staged outcome rather than throwing, because failing an otherwise-successful *paid* call in order to report a bookkeeping failure is strictly worse than today's behaviour.
- **A cache hit carries no reservation.** `reusedSelection(artifact)` rebuilds a `ModelSelectionOutcome` from the recorded metadata with `reservation() == null`, so nothing downstream can consume or release credits for a reused payload. `jobId` on a hit is the artifact's `producingJobId`, so the audit trail still points at the call that was actually paid for.

`AiRequestOutcome<R>` gains `boolean reused` and `AiArtifact artifact`; every construction site is inside `AiRequestQueueService`, so the blast radius is that class plus `AIRecipeVisualizationService`.

### 7.3 Atomic operations, not read-modify-write

Following the house convention established by `CreditService` and `VisualizationJobService`, every mutation is a single `findAndModify` / `updateFirst`.

**`findReusable`** — one operation that both tests reusability and takes the lease:

```java
Query q = query(where("userId").is(userId)
        .and("artifactKey").is(spec.artifactKey())
        .and("status").is(AiArtifactStatus.PENDING));
Update u = new Update().inc("reuseCount", 1)
        .set("updatedAt", now)
        .set("leaseUntil", now.plusMillis(properties.getLeaseTtlMs()));
```

`status = PENDING` in the *query* is what makes a `CONSUMED` row — payload already deleted, metadata awaiting TTL — correctly read as a miss. If the GridFS blob turns out to be missing (a GC race), log a warning and return empty so the caller regenerates rather than crashes.

**`stage`** — an **upsert into the `(userId, artifactKey)` slot**, not an insert. A `CONSUMED` row still occupies that slot until TTL, so a plain `save()` of a regenerated payload would hit `DuplicateKeyException`. The update must `unset("expiresAt")` to lift the TTL off a reincarnated slot, and `setOnInsert` the id and `createdAt`. Unlike `createJob` — where a `DuplicateKeyException` is deliberately allowed to propagate as a 409 — a race here is caught and resolved by re-reading, because from the caller's perspective the payload *was* stored. A stale `gridFsId` from a previous incarnation is deleted only *after* the update succeeds, so a crash leaves a garbage blob (which the GC pass collects) rather than a `PENDING` row pointing at nothing.

**`markConsumed`** — status flip first, blob delete second:

```java
UpdateResult r = mongoTemplate.updateFirst(
    query(where("_id").is(artifactId).and("status").is(AiArtifactStatus.PENDING)),
    new Update().set("status", CONSUMED).set("consumedAt", now).set("updatedAt", now)
                .set("expiresAt", now.plus(properties.getConsumedRetention()))
                .unset("leaseUntil"),
    AiArtifact.class);
if (r.getModifiedCount() == 0) return false;    // another actor already consumed it
// only the winner deletes the blob
```

The ordering is load-bearing and must be commented in the code. The guarded flip is a single-winner election, so the blob is only ever deleted by the thread that won. Crashing between the flip and the delete leaves a `CONSUMED` row still carrying `gridFsId` — exactly what the GC pass queries for. **The inverse ordering would risk a `PENDING` row whose blob is gone, which the sweeper would then retry forever.**

---

## 8. Rewritten Image Path

### 8.1 The consumer becomes a bean

The dependent work must be callable from **both** the request path and the sweeper — and the sweeper has no lambda to resurrect. So it becomes an SPI implementation:

```java
public interface AiArtifactConsumer {
    String consumerId();
    String consume(AiArtifact artifact, Object payload) throws Exception;   // returns the resulting URL
}
```

`VisualizationImageArtifactConsumer` (`consumerId = "visualization-image-upload"`) finds the `VisualizationAsset` by `artifact.getCorrelationId()` — already the unique `visualizationKey`, so no extra plumbing is needed to locate the row from a sweeper thread — uploads to Supabase, and writes `imageUrl` plus the producing-model metadata via `mongoTemplate.updateFirst` (not read-modify-save, since a sweeper thread and a request thread may both be writing this document).

**The request path calls this same bean.** That is the point: one implementation of "upload and record", so the sweeper can never drift from the online path.

### 8.2 `generateImage`

```
build AiArtifactSpec(key = fingerprint(TEXT_TO_IMAGE, "visualization-image", visualizationKey, imagePrompt))
  ↓
executeInline(..., spec, work)        ← payload is durable here, whether fresh or reused
  ↓
consumer.consume(artifact, payload)   ← Supabase upload
  ↓
artifactService.markConsumed(id)      ← blob deleted, TTL armed
```

**On upload failure — the core requirement:** the exception propagates out of `consume`, `markConsumed` is never reached, and the artifact **stays `PENDING` with its blob intact and no `expiresAt`**. The asset row records `imageUrl = null` plus a new `imageFailureReason` carrying the real cause. The next request for that step hits `findReusable` and gets the image back for free; if no request ever comes, the sweeper picks it up after the grace window. The paid `byte[]` is never again reachable only from a local variable.

The outer `catch` is kept — `VisualizationJobService.isEffectivelySuccessful` (`:181-183`) and its javadoc depend on `generateImage` returning rather than throwing — but `recordStepResult` (`:162`) is updated to surface `imageFailureReason` instead of the hardcoded string, so a Supabase outage finally reaches the job document as itself.

### 8.3 Additive entity change

`VisualizationAsset` gains `private String imageFailureReason;` (nullable). Additive mapping change, no migration — consistent with the codebase's no-Flyway/Liquibase/Mongock convention.

---

## 9. Recovery Sweeper

`AiArtifactRecoveryJob`, `@Scheduled(fixedDelayString = "${ai.artifact.sweeper.fixed-delay-ms:300000}")`. This is the second `@Scheduled` bean after `CreditCycleResetJob`, whose style it follows: enablement guarded by an early `if (!enabled) return;` rather than `@ConditionalOnProperty` (which this codebase does not use anywhere), and a javadoc paragraph on multi-instance safety.

**Pass 1 — orphan recovery.**

```
status = PENDING
  AND createdAt < now - orphanAgeMs
  AND recoveryAttempts < maxRecoveryAttempts
  AND (leaseUntil missing OR leaseUntil < now)
```

Per candidate: `acquireLease(id)` (atomic `findAndModify` re-checking the lease; `null` ⇒ another instance won, skip) → `load` (codec resolved by `codecId`, GridFS or inline read, decode) → `consumerRegistry.find(consumerId)` → `consume(...)` → `markConsumed(...)`. On any exception, `markRecoveryFailure` increments `recoveryAttempts`, records `lastError`, and releases the lease; on reaching the budget it flips to `ABANDONED` with `expiresAt` set, so the diagnostic row self-cleans and the blob becomes GC-eligible.

**Pass 2 — blob GC.** `CONSUMED`/`ABANDONED` rows still carrying a `gridFsId` get the blob deleted and the reference unset. This reclaims blobs orphaned by a crash between the two steps of `markConsumed`, and is why `consumedRetention` (7d) must stay far larger than the sweep interval (5m): if TTL removed the metadata row first, the blob would be unreferenced forever.

### 9.1 The invariant that makes the sweeper safe

**The sweeper never touches `AiRequestQueueService`, `ModelSelectionService`, `CreditService`, or any `ImageGenerationClient`.** It only re-runs *dependent* work over an already-paid payload. A sweeper bug is therefore structurally incapable of spending credits or calling a provider. This is asserted directly in its unit test with `verifyNoInteractions(...)`, not merely documented.

### 9.2 Not fighting the request path

| Actor | Query guard | Effect |
|---|---|---|
| Request `findReusable` | `status = PENDING` | Takes lease `now + leaseTtlMs` |
| Sweeper orphan scan | `createdAt < now - orphanAge` AND lease free | Skips anything a request is working on |
| Sweeper claim | atomic re-check of the lease | `null` ⇒ lost the race, skip |
| `markConsumed` | `_id = x AND status = PENDING` | Single winner elects itself |

Three independent mechanisms: a grace window far longer than a normal upload, a lease, and a single-winner guard. Worst case — a lease expiring mid-upload — is two Supabase uploads to two different UUID paths; the losing `markConsumed` returns `false` and its caller skips the asset write, leaving one orphaned Supabase object. This is accepted and documented; distributed locking is not warranted at this scale.

---

## 10. Failure, Retry & Error Fidelity

Making the store useful requires the surrounding error handling to tell the truth. These changes ship as Phase 1, *before* the store, so the failures it will catch become visible first.

| File | Change | Why |
|---|---|---|
| `restclient/client/gemini/GeminiImageClient.java` | Wrap `RestClient` errors: 401/403 → `AIAuthenticationException`, other statuses → `AICommunicationException`, `ResourceAccessException` → `AITimeoutException`, `JsonProcessingException` → `AIInvalidResponseException`. Mirror `GeminiClient.chat:112-155`. | Without this, `isRetryable:230` never matches an image failure and `ai.request.max-retries` is dead config for TEXT_TO_IMAGE. |
| `restclient/client/drawthings/DrawThingsImageClient.java` | Same wrapping. | A self-hosted Draw Things server is the most likely thing to be transiently unreachable. |
| `restclient/client/supabase/SupabaseImageStorageClient.java` | Same wrapping; validate blank url/serviceKey/bucket. | Gives `GlobalExceptionHandler` correct status mapping and gives `imageFailureReason` a meaningful value instead of a raw `HttpClientErrorException` message. |
| `AIRecipeVisualizationService` | Guard the two null-asset NPEs (`:223`, `:388-389`); record `imageFailureReason`; log the artifact id. | Section 2.5. |
| `VisualizationJobService` | `recordStepResult` surfaces `imageFailureReason`. | The real cause reaches the job document. |
| `AiRequestQueueService.runWithRetries` | Persist `job.setAttempt(i)` inside the loop. | The stored attempt count is currently always 1. |

**The Supabase upload is deliberately *not* moved inside the `AiWork` lambda** to gain retries. Doing so would put the upload failure back inside the credit-consuming region — precisely the bug being fixed. **The sweeper is the upload's retry mechanism**, with `maxRecoveryAttempts` as its budget.

### 10.1 Resulting failure matrix

| Failure | Credits | AI payload | Recovery |
|---|---|---|---|
| Provider 5xx / timeout | Released | None produced | `runWithRetries` (now actually retries, Phase 1) |
| Provider returns, staging fails | Consumed | Lost (degrades to today) | Regeneration; logged at ERROR |
| Provider returns, Supabase fails | Consumed | **Retained, `PENDING`** | Next request (free) or sweeper |
| Provider returns, JVM crashes before upload | Consumed | **Retained, `PENDING`** | Sweeper after `orphanAgeMs` |
| Supabase fails `maxRecoveryAttempts` times | Consumed | Dropped at `ABANDONED` | Manual; `lastError` retained 30d |

---

## 11. Configuration

`AiArtifactProperties`, `prefix = "ai.artifact"`:

| Property | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch; `false` makes find/stage no-ops |
| `max-inline-bytes` | `16000` | Above this, an inline payload is promoted to GridFS |
| `lease-ttl-ms` | `120000` | How long a consumer "owns" an artifact |
| `consumed-retention` | `7d` | Metadata retention after success |
| `abandoned-retention` | `30d` | Metadata retention after give-up |
| `sweeper.enabled` | `true` | Sweeper switch |
| `sweeper.fixed-delay-ms` | `300000` | Sweep interval |
| `sweeper.initial-delay-ms` | `60000` | Startup grace |
| `sweeper.orphan-age-ms` | `120000` | Grace before an artifact is considered orphaned |
| `sweeper.batch-size` | `25` | Bounded work per run |
| `sweeper.max-recovery-attempts` | `3` | Give-up budget |

Keys are appended to `backend/src/main/resources/application.properties` (there is no `application.yml`), together with `spring.data.mongodb.gridfs.bucket=ai_artifacts`, and mirrored into `exampleProps`.

`spring.data.mongodb.gridfs.bucket` is global for the auto-configured `GridFsTemplate`. That is acceptable today because this is the only GridFS consumer; if a second appears, define a dedicated `GridFsTemplate` bean in `common/config` rather than sharing the bucket.

Consider raising `spring.task.scheduling.pool.size` to 2 so a slow sweep cannot starve `CreditCycleResetJob`'s nightly cron.

`ai.artifact.enabled=false` is the instant rollback — no redeploy.

---

## 12. MongoDB Changes

Additive only, consistent with the established convention (no Flyway/Liquibase/Mongock; `spring.data.mongodb.auto-index-creation=true` creates indexes at boot).

| Collection | Change | Notes |
|---|---|---|
| `ai_artifacts` | **New** | Four indexes per Section 4.1, including the codebase's first TTL index |
| `ai_artifacts.files` / `.chunks` | **New** | GridFS bucket, created on first store |
| `visualization_asset` | `+ imageFailureReason` (nullable) | Additive field, no backfill |

No existing document is rewritten and no index is removed.

### 12.1 Storage footprint

The configured cluster is MongoDB Atlas. PNG blobs in GridFS consume that quota, so the retention design is what keeps it bounded: a blob's normal lifetime is **the duration of one Supabase upload**, after which `markConsumed` deletes it and only a small metadata row survives to TTL. Steady-state blob storage is therefore proportional to *in-flight and failed* generations, not to total generations. If the cluster tier makes even that uncomfortable, the alternative is staging into Supabase under a `staging/` prefix — but that makes the store depend on the very backend whose failure it insures against, which is why GridFS is the default.

---

## 13. Testing Strategy

House style, per `RecipeFlowGenerationJobServiceTest` and `VisualizationJobServiceTest`: package-private classes and methods, JUnit 5 + Mockito with no `@ExtendWith`, every collaborator hand-built in `@BeforeEach` with `mock(X.class)` and passed to the real constructor, real `ThreadPoolTaskPool` instances for async, `verify(..., timeout(5000)...)`, plain `Assertions.*`. Tests live in `backend/src/test/java/.../service/`, where all existing unit tests live regardless of production package. `SupabaseStorageServiceTest` is explicitly **not** a model — it is a live `@SpringBootTest` that uploads to real Supabase.

### The two tests that prove the feature

**`AiRequestQueueServiceArtifactTest`** — the billing test. With a **PAID** `ModelDefinition` and a mocked `CreditService`:
- artifact hit ⇒ `verify(creditService, never()).reserve(...)`, `verify(jobRepository, never()).save(...)`, the work lambda's counter stays at 0, `outcome.reused()` true, `outcome.selection().reservation()` null;
- artifact miss ⇒ work runs once and `InOrder` proves `stage(...)` precedes the return;
- `stage` throwing ⇒ the outcome is still returned, no exception escapes;
- null spec ⇒ `verifyNoInteractions(artifactService)`, proving un-wired capabilities are untouched.

**`AIRecipeVisualizationServiceArtifactTest`** — the end-to-end test. `upload` throws ⇒ the saved asset has `imageUrl == null`, a non-null `imageFailureReason` naming the thrown type, and `markConsumed` never called. Then a second `resolveVisualizationAsset` for the same step ⇒ **`verify(imageGenerationClient, times(1)).generate(...)`** and `verify(imageStorageClient, times(2)).upload(...)`. One provider call, two upload attempts — the entire feature in one assertion pair.

### Supporting tests

| Test | Locks in |
|---|---|
| `AiArtifactKeyBuilderTest` | Blank prompt under two `correlationId`s ⇒ **different** keys (the Section 5.1 regression guard); changed prompt ⇒ different key; model key absent from the key |
| `AiArtifactServiceTest` | `PENDING`-guarded queries; `expiresAt` unset while pending; inline promotion above `maxInlineBytes`; no blob delete when `modifiedCount == 0`; missing blob ⇒ empty, not throw; `enabled=false` ⇒ no-op |
| `AiArtifactRecoveryJobTest` | Orphan recovered and consumed; consumer throws ⇒ `markRecoveryFailure`; lost lease ⇒ `verifyNoInteractions(consumer)`; budget exhausted ⇒ `ABANDONED` with `expiresAt`; GC pass; and `verifyNoInteractions(queueService, creditService, imageGenerationClient)` for the Section 9.1 invariant |
| `SupabaseImageStorageClientTest`, `GeminiImageClientTest`, `DrawThingsImageClientTest` | `MockRestServiceServer`: 401 ⇒ `AIAuthenticationException`, 500 ⇒ `AICommunicationException`, connect failure ⇒ `AITimeoutException` |
| `AiRequestQueueServiceRetryTest` | A wrapped `AICommunicationException` is now retried (proving Section 10 re-enabled the path); `AIInvalidResponseException` is not |

### Manual end-to-end

Point `supabase.url` at an unreachable host, trigger `POST /api/recipes/{recipeId}/visualization/jobs`, and confirm an `ai_artifacts` row is `PENDING` with a GridFS blob and the step reports a real Supabase error. Restore `supabase.url`, re-trigger the same step, and confirm from the logs and `ai_request_jobs` that **no new provider call was made** and the image uploaded from the stored payload.

---

## 14. Rollout Plan

Each phase is independently deployable and independently valuable.

1. **Error fidelity.** The wrapping fixes, `imageFailureReason`, the NPE guards, the attempt counter. No new collection. Immediately makes image retries work and failures diagnosable — which you want *before* the store exists, so you can see what it will catch.
2. **Artifact core, unwired.** Entity, enums, repository, properties, key builder, codec SPI + registry + `GeneratedImageCodec`, `AiArtifactService`, the GridFS bucket property. Nothing in production calls it; deploying only creates the indexes, de-risking the TTL index on its own.
3. **Queue hook, inert.** `AiArtifactSpec`, the widened `AiRequestOutcome`, the 8-arg overloads, the 7-arg delegation, `AiArtifactService` injected into `AiRequestQueueService`. Behaviour byte-identical; only test constructors change.
4. **Wire TEXT_TO_IMAGE.** Consumer SPI + registry, `VisualizationImageArtifactConsumer`, rewritten `generateImage`. Reuse and crash-safety go live for the visualization path. `ai.artifact.enabled=false` is the rollback.
5. **Sweeper + retention.** Both passes. Ship with `sweeper.enabled=false`, inspect `ai_artifacts` for real `PENDING` rows older than the grace window, then enable.
6. **Optional opt-ins.** A `JsonArtifactCodec` plus specs for `generatePrompt` (`TEXT_TO_TEXT`, `"visualization-prompt"`) and `AIRecipeGenerationService`'s flow generation — the latter would let a JSON-parse failure re-validate raw LLM content without re-calling the model. Each opt-in is one `AiArtifactSpec` at the call site plus a consumer.

---

## 15. Out of Scope

- **Secrets in version control.** `backend/src/main/resources/application.properties` is tracked in git containing a live Mongo URI with password, Gemini API key, Supabase service key, SMTP password, and JWT secret; `MongoConfig` additionally prints the URI to stdout at startup. Requires rotation and externalisation, independently of this work.
- **The duplicate-key bug** at `resolveVisualizationAsset:220-222`: re-prompting an existing asset builds a new `VisualizationAsset` with a fresh sequence id but the same `visualizationKey`, which carries a unique index, so the insert throws `DuplicateKeyException` on a blank-prompt reprocess.
- **`executeInline` ignores `ai.request.timeout-ms.TEXT_TO_IMAGE`** — only `executeBounded` reads it, so an image call is bounded solely by the `RestClient` timeout. The artifact store cannot help with a call that never returns.
- **Upload path encoding asymmetry (confirmed, left as-is).** `SupabaseImageStorageClient` passes the object path as a URI template variable, so its separators are percent-encoded — the request goes to `…/recipe-images/visualizations%2F<key>%2F<id>%2F<uuid>.png` — while the public URL it returns is string-concatenated with raw `/`. Supabase decodes the encoded form, so the two agree in practice, and changing it would alter the key layout of every object already stored. `SupabaseImageStorageClientTest.upload_success_returnsThePublicUrl` now pins both sides so the assumption fails loudly if either changes.
- **Signed URLs / private buckets.** `ImageStorageClient` is upload-only against a public bucket; there is no download, delete, or `createSignedUrl` capability anywhere.
- **`AIVisualizationServiceImpl`**, a legacy stub that fabricates placeholder clip paths and calls no AI or storage client.

---

## 16. Open Questions for Stakeholder Confirmation

1. **Retention windows.** Are 7 days (consumed metadata) and 30 days (abandoned) the right balance between forensic value and Atlas storage? The metadata rows are small; the blobs are already gone by then.
2. **Give-up budget.** `maxRecoveryAttempts = 3` at a 5-minute cadence means an artifact is abandoned ~15 minutes after first becoming eligible. For a multi-hour Supabase outage that is too aggressive — should the sweeper use exponential spacing, or should the budget count only *distinct* sweep windows?
3. **Cross-user reuse.** Section 4.1 scopes the dedup key by `userId` on leakage and billing-attribution grounds. If identical prompts across users turn out to be common, is a shared-payload mode with explicit attribution worth revisiting?
4. **Sweeper in a multi-instance deployment.** The lease design is safe for concurrent instances, but every instance will run its own sweep. At the current scale that is harmless; confirm whether a single-writer election is wanted before horizontal scaling.
5. **Phase 6 priority.** Is persisting raw LLM content for flow generation (so a JSON-parse failure re-validates instead of re-calling the model) worth scheduling now, or after the image path has proven itself in production?
