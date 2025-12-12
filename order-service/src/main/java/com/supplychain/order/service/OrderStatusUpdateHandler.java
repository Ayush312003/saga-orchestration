package com.supplychain.order.service;

import com.supplychain.common.event.OrderEvent;
import com.supplychain.common.event.OrderStatus;
import com.supplychain.order.entity.PurchaseOrder;
import com.supplychain.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderStatusUpdateHandler {

    @Autowired
    private OrderRepository repository;

    @Transactional
    @KafkaListener(topics = "order-updates", groupId = "order-group")
    public void updateOrder(OrderEvent orderEvent) {
        repository.findByOrderId(orderEvent.getRequestId())
                .ifPresent(po -> {
                    po.setOrderStatus(orderEvent.getOrderStatus());
                    repository.save(po);
                    System.out.println("Saga Complete. Order " + po.getOrderId() + " updated to: " + orderEvent.getOrderStatus());
                });
    }
}