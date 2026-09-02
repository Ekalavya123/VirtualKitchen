package com.processVisualisation.virtualKitchen.kitchen.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "orders")
public class Order {

    public static final String SEQUENCE_NAME = "orders_sequence";

    @Id
    private Long orderId;

    private Long userId;

    private List<OrderItem> items;

    private double totalAmount;

    private OrderStatus orderStatus;

    private PaymentStatus paymentStatus;

    private LocalDateTime createdAt;
}
