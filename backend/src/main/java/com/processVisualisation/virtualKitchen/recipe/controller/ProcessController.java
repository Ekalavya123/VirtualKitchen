package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.common.exception.AuthException;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessBatchUpdateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.service.IProcessService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for the new Recipe Tool's {@code Process} model (a
 * recipe's MAIN process and its nested SUBPROCESS documents). Every
 * endpoint is scoped under a recipe id; the authenticated user is resolved
 * from the security context (never trusted from a request parameter) and
 * ownership/visibility is enforced by {@link IProcessService}.
 */
@RestController
@RequestMapping("/api/v1/recipes/{recipeId}/processes")
public class ProcessController {

    @Autowired
    private IProcessService processService;

    /**
     * Lists every process (MAIN and any SUBPROCESS documents) belonging to a recipe.
     *
     * @param recipeId the id of the owning recipe
     * @return an ApiResponse wrapping the recipe's processes
     */
    @GetMapping
    public ApiResponse<List<ProcessResponseDTO>> list(@PathVariable Long recipeId) {
        return build(processService.listByRecipe(recipeId, optionalCurrentUserId()), "fetched");
    }

    /**
     * Creates a new process (MAIN or SUBPROCESS, per the request body) under a recipe.
     * Requires the caller to own the recipe.
     *
     * @param recipeId the id of the owning recipe
     * @param dto      the process type/name/description to persist
     * @return an ApiResponse wrapping the created process
     */
    @PostMapping
    public ApiResponse<ProcessResponseDTO> create(@PathVariable Long recipeId, @Valid @RequestBody ProcessRequestDTO dto) {
        return build(processService.create(recipeId, currentUserId(), dto), "created");
    }

    /**
     * Retrieves a single process by id, scoped to a recipe.
     *
     * @param recipeId  the id of the recipe the process is expected to belong to
     * @param processId the id of the process to fetch
     * @return an ApiResponse wrapping the matching process
     */
    @GetMapping("/{processId}")
    public ApiResponse<ProcessResponseDTO> get(@PathVariable Long recipeId, @PathVariable Long processId) {
        return build(processService.get(recipeId, processId, optionalCurrentUserId()), "fetched");
    }

    /**
     * Updates a process's name/description and replaces its node/edge graph and viewport.
     * Requires the caller to own the recipe.
     *
     * @param recipeId  the id of the recipe the process is expected to belong to
     * @param processId the id of the process to update
     * @param dto       the replacement name/description/nodes/edges/viewport
     * @return an ApiResponse wrapping the updated process
     */
    @PutMapping("/{processId}")
    public ApiResponse<ProcessResponseDTO> update(
            @PathVariable Long recipeId,
            @PathVariable Long processId,
            @Valid @RequestBody ProcessUpdateDTO dto
    ) {
        return build(processService.update(recipeId, processId, currentUserId(), dto), "updated");
    }

    /**
     * Recipe-level save: replaces name/description/nodes/edges/viewport for every process in the
     * request body in one call — the Recipe Tool's single "Save" action for its unified editing
     * session (MAIN + every loaded SUBPROCESS), rather than one independent save per process.
     * Requires the caller to own the recipe. Every process is validated before any of them are
     * persisted, so an invalid process rejects the whole batch.
     *
     * @param recipeId the id of the recipe every listed process is expected to belong to
     * @param dto      the replacement content for each process, keyed by its own processId
     * @return an ApiResponse wrapping the updated processes
     */
    @PutMapping
    public ApiResponse<List<ProcessResponseDTO>> updateAll(@PathVariable Long recipeId, @Valid @RequestBody ProcessBatchUpdateRequestDTO dto) {
        return build(processService.updateAll(recipeId, currentUserId(), dto.getProcesses()), "updated");
    }

    /**
     * Deletes a process. Requires the caller to own the recipe. Rejected while the process is
     * the recipe's referenced MAIN process, or while another process in the recipe still
     * references it.
     *
     * @param recipeId  the id of the recipe the process is expected to belong to
     * @param processId the id of the process to delete
     * @return an ApiResponse with no data, confirming deletion
     */
    @DeleteMapping("/{processId}")
    public ApiResponse<Void> delete(@PathVariable Long recipeId, @PathVariable Long processId) {
        processService.delete(recipeId, processId, currentUserId());
        return build(null, "deleted");
    }

    /**
     * Copy-on-insert reuse: deep-clones a SUBPROCESS (recursively, including nested subprocess
     * references) into a new, independent process within the same recipe. Requires the caller to
     * own the recipe. The MAIN process cannot be copied.
     *
     * @param recipeId  the id of the recipe both the source and the copy belong to
     * @param processId the id of the SUBPROCESS to copy
     * @return an ApiResponse wrapping the newly created copy
     */
    @PostMapping("/{processId}/copy")
    public ApiResponse<ProcessResponseDTO> copy(@PathVariable Long recipeId, @PathVariable Long processId) {
        return build(processService.copy(recipeId, processId, currentUserId()), "created");
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;
        if (!(principal instanceof Long userId)) {
            throw new AuthException("Authentication required to modify this process", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }

    private Long optionalCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;
        return principal instanceof Long userId ? userId : null;
    }

    private <T> ApiResponse<T> build(T data, String msg) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(msg)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
