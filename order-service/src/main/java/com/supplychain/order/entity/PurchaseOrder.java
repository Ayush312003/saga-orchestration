package com.supplychain.order.entity;

import com.supplychain.common.event.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer userId;
    private Integer productId;
    private Double price;

    // We use the UUID as the correlation ID across the saga
    private UUID orderId; // Logical ID exposed to outside world

    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;
}