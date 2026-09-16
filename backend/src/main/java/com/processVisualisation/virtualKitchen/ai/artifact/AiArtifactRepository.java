package com.processVisualisation.virtualKitchen.ai.artifact;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link AiArtifact}. Derived finders only, matching the rest of the codebase.
 * <p>
 * Every <em>mutation</em> goes through {@code MongoTemplate} in {@link AiArtifactService}
 * instead, because each one must be a single guarded atomic operation rather than a
 * read-modify-save — the request path and the recovery sweeper write these documents
 * concurrently. The orphan sweep's query is also expressed there, since its
 * "lease absent or expired" disjunction has no derived-finder equivalent.
 */
public interface AiArtifactRepository extends MongoRepository<AiArtifact, String> {

    /** The reuse lookup, backed by the unique {@code artifact_dedup_idx}. */
    Optional<AiArtifact> findByUserIdAndArtifactKey(Long userId, String artifactKey);

    /**
     * Rows whose payload blob still needs collecting — a terminal artifact that still carries a
     * {@code gridFsId}, which happens when a process died between the status flip and the blob
     * delete in {@code markConsumed}.
     */
    List<AiArtifact> findByStatusInAndGridFsIdNotNull(Collection<AiArtifactStatus> statuses, Pageable pageable);
}
