package com.supplychain.orchestrator.service;

import com.supplychain.common.dto.OrderRequestDto;
import com.supplychain.common.event.*;
import com.supplychain.orchestrator.entity.SagaState;
import com.supplychain.orchestrator.repository.SagaStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private SagaStateRepository sagaStateRepository;

    @InjectMocks
    private SagaOrchestrator orchestrator;

    private final UUID orderId = UUID.randomUUID();

    // --------------------------------------------------------
    // TEST 1: Order Created -> Persist State -> Command Payment
    // --------------------------------------------------------
    @Test
    void testHandleOrderEvent_StartsSagaAndCommandsPayment() {
        // Given
        OrderEvent orderEvent = OrderEvent.builder()
                .requestId(orderId)
                .userId(Integer.valueOf(101))
                .productId(Integer.valueOf(55))
                .amount(Double.valueOf(100.0))
                .orderStatus(OrderStatus.ORDER_CREATED)
                .build();

        // When
        orchestrator.handleOrderEvent(orderEvent);

        // Then
        // 1. Verify Saga State was saved with "STARTED"
        ArgumentCaptor<SagaState> stateCaptor = ArgumentCaptor.forClass(SagaState.class);
        verify(sagaStateRepository).save(stateCaptor.capture());
        SagaState savedState = stateCaptor.getValue();
        assertEquals("STARTED", savedState.getStatus());
        assertEquals(101, savedState.getUserId());
        assertEquals(55, savedState.getProductId());

        // 2. Verify Command sent to Payment Service
        verify(kafkaTemplate).send(eq("payment-orders"), any(OrderRequestDto.class));
    }

    // --------------------------------------------------------
    // TEST 2: Payment Success -> Fetch State -> Command Inventory
    // --------------------------------------------------------
    @Test
    void testHandlePaymentUpdate_Success_CommandsInventory() {
        // Given
        PaymentEvent paymentEvent = PaymentEvent.builder()
                .requestId(orderId)
                .paymentStatus(PaymentStatus.PAYMENT_COMPLETED)
                .build();

        // Mock Database having the context
        SagaState mockState = SagaState.builder()
                .orderId(orderId)
                .userId(Integer.valueOf(101))
                .productId(Integer.valueOf(55))
                .amount(Double.valueOf(100.0))
                .status("STARTED")
                .build();

        when(sagaStateRepository.findById(orderId)).thenReturn(Optional.of(mockState));

        // When
        orchestrator.handlePaymentUpdate(paymentEvent);

        // Then
        // 1. Verify State updated to PAYMENT_COMPLETED
        verify(sagaStateRepository).save(argThat(state ->
                state.getStatus().equals("PAYMENT_COMPLETED")
        ));

        // 2. Verify Inventory Command uses Data from DB (not hardcoded)
        ArgumentCaptor<OrderRequestDto> cmdCaptor = ArgumentCaptor.forClass(OrderRequestDto.class);
        verify(kafkaTemplate).send(eq("inventory-orders"), cmdCaptor.capture());

        OrderRequestDto sentCommand = cmdCaptor.getValue();
        assertEquals(55, sentCommand.getProductId()); // Comes from mockState
        assertEquals(101, sentCommand.getUserId());   // Comes from mockState
    }

    // --------------------------------------------------------
    // TEST 3: Inventory Failure -> Compensate (Refund) & Cancel
    // --------------------------------------------------------
    @Test
    void testHandleInventoryUpdate_OutOfStock_TriggersCompensation() {
        InventoryEvent inventoryEvent = InventoryEvent.builder()
                .requestId(orderId)
                .inventoryStatus(InventoryStatus.INVENTORY_UNAVAILABLE)
                .build();

        SagaState mockState = SagaState.builder()
                .orderId(orderId)
                .userId(Integer.valueOf(101))
                .productId(Integer.valueOf(55))
                .amount(Double.valueOf(100.0))
                .status("PAYMENT_COMPLETED")
                .build();

        when(sagaStateRepository.findById(orderId)).thenReturn(Optional.of(mockState));

        // When
        orchestrator.handleInventoryUpdate(inventoryEvent);

        // Then
        // 1. Verify State updated to FAILED
        verify(sagaStateRepository).save(argThat(state ->
                state.getStatus().equals("INVENTORY_FAILED")
        ));

        // 2. Verify Compensation (Refund) Command sent
        ArgumentCaptor<OrderRequestDto> refundCaptor = ArgumentCaptor.forClass(OrderRequestDto.class);
        verify(kafkaTemplate).send(eq("payment-orders"), refundCaptor.capture());
        assertEquals(-100.0, refundCaptor.getValue().getAmount()); // Negative amount = Refund

        // 3. Verify Order Cancelled Event
        ArgumentCaptor<OrderEvent> cancelCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq("order-updates"), cancelCaptor.capture());
        assertEquals(OrderStatus.ORDER_CANCELLED, cancelCaptor.getValue().getOrderStatus());
    }
}