package com.processVisualisation.virtualKitchen.kitchen.dto;

import com.processVisualisation.virtualKitchen.kitchen.model.OrderStatus;
import com.processVisualisation.virtualKitchen.kitchen.model.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response payload returned to clients describing a placed order: its line
 * items, computed total, current fulfillment ({@code orderStatus}) and payment
 * ({@code paymentStatus}) state, and when it was created.
 */
@Data
@Builder
public class OrderResponseDTO {

    private Long orderId;
    /** Human-readable code, e.g. {@code SO-000045} (shop orders; recipe orders use {@code RO-}). */
    private String orderCode;
    private Long userId;
    private List<OrderItemResponseDTO> items;
    private double totalAmount;
    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;
    private LocalDateTime createdAt;
}
