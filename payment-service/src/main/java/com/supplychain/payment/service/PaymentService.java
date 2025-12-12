package com.supplychain.payment.service;

import com.supplychain.common.dto.OrderRequestDto;
import com.supplychain.common.event.OrderEvent;
import com.supplychain.common.event.PaymentEvent;
import com.supplychain.common.event.PaymentStatus;
import com.supplychain.common.event.TransactionType;
import com.supplychain.payment.entity.UserBalance;
import com.supplychain.payment.entity.UserTransaction;
import com.supplychain.payment.repository.UserBalanceRepository;
import com.supplychain.payment.repository.UserTransactionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    @Autowired
    private UserBalanceRepository balanceRepository;

    @Autowired
    private UserTransactionRepository transactionRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @PostConstruct
    public void initData() {
        balanceRepository.save(new UserBalance(101, 10000.0));
    }

    /**
     * Listens to 'payment-orders' topic (Command from Orchestrator)
     * We expect an OrderRequestDto or similar payload carrying userId and amount.
     * For this implementation, we map the OrderEvent fields manually.
     */
    @Transactional
    @KafkaListener(topics = "payment-orders", groupId = "payment-group")
    public void processPayment(OrderRequestDto orderRequest) {

        System.out.println("Payment Service: Recieved Order " + orderRequest.getOrderId());

        UserBalance userBalance = balanceRepository.findById(orderRequest.getUserId())
                .orElse(null);

        PaymentEvent paymentEvent = new PaymentEvent();
        paymentEvent.setRequestId(orderRequest.getOrderId());
        paymentEvent.setTransactionType(orderRequest.getTransactionType());
        paymentEvent.setPaymentId(UUID.randomUUID());

        // 1. Fail fast if User doesn't exist
        if (userBalance == null) {
            paymentEvent.setPaymentStatus(PaymentStatus.PAYMENT_FAILED);
            kafkaTemplate.send("payment-updates", paymentEvent);
            return;
        }

        double amount = orderRequest.getAmount();

        // 2. Handle Logic based on Intent
        if (TransactionType.COMPENSATION.equals(orderRequest.getTransactionType())) {
            // === REFUND LOGIC ===
            // Always add money back. Using Math.abs to handle if input was sent as negative.
            double refundAmount = Math.abs(amount);

            userBalance.setBalance(userBalance.getBalance() + refundAmount);
            balanceRepository.save(userBalance);

            transactionRepository.save(new UserTransaction(orderRequest.getOrderId(), refundAmount));

            paymentEvent.setPaymentStatus(PaymentStatus.PAYMENT_COMPLETED);
            System.out.println("Payment Service: Refund Processed. New Balance: " + userBalance.getBalance());

        } else {
            // === PURCHASE LOGIC ===
            if (userBalance.getBalance() >= amount) {
                userBalance.setBalance(userBalance.getBalance() - amount);
                balanceRepository.save(userBalance);

                transactionRepository.save(new UserTransaction(orderRequest.getOrderId(), amount));
                paymentEvent.setPaymentStatus(PaymentStatus.PAYMENT_COMPLETED);
            } else {
                paymentEvent.setPaymentStatus(PaymentStatus.PAYMENT_FAILED);
            }
        }

        // Reply to Orchestrator
        kafkaTemplate.send("payment-updates", paymentEvent);
    }
}