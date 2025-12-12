package com.supplychain.orchestrator.repository;

import com.supplychain.orchestrator.entity.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SagaStateRepository extends JpaRepository<SagaState, UUID> {
}