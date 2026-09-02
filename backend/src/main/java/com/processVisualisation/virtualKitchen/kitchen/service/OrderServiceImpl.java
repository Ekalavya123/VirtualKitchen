package com.processVisualisation.virtualKitchen.kitchen.service;

import com.processVisualisation.virtualKitchen.kitchen.dto.OrderCreateRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.OrderResponseDTO;
import com.processVisualisation.virtualKitchen.common.mapper.OrderMapper;
import com.processVisualisation.virtualKitchen.kitchen.model.Order;
import com.processVisualisation.virtualKitchen.kitchen.model.OrderItem;
import com.processVisualisation.virtualKitchen.auth.repository.UserRepository;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.kitchen.dto.InventoryRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.model.Kitchen;
import com.processVisualisation.virtualKitchen.kitchen.repository.KitchenRepository;
import com.processVisualisation.virtualKitchen.kitchen.repository.OrderRepository;
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

    @Autowired
    private KitchenRepository kitchenRepository;

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

        // After successfully creating the order, add ordered items to the user's kitchen inventory
        try {
            // fetch kitchen for the user
            List<Kitchen> kitchens = kitchenRepository.findByOwnerId(saved.getUserId());
            if (kitchens == null || kitchens.isEmpty()) {
                throw new IllegalStateException("Kitchen not found for userId=" + saved.getUserId());
            }
            Kitchen kitchen = kitchens.get(0);

            if (saved.getItems() != null) {
                for (OrderItem item : saved.getItems()) {
                    if (item == null) continue;
                    InventoryRequestDTO invReq = new InventoryRequestDTO();
                    invReq.setUserId(saved.getUserId());
                    invReq.setKitchenId(kitchen.getId());
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
            // if the exception is due to missing kitchen we rethrow as requested
            if (ex instanceof IllegalStateException && ex.getMessage().startsWith("Kitchen not found")) {
                throw ex;
            }
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
