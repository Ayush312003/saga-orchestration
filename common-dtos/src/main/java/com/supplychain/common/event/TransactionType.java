package com.supplychain.common.event;

public enum TransactionType {
    PURCHASE,   // Normal Debit
    COMPENSATION // Refund/Rollback
}