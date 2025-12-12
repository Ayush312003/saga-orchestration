package com.supplychain.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderEvent {
    private UUID requestId; // Acts as the Correlation ID
    private Date date;
    private OrderStatus orderStatus;
    private Integer userId;
    private Integer productId;
    private Double amount;
}