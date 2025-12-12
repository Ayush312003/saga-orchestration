package com.supplychain.order.repository;

import com.supplychain.order.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Integer> {
     Optional<PurchaseOrder> findByOrderId(UUID orderId);
}