package com.processVisualisation.virtualKitchen.dto;

import com.processVisualisation.virtualKitchen.model.OrderStatus;
import com.processVisualisation.virtualKitchen.model.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponseDTO {

    private Long orderId;
    private Long userId;
    private List<OrderItemResponseDTO> items;
    private double totalAmount;
    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;
    private LocalDateTime createdAt;
}
