package com.supplychain.order.service;

import com.supplychain.common.dto.OrderRequestDto;
import com.supplychain.common.event.OrderEvent;
import com.supplychain.common.event.OrderStatus;
import com.supplychain.order.entity.PurchaseOrder;
import com.supplychain.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
public class OrderService {

    @Autowired
    private OrderRepository repository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    // The Topic name the Orchestrator will listen to
    private static final String ORDER_TOPIC = "order-event-topic";

    @Transactional
    public PurchaseOrder createOrder(OrderRequestDto orderRequest) {
        // 1. Convert DTO to Entity
        PurchaseOrder purchaseOrder = convertDtoToEntity(orderRequest);

        // 2. Save to DB with status CREATED
        purchaseOrder = repository.save(purchaseOrder);

        OrderEvent event = OrderEvent.builder()
                .requestId(purchaseOrder.getOrderId())
                .date(new Date())
                .orderStatus(OrderStatus.ORDER_CREATED)
                .userId(purchaseOrder.getUserId())
                .productId(purchaseOrder.getProductId())
                .amount(purchaseOrder.getPrice())
                .build();

        kafkaTemplate.send(ORDER_TOPIC, event.getRequestId().toString(), event);

        System.out.println("Order created and event published: " + purchaseOrder.getOrderId());

        return purchaseOrder;
    }

    private PurchaseOrder convertDtoToEntity(OrderRequestDto dto) {
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setUserId(dto.getUserId());
        purchaseOrder.setProductId(dto.getProductId());
        purchaseOrder.setPrice(dto.getAmount());
        purchaseOrder.setOrderStatus(OrderStatus.ORDER_CREATED);
        purchaseOrder.setOrderId(UUID.randomUUID()); // Generate the Saga ID
        return purchaseOrder;
    }

    @Transactional
    @KafkaListener(topics = "order-updates", groupId = "order-group")
    public void consumeOrderUpdates(OrderEvent orderEvent) {
        System.out.println("Order Service: Update received for " + orderEvent.getRequestId());

        repository.findByOrderId(orderEvent.getRequestId())
                .ifPresent(purchaseOrder -> {
                    // Update the status in the local database
                    purchaseOrder.setOrderStatus(orderEvent.getOrderStatus());
                    repository.save(purchaseOrder);

                    System.out.println("Order Service: Order " + purchaseOrder.getOrderId()
                            + " finalized with status: " + orderEvent.getOrderStatus());
                });
    }
}