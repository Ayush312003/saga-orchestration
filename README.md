# Distributed Supply Chain System (Saga Orchestration)

This project is an implementation of the Saga Orchestration Pattern using Java and Spring Boot. It demonstrates how to manage distributed transactions across microservices while ensuring data consistency in an eventual consistency model.

The system is designed to handle a supply chain workflow involving Order creation, Payment processing, and Inventory management. It features a custom Stateful Orchestrator that manages transaction lifecycles and triggers compensating transactions (rollbacks) automatically upon failure.

## Architecture Overview

The system utilizes an event-driven architecture where microservices are decoupled and communicate asynchronously via Apache Kafka. A central Orchestrator Service acts as the coordinator, listening to events and issuing commands.

### Transaction Flow
1.  **Order Service:** Receives a request and publishes an `ORDER_CREATED` event.
2.  **Orchestrator:** Persists the transaction state and commands the Payment Service.
3.  **Payment Service:** Processes the debit. If successful, publishes `PAYMENT_COMPLETED`.
4.  **Orchestrator:** Commands the Inventory Service to reserve stock.
5.  **Inventory Service:** Updates stock. If successful, publishes `INVENTORY_ALLOCATED`.
6.  **Orchestrator:** Finalizes the order as `COMPLETED`.

### Failure & Compensation (Rollback)
If the Inventory Service fails (e.g., out of stock) after payment has been deducted:
1.  Inventory publishes `INVENTORY_FAILED`.
2.  Orchestrator identifies the failure and issues a `COMPENSATION` command (Refund) to the Payment Service.
3.  Payment Service processes the refund and confirms.
4.  Orchestrator marks the transaction as rolled back and the Order status is updated to `CANCELLED`.

### System Diagram

```mermaid
graph TD
    User([Client]) -->|POST /order/create| OrderService
    
    subgraph Infrastructure
        Kafka{Apache Kafka}
        MySQL[(MySQL Databases)]
    end

    subgraph Microservices
        OrderService
        PaymentService
        InventoryService
        Orchestrator
    end

    OrderService -->|1. Order Created| Kafka
    Kafka -->|OrderEvent| Orchestrator
    
    Orchestrator -->|2. Command: Debit| Kafka
    Kafka -->|PaymentCommand| PaymentService
    PaymentService -->|3. Payment Success/Fail| Kafka
    
    Kafka -->|PaymentEvent| Orchestrator
    Orchestrator -->|4. Command: Reserve Stock| Kafka
    Kafka -->|InventoryCommand| InventoryService
    
    InventoryService -->|5. Stock Allocated/Fail| Kafka
    Kafka -->|InventoryEvent| Orchestrator
    
    Orchestrator -->|6. Completion or Refund| Kafka
    Kafka -->|Order Update| OrderService
