package com.supplychain.inventory.service;

import com.supplychain.common.dto.OrderRequestDto;
import com.supplychain.common.event.InventoryEvent;
import com.supplychain.common.event.InventoryStatus;
import com.supplychain.inventory.entity.Inventory;
import com.supplychain.inventory.repository.InventoryRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InventoryService {

    @Autowired
    private InventoryRepository repository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @PostConstruct
    public void initData() {
        // Seed Product ID 1 with 100 items
        repository.save(new Inventory(1, 100));
    }

    @Transactional
    @KafkaListener(topics = "inventory-orders", groupId = "inventory-group")
    public void processInventory(OrderRequestDto orderRequest) {

        System.out.println("Inventory Service: Recieved Order " + orderRequest.getOrderId());

        Inventory inventory = repository.findById(orderRequest.getProductId())
                .orElse(null);

        InventoryEvent event = new InventoryEvent();
        event.setRequestId(orderRequest.getOrderId());
        event.setInventoryId(UUID.randomUUID());

        // Check if stock exists and is sufficient (assuming 1 item per order for simplicity, or we can use amount)
        if (inventory != null && inventory.getAvailableStock() > 0) {

            inventory.setAvailableStock(inventory.getAvailableStock() - 1);
            repository.save(inventory);

            event.setInventoryStatus(InventoryStatus.INVENTORY_ALLOCATED);
        } else {
            event.setInventoryStatus(InventoryStatus.INVENTORY_UNAVAILABLE);
        }

        // Reply to Orchestrator
        kafkaTemplate.send("inventory-updates", event);
    }
}