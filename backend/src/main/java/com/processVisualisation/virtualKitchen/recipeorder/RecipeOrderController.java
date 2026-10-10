package com.processVisualisation.virtualKitchen.recipeorder;

import com.processVisualisation.virtualKitchen.common.security.CurrentUser;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import com.processVisualisation.virtualKitchen.recipeorder.IngredientAvailabilityService.AvailabilityReport;
import com.processVisualisation.virtualKitchen.recipeorder.dto.RecipeOrderRequests;
import com.processVisualisation.virtualKitchen.recipeorder.dto.RecipeOrderResponseDTO;
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
 * The customer's recipe orders, under {@code /api/v1/recipe-orders}. Every endpoint acts for the
 * authenticated user only (401 otherwise); another user's order answers 404. Clients never send a
 * status: each action endpoint asks the server to make one lifecycle move, which it validates.
 */
@RestController
@RequestMapping("/api/v1/recipe-orders")
public class RecipeOrderController {

    private final RecipeOrderService service;
    private final RecipeOrderMapper mapper;

    public RecipeOrderController(RecipeOrderService service, RecipeOrderMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public ApiResponse<List<RecipeOrderResponseDTO>> listMine() {
        return build(service.listMine(CurrentUser.requireUserId()).stream().map(mapper::toSummaryDTO).toList(), "fetched");
    }

    @PostMapping
    public ApiResponse<RecipeOrderResponseDTO> create(@RequestBody RecipeOrderRequests.Create request) {
        return build(mapper.toDTO(service.createDraft(CurrentUser.requireUserId(), request)), "created");
    }

    @GetMapping("/{orderId}")
    public ApiResponse<RecipeOrderResponseDTO> get(@PathVariable Long orderId) {
        return build(mapper.toDTO(service.get(CurrentUser.requireUserId(), orderId)), "fetched");
    }

    @PutMapping("/{orderId}")
    public ApiResponse<RecipeOrderResponseDTO> update(@PathVariable Long orderId, @RequestBody RecipeOrderRequests.Update request) {
        return build(mapper.toDTO(service.updateDetails(CurrentUser.requireUserId(), orderId, request)), "updated");
    }

    @PostMapping("/{orderId}/confirm")
    public ApiResponse<RecipeOrderResponseDTO> confirm(@PathVariable Long orderId,
                                                       @RequestBody(required = false) RecipeOrderRequests.Confirm request) {
        return build(mapper.toDTO(service.confirm(CurrentUser.requireUserId(), orderId, request)), "confirmed");
    }

    /** Read-only: compares the order's requirements with the kitchen's free stock. */
    @GetMapping("/{orderId}/availability")
    public ApiResponse<AvailabilityReport> availability(@PathVariable Long orderId) {
        return build(service.availability(CurrentUser.requireUserId(), orderId), "fetched");
    }

    @PostMapping("/{orderId}/reserve")
    public ApiResponse<RecipeOrderResponseDTO> reserve(@PathVariable Long orderId) {
        return build(mapper.toDTO(service.reserve(CurrentUser.requireUserId(), orderId)), "reserved");
    }

    @PostMapping("/{orderId}/pay")
    public ApiResponse<RecipeOrderResponseDTO> pay(@PathVariable Long orderId, @RequestBody RecipeOrderRequests.Pay request) {
        return build(mapper.toDTO(service.pay(CurrentUser.requireUserId(), orderId, request)), "payment processed");
    }

    @PostMapping("/{orderId}/advance")
    public ApiResponse<RecipeOrderResponseDTO> advance(@PathVariable Long orderId,
                                                       @RequestBody(required = false) RecipeOrderRequests.Advance request) {
        return build(mapper.toDTO(service.advance(CurrentUser.requireUserId(), orderId, request)), "advanced");
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<RecipeOrderResponseDTO> cancel(@PathVariable Long orderId) {
        return build(mapper.toDTO(service.cancel(CurrentUser.requireUserId(), orderId)), "cancelled");
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
