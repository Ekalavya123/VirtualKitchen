package com.processVisualisation.virtualKitchen.kitchen.controller;

import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenInventoryRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenInventoryResponseDTO;
import com.processVisualisation.virtualKitchen.kitchen.service.IKitchenInventoryService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller exposing kitchen-level inventory association endpoints
 * under {@code /api/v1/kitchen-inventory}: linking an inventory item to a
 * kitchen, listing a kitchen's inventory allocations, and removing an
 * association. Delegates all business logic to
 * {@link IKitchenInventoryService}.
 */
@RestController
@RequestMapping("/api/v1/kitchen-inventory")
public class KitchenInventoryController {

    @Autowired
    private IKitchenInventoryService service;

    /**
     * Creates a new kitchen-inventory association. Handles
     * {@code POST /api/v1/kitchen-inventory}.
     *
     * @param dto the kitchen id and inventory id to associate
     * @return an {@link ApiResponse} wrapping the created association
     */
    @PostMapping
    public ApiResponse<KitchenInventoryResponseDTO> create(@RequestBody KitchenInventoryRequestDTO dto){
        return build(service.create(dto), "created");
    }

    /**
     * Fetches all inventory allocated to a given kitchen. Handles
     * {@code GET /api/v1/kitchen-inventory/{kitchenId}}.
     *
     * @param kitchenId id of the kitchen
     * @return an {@link ApiResponse} wrapping the kitchen's inventory associations
     */
    @GetMapping("/{kitchenId}")
    public ApiResponse<List<KitchenInventoryResponseDTO>> get(@PathVariable Long kitchenId){
        return build(service.getByKitchen(kitchenId), "fetched");
    }

    /**
     * Deletes a kitchen-inventory association by id. Handles
     * {@code DELETE /api/v1/kitchen-inventory/{id}}.
     *
     * @param id id of the association to delete
     * @return an {@link ApiResponse} with no payload confirming the deletion
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id){
        service.delete(id);
        return build(null, "deleted");
    }

    /**
     * Builds a standard success {@link ApiResponse} envelope for the given
     * payload and message.
     *
     * @param data the response payload
     * @param msg  a short human-readable status message
     * @param <T>  the payload type
     * @return the assembled response envelope
     */
    private <T> ApiResponse<T> build(T data, String msg){
        return ApiResponse.<T>builder()
                .success(true)
                .message(msg)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
