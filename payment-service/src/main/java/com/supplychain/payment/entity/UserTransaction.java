package com.supplychain.payment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserTransaction {
    @Id
    private UUID orderId; // Using OrderID as key for simplicity in 1-to-1 mapping
    private Double amount;
}