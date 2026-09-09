package com.processVisualisation.virtualKitchen.ai.controller;

import com.processVisualisation.virtualKitchen.ai.dto.AIResponseRecordDTO;
import com.processVisualisation.virtualKitchen.ai.model.AIResponseDocument;
import com.processVisualisation.virtualKitchen.ai.service.IAIResponseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * REST controller for CRUD access to persisted AI response records under
 * {@code /api/ai/responses}. Backs the analytics/training log of AI
 * interactions maintained via {@link IAIResponseService}.
 */
@RestController
@RequestMapping("/api/ai/responses")
public class AIResponseController {

    private final IAIResponseService aiResponseService;

    public AIResponseController(IAIResponseService aiResponseService) {
        this.aiResponseService = aiResponseService;
    }

    /**
     * Persists a new AI response record.
     *
     * @param dto the validated response record to save
     * @return HTTP 201 with the saved document and a {@code Location} header
     *         pointing at the new resource
     */
    @PostMapping
    public ResponseEntity<AIResponseDocument> create(@Valid @RequestBody AIResponseRecordDTO dto) {
        AIResponseDocument saved = aiResponseService.save(dto);
        return ResponseEntity.created(URI.create("/api/ai/responses/" + saved.getId())).body(saved);
    }

    /**
     * Lists AI response records, optionally filtered by context and/or success
     * status.
     *
     * @param context optional context name to filter by (e.g. "chat")
     * @param success optional success flag to filter by
     * @return HTTP 200 with the matching list of response documents
     */
    @GetMapping
    public ResponseEntity<List<AIResponseDocument>> list(@RequestParam(required = false) String context,
                                                        @RequestParam(required = false) Boolean success) {
        if (context != null && success != null) {
            return ResponseEntity.ok(aiResponseService.findByContextAndSuccess(context, success));
        }
        if (context != null) {
            return ResponseEntity.ok(aiResponseService.findByContext(context));
        }
        return ResponseEntity.ok(aiResponseService.findAll());
    }

    /**
     * Retrieves a single AI response record by its identifier.
     *
     * @param id the document identifier
     * @return HTTP 200 with the document if found, otherwise HTTP 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<AIResponseDocument> getById(@PathVariable String id) {
        return aiResponseService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deletes an AI response record by its identifier.
     *
     * @param id the document identifier to delete
     * @return HTTP 204 with no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        aiResponseService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

