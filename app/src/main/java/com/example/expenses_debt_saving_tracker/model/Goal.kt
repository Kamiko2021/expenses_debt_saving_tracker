package com.example.expenses_debt_saving_tracker.model

data class MonthlyGoal(
    val category: String = "",
    val amount: Double = 0.0,
    val currentSpending: Double = 0.0
)
