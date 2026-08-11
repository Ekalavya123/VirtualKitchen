package com.processVisualisation.virtualKitchen.controller;

import com.processVisualisation.virtualKitchen.dto.OrderCreateRequestDTO;
import com.processVisualisation.virtualKitchen.dto.OrderResponseDTO;
import com.processVisualisation.virtualKitchen.service.IOrderService;
import com.processVisualisation.virtualKitchen.utils.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @Autowired
    private IOrderService orderService;

    @PostMapping
    public ApiResponse<OrderResponseDTO> createOrder(@RequestBody OrderCreateRequestDTO dto) {
        return build(orderService.createOrder(dto), "created");
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<OrderResponseDTO>> getOrdersByUser(@PathVariable Long userId) {
        return build(orderService.getOrdersByUser(userId), "fetched");
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
