package com.supplychain.orchestrator.service;

import com.supplychain.common.dto.OrderRequestDto;
import com.supplychain.common.event.*;
import com.supplychain.orchestrator.entity.SagaState;
import com.supplychain.orchestrator.repository.SagaStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SagaOrchestrator {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private SagaStateRepository sagaStateRepository;

    private static final String PAYMENT_TOPIC = "payment-orders";
    private static final String INVENTORY_TOPIC = "inventory-orders";
    private static final String ORDER_TOPIC = "order-updates";

    /**
     * STEP 1: START SAGA
     * Action: Persist State -> Command Payment (PURCHASE)
     */
    @Transactional
    @KafkaListener(topics = "order-event-topic", groupId = "orchestrator-group")
    public void handleOrderEvent(OrderEvent orderEvent) {
        if (OrderStatus.ORDER_CREATED.equals(orderEvent.getOrderStatus())) {
            System.out.println("Orchestrator: Starting Saga for Order " + orderEvent.getRequestId());

            // 1. Persist the Saga State (Context)
            SagaState state = SagaState.builder()
                    .orderId(orderEvent.getRequestId())
                    .userId(orderEvent.getUserId())
                    .productId(orderEvent.getProductId())
                    .amount(orderEvent.getAmount())
                    .status("STARTED")
                    .build();

            sagaStateRepository.save(state);

            // 2. Send Command to Payment Service
            OrderRequestDto paymentCommand = OrderRequestDto.builder()
                    .userId(orderEvent.getUserId())
                    .productId(orderEvent.getProductId())
                    .amount(orderEvent.getAmount())
                    .orderId(orderEvent.getRequestId())
                    .transactionType(TransactionType.PURCHASE) // Explicit Type
                    .build();

            kafkaTemplate.send(PAYMENT_TOPIC, paymentCommand);
        }
    }

    /**
     * STEP 2: PAYMENT UPDATES
     * Handles both "Purchase Success" AND "Refund Success"
     */
    @Transactional
    @KafkaListener(topics = "payment-updates", groupId = "orchestrator-group")
    public void handlePaymentUpdate(PaymentEvent paymentEvent) {
        // Fetch the Persistent State
        SagaState state = sagaStateRepository.findById(paymentEvent.getRequestId())
                .orElse(null);

        if (state == null) {
            System.err.println("Orchestrator Error: State not found for " + paymentEvent.getRequestId());
            return;
        }

        // === BRANCH 1: HANDLING REFUNDS (COMPENSATIONS) ===
        // This is the specific logic that breaks the infinite loop.
        if (TransactionType.COMPENSATION.equals(paymentEvent.getTransactionType())) {
            if (PaymentStatus.PAYMENT_COMPLETED.equals(paymentEvent.getPaymentStatus())) {
                System.out.println("Orchestrator: Refund Successful. Saga Rolled Back.");
                state.setStatus("SAGA_ROLLED_BACK");
                sagaStateRepository.save(state);
                // LOOP BREAKER: We stop here. We do NOT trigger Inventory again.
            } else {
                state.setStatus("REFUND_FAILED"); // Critical state requiring human ops
                sagaStateRepository.save(state);
            }
            return; // Exit method
        }

        // === BRANCH 2: HANDLING PURCHASES (NORMAL FLOW) ===
        // If we get here, we KNOW it's a Purchase event because type != COMPENSATION.
        if (PaymentStatus.PAYMENT_COMPLETED.equals(paymentEvent.getPaymentStatus())) {
            System.out.println("Orchestrator: Payment Success. Fetching context for Product ID...");

            // 1. Update State
            state.setStatus("PAYMENT_COMPLETED");
            sagaStateRepository.save(state);

            // 2. Construct Inventory Command using DB Data
            OrderRequestDto inventoryCommand = OrderRequestDto.builder()
                    .userId(state.getUserId())
                    .productId(state.getProductId())
                    .amount(state.getAmount())
                    .orderId(state.getOrderId())
                    .transactionType(TransactionType.PURCHASE) // Explicit Type
                    .build();

            kafkaTemplate.send(INVENTORY_TOPIC, inventoryCommand);

        } else {
            // Payment Failed -> Cancel Order immediately
            state.setStatus("PAYMENT_FAILED");
            sagaStateRepository.save(state);
            notifyOrderService(paymentEvent.getRequestId(), OrderStatus.ORDER_CANCELLED);
        }
    }

    /**
     * STEP 3: INVENTORY UPDATES
     * Success -> Complete Order
     * Failure -> Trigger Refund (COMPENSATION)
     */
    @Transactional
    @KafkaListener(topics = "inventory-updates", groupId = "orchestrator-group")
    public void handleInventoryUpdate(InventoryEvent inventoryEvent) {
        SagaState state = sagaStateRepository.findById(inventoryEvent.getRequestId())
                .orElse(null);

        if (state == null) return;

        if (InventoryStatus.INVENTORY_ALLOCATED.equals(inventoryEvent.getInventoryStatus())) {
            // Happy Path
            state.setStatus("SAGA_COMPLETED");
            sagaStateRepository.save(state);

            notifyOrderService(inventoryEvent.getRequestId(), OrderStatus.ORDER_COMPLETED);
            System.out.println("Orchestrator: Saga Finished Successfully for " + state.getOrderId());

        } else {
            // Failure Path: Out of Stock -> Compensate
            System.out.println("Orchestrator: Inventory Unavailable. Initiating Refund...");
            state.setStatus("INVENTORY_FAILED");
            sagaStateRepository.save(state);

            // Create Refund Command
            OrderRequestDto refundCommand = OrderRequestDto.builder()
                    .userId(state.getUserId())
                    .productId(state.getProductId())
                    .amount(state.getAmount())
                    .orderId(state.getOrderId())
                    .transactionType(TransactionType.COMPENSATION) // Explicit Refund Type
                    .build();

            kafkaTemplate.send(PAYMENT_TOPIC, refundCommand);

            // Cancel the Order in Order Service
            notifyOrderService(inventoryEvent.getRequestId(), OrderStatus.ORDER_CANCELLED);
        }
    }

    private void notifyOrderService(UUID orderId, OrderStatus status) {
        OrderEvent orderEvent = OrderEvent.builder()
                .requestId(orderId)
                .orderStatus(status)
                .build();
        kafkaTemplate.send(ORDER_TOPIC, orderEvent);
    }
}