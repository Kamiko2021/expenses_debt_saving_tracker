package com.example.expenses_debt_saving_tracker.model

data class SavingsAccount(
    val id: String = "",
    val userId: String = "",
    val name: String = "", // e.g., Emergency Savings Account, Daily Saving
    val targetAmount: Double = 0.0, // e.g., 30000.0, 20000.0
    val currentBalance: Double = 0.0,
    val category: String = "General"
)
