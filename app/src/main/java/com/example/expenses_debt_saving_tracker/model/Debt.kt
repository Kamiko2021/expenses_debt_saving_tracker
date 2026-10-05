package com.example.expenses_debt_saving_tracker.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Installment(
    val month: Int = 1,
    @get:PropertyName("paid")
    @set:PropertyName("paid")
    var isPaid: Boolean = false,
    val dueDate: Timestamp = Timestamp.now()
)

data class Debt(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val totalAmount: Double = 0.0, // (Installment * Months)
    val principalAmount: Double = 0.0, // Original borrowed amount
    val remainingAmount: Double = 0.0,
    val installmentAmount: Double = 0.0,
    val dueDate: Timestamp = Timestamp.now(),
    val startDate: Timestamp = Timestamp.now(),
    val totalMonths: Int = 12,
    val interestRate: Double = 0.0,
    val frequency: String = "monthly", // monthly, weekly, etc.
    val installments: List<Installment> = emptyList(),
    @get:PropertyName("paid")
    @set:PropertyName("paid")
    var isPaid: Boolean = false
)
