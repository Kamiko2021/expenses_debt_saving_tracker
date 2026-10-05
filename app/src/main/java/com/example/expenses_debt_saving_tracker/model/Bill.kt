package com.example.expenses_debt_saving_tracker.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Bill(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val dueDate: Timestamp = Timestamp.now(),
    val category: String = "Utilities", // Utilities, Subscription, Rent, etc.
    val frequency: String = "Monthly", // Monthly, Weekly, Yearly
    @get:PropertyName("paid")
    @set:PropertyName("paid")
    var isPaid: Boolean = false,
    val autoPay: Boolean = false
)
