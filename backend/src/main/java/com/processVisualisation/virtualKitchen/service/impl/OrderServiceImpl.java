package com.processVisualisation.virtualKitchen.service.impl;

import com.processVisualisation.virtualKitchen.dto.OrderCreateRequestDTO;
import com.processVisualisation.virtualKitchen.dto.OrderResponseDTO;
import com.processVisualisation.virtualKitchen.mapper.OrderMapper;
import com.processVisualisation.virtualKitchen.model.Order;
import com.processVisualisation.virtualKitchen.model.OrderItem;
import com.processVisualisation.virtualKitchen.repository.OrderRepository;
import com.processVisualisation.virtualKitchen.repository.UserRepository;
import com.processVisualisation.virtualKitchen.service.IOrderService;
import com.processVisualisation.virtualKitchen.service.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.service.IInventoryService;
import com.processVisualisation.virtualKitchen.dto.InventoryRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements IOrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private SequenceGeneratorService sequenceGeneratorService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IInventoryService inventoryService;

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);
    @Override
    public OrderResponseDTO createOrder(OrderCreateRequestDTO dto) {
        if (dto == null || dto.getUserId() == null) {
            throw new IllegalArgumentException("userId is required");
        }

        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        userRepository.findById(dto.getUserId()).orElseThrow(() -> new IllegalArgumentException("User not found"));

        Order order = orderMapper.toEntity(dto);
        order.setOrderId(sequenceGeneratorService.generateSequence(Order.SEQUENCE_NAME));
        order.setCreatedAt(LocalDateTime.now());
        order.setTotalAmount(calculateTotal(order.getItems()));

        Order saved = orderRepository.save(order);

        // After successfully creating the order, add ordered items to the user's inventory
        try {
            if (saved.getItems() != null) {
                for (OrderItem item : saved.getItems()) {
                    if (item == null) continue;
                    InventoryRequestDTO invReq = new InventoryRequestDTO();
                    invReq.setUserId(saved.getUserId());
                    invReq.setItemType(item.getItemType());
                    invReq.setItemId(item.getItemId());
                    invReq.setQuantity(item.getQuantity());
                    invReq.setUnit(item.getUnit());
                    inventoryService.addOrUpdate(invReq);
                }
            }
        } catch (Exception ex) {
            // Log and continue - order creation should not fail because inventory update failed
            logger.error("Failed to add ordered items to user inventory for orderId={}", saved.getOrderId(), ex);
        }

        return orderMapper.toDTO(saved);
    }

    @Override
    public List<OrderResponseDTO> getOrdersByUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(orderMapper::toDTO)
                .collect(Collectors.toList());
    }

    private double calculateTotal(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        return items.stream()
                .filter(item -> item != null)
                .mapToDouble(item -> item.getSubTotal())
                .sum();
    }
}
