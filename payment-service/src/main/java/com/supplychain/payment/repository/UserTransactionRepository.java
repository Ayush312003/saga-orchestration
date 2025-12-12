package com.supplychain.payment.repository;
import com.supplychain.payment.entity.UserTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserTransactionRepository extends JpaRepository<UserTransaction, UUID> {}