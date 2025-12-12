package com.supplychain.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentEvent {
    private UUID requestId;
    private UUID paymentId;
    private PaymentStatus paymentStatus;
    private TransactionType transactionType;
}