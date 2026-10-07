package com.processVisualisation.virtualKitchen.ai.globalasset;

import com.processVisualisation.virtualKitchen.ai.globalasset.dto.GlobalAssetBatchRequestDTO;
import com.processVisualisation.virtualKitchen.ai.globalasset.dto.GlobalAssetBatchResponseDTO;
import com.processVisualisation.virtualKitchen.ai.globalasset.dto.GlobalAssetImageResponseDTO;
import com.processVisualisation.virtualKitchen.common.security.CurrentUser;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Admin API for global (recipe-independent) asset images: one reusable image per store catalog
 * ingredient or piece of equipment. {@code resourceType} is {@code ingredients} or
 * {@code equipment}.
 * <p>
 * ADMIN only: enforced by the {@code /api/v1/admin/**} rule in {@code SecurityConfig} and again
 * here via {@link CurrentUser#requireAdmin()}. Generation is asynchronous; requests return 202
 * with the queued state, and {@code GET .../image} reports progress.
 */
@RestController
@RequestMapping("/api/v1/admin/global-assets/{resourceType}")
public class AdminGlobalAssetController {

    private final GlobalAssetImageService imageService;

    public AdminGlobalAssetController(GlobalAssetImageService imageService) {
        this.imageService = imageService;
    }

    /**
     * Queues image generation for one resource. Idempotent: answers 200 ALREADY_EXISTS when it
     * already has an image, and 202 IN_PROGRESS (without a new job) when one is running.
     */
    @PostMapping("/{resourceId}/image")
    public ResponseEntity<ApiResponse<GlobalAssetImageResponseDTO>> generate(
            @PathVariable String resourceType, @PathVariable Long resourceId) {
        Long adminId = CurrentUser.requireAdmin();
        GlobalAssetImageResponseDTO result =
                imageService.request(GlobalResourceType.fromPathSegment(resourceType), resourceId, adminId);
        HttpStatus status = result.getOutcome() == GlobalAssetImageResponseDTO.Outcome.ALREADY_EXISTS
                ? HttpStatus.OK
                : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(build(result, result.getMessage()));
    }

    /** The resource's current image and generation state. */
    @GetMapping("/{resourceId}/image")
    public ApiResponse<GlobalAssetImageResponseDTO> status(
            @PathVariable String resourceType, @PathVariable Long resourceId) {
        CurrentUser.requireAdmin();
        GlobalAssetImageResponseDTO result =
                imageService.status(GlobalResourceType.fromPathSegment(resourceType), resourceId);
        return build(result, result.getMessage());
    }

    /** Queues image generation for the listed resources (at most 200), reporting each one's outcome. */
    @PostMapping("/images")
    public ResponseEntity<ApiResponse<GlobalAssetBatchResponseDTO>> generateBatch(
            @PathVariable String resourceType, @RequestBody(required = false) GlobalAssetBatchRequestDTO request) {
        Long adminId = CurrentUser.requireAdmin();
        GlobalAssetBatchResponseDTO result = imageService.requestBatch(
                GlobalResourceType.fromPathSegment(resourceType), request == null ? null : request.getResourceIds(), adminId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(build(result, "Image generation requested"));
    }

    /** Queues image generation for every resource of this type that has no image yet. */
    @PostMapping("/images/missing")
    public ResponseEntity<ApiResponse<GlobalAssetBatchResponseDTO>> generateMissing(@PathVariable String resourceType) {
        Long adminId = CurrentUser.requireAdmin();
        GlobalAssetBatchResponseDTO result =
                imageService.requestMissing(GlobalResourceType.fromPathSegment(resourceType), adminId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(build(result, "Image generation requested"));
    }

    private <T> ApiResponse<T> build(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
