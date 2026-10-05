package com.example.expenses_debt_saving_tracker.model

data class CreditAccount(
    val id: String = "",
    val userId: String = "",
    val name: String = "", // e.g. Salmon Credit, GCredit, BDO Credit Card
    val creditLimit: Double = 0.0 // e.g. 100.0 or 150.0
)
