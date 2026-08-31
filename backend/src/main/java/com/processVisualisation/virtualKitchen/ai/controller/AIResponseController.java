package com.processVisualisation.virtualKitchen.ai.controller;

import com.processVisualisation.virtualKitchen.ai.dto.AIResponseRecordDTO;
import com.processVisualisation.virtualKitchen.ai.model.AIResponseDocument;
import com.processVisualisation.virtualKitchen.ai.service.IAIResponseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/ai/responses")
public class AIResponseController {

    private final IAIResponseService aiResponseService;

    public AIResponseController(IAIResponseService aiResponseService) {
        this.aiResponseService = aiResponseService;
    }

    @PostMapping
    public ResponseEntity<AIResponseDocument> create(@Valid @RequestBody AIResponseRecordDTO dto) {
        AIResponseDocument saved = aiResponseService.save(dto);
        return ResponseEntity.created(URI.create("/api/ai/responses/" + saved.getId())).body(saved);
    }

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

    @GetMapping("/{id}")
    public ResponseEntity<AIResponseDocument> getById(@PathVariable String id) {
        return aiResponseService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        aiResponseService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

