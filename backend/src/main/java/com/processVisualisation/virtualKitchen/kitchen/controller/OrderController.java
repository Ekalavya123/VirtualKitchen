package com.processVisualisation.virtualKitchen.kitchen.controller;

import com.processVisualisation.virtualKitchen.kitchen.dto.OrderCreateRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.OrderResponseDTO;
import com.processVisualisation.virtualKitchen.kitchen.service.IOrderService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller exposing order endpoints under {@code /api/v1/orders}:
 * placing a new order and retrieving a user's order history. Delegates all
 * business logic to {@link IOrderService}.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @Autowired
    private IOrderService orderService;

    /**
     * Places a new order for the requested items. Handles
     * {@code POST /api/v1/orders}.
     *
     * @param dto the ordering user id and the requested line items
     * @return an {@link ApiResponse} wrapping the created order
     * @throws IllegalArgumentException if the user id or items are missing, or
     *                                  the user does not exist
     * @throws IllegalStateException    if the user has no kitchen to receive
     *                                  the ordered inventory
     */
    @PostMapping
    public ApiResponse<OrderResponseDTO> createOrder(@RequestBody OrderCreateRequestDTO dto) {
        return build(orderService.createOrder(dto), "created");
    }

    /**
     * Fetches all orders placed by a given user, most recently created
     * first. Handles {@code GET /api/v1/orders/user/{userId}}.
     *
     * @param userId id of the user
     * @return an {@link ApiResponse} wrapping the user's orders
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<OrderResponseDTO>> getOrdersByUser(@PathVariable Long userId) {
        return build(orderService.getOrdersByUser(userId), "fetched");
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
    private <T> ApiResponse<T> build(T data, String msg) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(msg)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
