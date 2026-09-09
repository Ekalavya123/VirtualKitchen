package com.processVisualisation.virtualKitchen.kitchen.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Domain entity representing a customer order placed against a kitchen, persisted
 * in the "orders" MongoDB collection.
 * <p>
 * An Order aggregates the requested {@code OrderItem}s, the computed
 * {@code totalAmount}, and tracks both the fulfillment lifecycle
 * ({@code orderStatus}) and the payment lifecycle ({@code paymentStatus})
 * independently. The {@code id} is generated from the {@code SEQUENCE_NAME}
 * sequence.
 */
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
