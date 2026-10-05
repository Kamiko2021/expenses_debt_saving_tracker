package com.example.expenses_debt_saving_tracker.model

import com.google.firebase.Timestamp

enum class TransactionType {
    INCOME, EXPENSE, SALARY, SAVINGS, ADJUSTMENT, WITHDRAWAL, DEPOSIT, TRANSFER, DEBT_PAYMENT, BILL_PAYMENT, SUBSCRIPTION, WIRED_TRANSFER, CASH_ON_HAND, LOAN_PROCEEDS
}

data class Transaction(
    val id: String = "",
    val userId: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val description: String = "",
    val source: String = "", // e.g., GCash, Landbank, Workplace
    val destinationSource: String = "", // Used for Transfers
    val timestamp: Timestamp = Timestamp.now(),
    val type: TransactionType = TransactionType.EXPENSE
)
