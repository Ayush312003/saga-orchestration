package com.supplychain.orchestrator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "saga_state")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SagaState {

    @Id
    private UUID orderId; // The Correlation ID

    private Integer userId;
    private Integer productId;
    private Double amount;

    private String status; // E.g., STARTED, PAYMENT_DONE, COMPLETED, CANCELLED
}