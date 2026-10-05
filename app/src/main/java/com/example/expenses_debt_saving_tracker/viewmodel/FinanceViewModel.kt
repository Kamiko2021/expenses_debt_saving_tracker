package com.example.expenses_debt_saving_tracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expenses_debt_saving_tracker.model.Bill
import com.example.expenses_debt_saving_tracker.model.Debt
import com.example.expenses_debt_saving_tracker.model.Transaction
import com.example.expenses_debt_saving_tracker.model.TransactionType
import com.example.expenses_debt_saving_tracker.repository.FinanceRepository
import com.example.expenses_debt_saving_tracker.util.NotificationHelper
import com.google.ai.client.generativeai.GenerativeModel
import android.graphics.Bitmap
import com.google.ai.client.generativeai.type.content
import org.json.JSONObject
import com.google.firebase.Timestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class FinanceViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: FinanceRepository = FinanceRepository()
) : AndroidViewModel(application) {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions

    private val _debts = MutableStateFlow<List<Debt>>(emptyList())
    val debts: StateFlow<List<Debt>> = _debts

    private val _bills = MutableStateFlow<List<Bill>>(emptyList())
    val bills: StateFlow<List<Bill>> = _bills

    private val _creditAccounts = MutableStateFlow<List<com.example.expenses_debt_saving_tracker.model.CreditAccount>>(emptyList())
    val creditAccounts: StateFlow<List<com.example.expenses_debt_saving_tracker.model.CreditAccount>> = _creditAccounts

    private val _savingsAccounts = MutableStateFlow<List<com.example.expenses_debt_saving_tracker.model.SavingsAccount>>(emptyList())
    val savingsAccounts: StateFlow<List<com.example.expenses_debt_saving_tracker.model.SavingsAccount>> = _savingsAccounts

    private val _financialPlan = MutableStateFlow<String>("")
    val financialPlan: StateFlow<String> = _financialPlan

    private val _aiInsight = MutableStateFlow<String>("Add your first transaction to get AI insights!")
    val aiInsight: StateFlow<String> = _aiInsight

    private val _monthlyGoals = MutableStateFlow<List<com.example.expenses_debt_saving_tracker.model.MonthlyGoal>>(emptyList())
    val monthlyGoals: StateFlow<List<com.example.expenses_debt_saving_tracker.model.MonthlyGoal>> = _monthlyGoals

    private val _savingsChallenge = MutableStateFlow<String>("Add transactions to unlock AI savings challenges!")
    val savingsChallenge: StateFlow<String> = _savingsChallenge

    private var aiJob: Job? = null

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = "AQ.Ab8RN6KsgKKN5270UTNbaDl1noay-ULEGABXracTa46veIAcRA" // You'll need to get this from Google AI Studio
    )

    fun loadData(userId: String) {
        viewModelScope.launch {
            // Start real-time observation
            launch {
                repository.getTransactionsFlow(userId).collectLatest {
                    _transactions.value = it.sortedByDescending { t -> t.timestamp }
                    generateFinancialPlan()
                }
            }
            
            launch {
                repository.getDebtsFlow(userId).collectLatest {
                    _debts.value = it
                    generateFinancialPlan()
                    checkDueDates(it)
                }
            }

            launch {
                repository.getBillsFlow(userId).collectLatest {
                    _bills.value = it
                    checkBillDueDates(it)
                }
            }

            launch {
                repository.getCreditAccountsFlow(userId).collectLatest {
                    _creditAccounts.value = it
                    generateFinancialPlan()
                }
            }

            launch {
                repository.getSavingsAccountsFlow(userId).collectLatest {
                    _savingsAccounts.value = it
                }
            }
        }
    }

    private fun checkBillDueDates(bills: List<Bill>) {
        val now = Calendar.getInstance().timeInMillis
        val oneDayInMs = 24 * 60 * 60 * 1000
        
        bills.forEach { bill ->
            val dueDateMs = bill.dueDate.toDate().time
            if (dueDateMs in now..(now + oneDayInMs) && !bill.isPaid) {
                NotificationHelper.showNotification(
                    getApplication(),
                    "Upcoming Bill: ${bill.name}",
                    "Your ${bill.category} bill of ₱${bill.amount} is due soon!"
                )
            }
        }
    }

    private fun checkDueDates(debts: List<Debt>) {
        val now = Calendar.getInstance().timeInMillis
        val oneDayInMs = 24 * 60 * 60 * 1000
        
        debts.forEach { debt ->
            val dueDateMs = debt.dueDate.toDate().time
            if (dueDateMs in now..(now + oneDayInMs) && !debt.isPaid) {
                NotificationHelper.showNotification(
                    getApplication(),
                    "Upcoming Payment: ${debt.name}",
                    "Your installment of ${debt.installmentAmount} is due soon!"
                )
            }
        }
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.addTransaction(transaction)
            loadData(transaction.userId)
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
            loadData(transaction.userId)
        }
    }

    fun deleteTransaction(transactionId: String, userId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(transactionId)
            loadData(userId)
        }
    }

    fun addDebt(debt: Debt) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.time = debt.startDate.toDate()
            
            val installments = (1..debt.totalMonths).map { month ->
                val dueDate = Timestamp(calendar.time)
                calendar.add(Calendar.MONTH, 1)
                com.example.expenses_debt_saving_tracker.model.Installment(
                    month = month,
                    isPaid = false,
                    dueDate = dueDate
                )
            }
            
            repository.addDebt(debt.copy(installments = installments))
            loadData(debt.userId)
        }
    }

    fun deleteDebt(debtId: String, userId: String) {
        viewModelScope.launch {
            repository.deleteDebt(debtId)
            loadData(userId)
        }
    }

    fun addCreditAccount(account: com.example.expenses_debt_saving_tracker.model.CreditAccount) {
        viewModelScope.launch {
            repository.addCreditAccount(account)
            loadData(account.userId)
        }
    }

    fun deleteCreditAccount(accountId: String, userId: String) {
        viewModelScope.launch {
            repository.deleteCreditAccount(accountId)
            loadData(userId)
        }
    }

    fun addSavingsAccount(account: com.example.expenses_debt_saving_tracker.model.SavingsAccount) {
        viewModelScope.launch {
            repository.addSavingsAccount(account)
            loadData(account.userId)
        }
    }

    fun updateSavingsAccount(account: com.example.expenses_debt_saving_tracker.model.SavingsAccount) {
        viewModelScope.launch {
            repository.updateSavingsAccount(account)
            loadData(account.userId)
        }
    }

    fun deleteSavingsAccount(accountId: String, userId: String) {
        viewModelScope.launch {
            repository.deleteSavingsAccount(accountId)
            loadData(userId)
        }
    }

    fun depositToSavingsAccount(account: com.example.expenses_debt_saving_tracker.model.SavingsAccount, amount: Double, source: String) {
        viewModelScope.launch {
            val updated = account.copy(currentBalance = account.currentBalance + amount)
            repository.updateSavingsAccount(updated)
            if (amount > 0) {
                addTransaction(
                    Transaction(
                        userId = account.userId,
                        amount = amount,
                        description = "Deposit to ${account.name}",
                        category = "Savings",
                        source = source.ifEmpty { "Cash on Hand" },
                        type = TransactionType.SAVINGS
                    )
                )
            }
        }
    }

    fun withdrawFromSavingsAccount(account: com.example.expenses_debt_saving_tracker.model.SavingsAccount, amount: Double, destination: String) {
        viewModelScope.launch {
            val updated = account.copy(currentBalance = (account.currentBalance - amount).coerceAtLeast(0.0))
            repository.updateSavingsAccount(updated)
            if (amount > 0) {
                addTransaction(
                    Transaction(
                        userId = account.userId,
                        amount = amount,
                        description = "Withdrawal from ${account.name}",
                        category = "Savings Withdrawal",
                        source = account.name,
                        destinationSource = destination.ifEmpty { "Cash on Hand" },
                        type = TransactionType.INCOME
                    )
                )
            }
        }
    }

    fun addBill(bill: Bill) {
        viewModelScope.launch {
            repository.addBill(bill)
            loadData(bill.userId)
        }
    }

    fun updateBill(bill: Bill) {
        viewModelScope.launch {
            repository.updateBill(bill)
            loadData(bill.userId)
        }
    }

    fun deleteBill(billId: String, userId: String) {
        viewModelScope.launch {
            repository.deleteBill(billId)
            loadData(userId)
        }
    }

    fun toggleBillPaid(bill: Bill) {
        viewModelScope.launch {
            repository.updateBill(bill.copy(isPaid = !bill.isPaid))
        }
    }

    fun toggleInstallmentPaid(debt: Debt, installmentMonth: Int) {
        viewModelScope.launch {
            val targetInst = debt.installments.find { it.month == installmentMonth }
            val wasPaid = targetInst?.isPaid ?: false
            val isNowPaid = !wasPaid

            val updatedInstallments = debt.installments.map { 
                if (it.month == installmentMonth) it.copy(isPaid = isNowPaid) else it
            }
            
            val paidCount = updatedInstallments.count { it.isPaid }
            val remainingAmount = debt.totalAmount - (paidCount * debt.installmentAmount)
            val isDebtPaid = paidCount == debt.totalMonths || remainingAmount <= 0
            
            repository.updateDebt(debt.copy(
                installments = updatedInstallments,
                remainingAmount = if (remainingAmount < 0) 0.0 else remainingAmount,
                isPaid = isDebtPaid
            ))

            if (isNowPaid) {
                // Automatically record a Debt Payment transaction for 7-day trend tracking
                val paymentTx = Transaction(
                    userId = debt.userId,
                    amount = debt.installmentAmount,
                    description = "${debt.name} (Installment #$installmentMonth)",
                    category = "Debt Payment",
                    source = "Cash",
                    type = TransactionType.DEBT_PAYMENT,
                    timestamp = Timestamp.now()
                )
                repository.addTransaction(paymentTx)
            }
        }
    }

    fun clearData(userId: String) {
        viewModelScope.launch {
            repository.clearUserData(userId)
            // The flows will automatically update the UI to empty
        }
    }

    fun extractDebtFromImage(bitmap: Bitmap, onResult: (Debt?) -> Unit) {
        viewModelScope.launch {
            try {
                val prompt = content {
                    image(bitmap)
                    text("Extract debt information from this screenshot. Return ONLY a JSON object with keys: name, totalAmount, interestRate, installmentAmount, totalMonths. Use 0.0 or 12 as default if not found. Name should be a string, others should be numbers.")
                }
                
                val response = generativeModel.generateContent(prompt)
                val jsonText = response.text?.replace("```json", "")?.replace("```", "")?.trim()
                
                if (jsonText != null) {
                    val json = JSONObject(jsonText)
                    val debt = Debt(
                        name = json.optString("name", "Extracted Debt"),
                        totalAmount = json.optDouble("totalAmount", 0.0),
                        remainingAmount = json.optDouble("totalAmount", 0.0),
                        interestRate = json.optDouble("interestRate", 0.0),
                        installmentAmount = json.optDouble("installmentAmount", 0.0),
                        totalMonths = json.optInt("totalMonths", 12)
                    )
                    onResult(debt)
                } else {
                    onResult(null)
                }
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    private fun generateFinancialPlan() {
        if (_transactions.value.isEmpty() && _debts.value.isEmpty() && _bills.value.isEmpty()) {
            _financialPlan.value = "Welcome! Add your earnings, debts and bills to see your personalized roadmap."
            _aiInsight.value = "I'm ready to help you achieve financial freedom. Start by adding your first transaction!"
            return
        }

        val startOfMonthCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonthMs = startOfMonthCal.timeInMillis
        val endOfMonthMs = (startOfMonthCal.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
        }.timeInMillis

        // Real Income (excluding internal deposits, transfers, withdrawals, and loan proceeds)
        val totalIncome = _transactions.value.filter {
            it.type in listOf(TransactionType.INCOME, TransactionType.SALARY) ||
            (it.type == TransactionType.ADJUSTMENT && it.amount > 0)
        }.sumOf { it.amount }

        // Real Expenses (excluding internal transfers, withdrawals, and deposits)
        val totalExpenses = _transactions.value.filter {
            it.type in listOf(TransactionType.EXPENSE, TransactionType.DEBT_PAYMENT, TransactionType.BILL_PAYMENT, TransactionType.SUBSCRIPTION, TransactionType.WIRED_TRANSFER)
        }.sumOf { it.amount }

        // Money Borrowed This Month (Debts added + Loan Proceeds transactions)
        val borrowedFromDebts = _debts.value.filter { debt ->
            val t = debt.startDate.toDate().time
            t >= startOfMonthMs && t < endOfMonthMs
        }.sumOf { if (it.principalAmount > 0) it.principalAmount else it.totalAmount }

        val borrowedFromTxs = _transactions.value.filter { tx ->
            val t = tx.timestamp.toDate().time
            t >= startOfMonthMs && t < endOfMonthMs && (
                tx.type == TransactionType.LOAN_PROCEEDS || (
                    tx.type == TransactionType.INCOME && (
                        tx.category.contains("loan", ignoreCase = true) ||
                        tx.category.contains("borrow", ignoreCase = true) ||
                        tx.description.contains("loan", ignoreCase = true) ||
                        tx.description.contains("borrow", ignoreCase = true)
                    )
                )
            )
        }.sumOf { it.amount }

        val moneyBorrowedThisMonth = maxOf(borrowedFromDebts, borrowedFromTxs)

        val totalSavings = _transactions.value.filter { it.type == TransactionType.SAVINGS }.sumOf { it.amount }
        val balance = totalIncome - totalExpenses - totalSavings
        val totalDebt = _debts.value.sumOf { it.remainingAmount }
        val totalMonthlyBills = _bills.value.filter { it.frequency == "Monthly" }.sumOf { it.amount }

        val plan = StringBuilder()
        plan.append("Summary:\n")
        plan.append("Total Real Income: $totalIncome\n")
        plan.append("Total Real Expenses: $totalExpenses\n")
        plan.append("Money Borrowed This Month: $moneyBorrowedThisMonth\n")
        plan.append("Total Savings: $totalSavings\n")
        plan.append("Current Net Balance: $balance\n")
        plan.append("Total Remaining Debt: $totalDebt\n")
        plan.append("Monthly Bills/Subs: $totalMonthlyBills\n\n")

        if (totalDebt > 0) {
            plan.append("Roadmap to Financial Freedom:\n")
            val sortedDebts = _debts.value.sortedByDescending { it.interestRate }
            plan.append("1. Prioritize paying off debts with the highest interest rates:\n")
            sortedDebts.forEach { debt ->
                plan.append("   - ${debt.name}: ${debt.remainingAmount} (Interest: ${debt.interestRate}%)\n")
            }
            
            if (balance > 0) {
                plan.append("\nRecommendation: Allocate at least ${(balance * 0.2).toInt()} towards your highest interest debt.")
            } else {
                plan.append("\nWarning: Your expenses exceed or equal your income. Look for areas to reduce spending to start paying off debt.")
            }
        } else {
            plan.append("Congratulations! You are debt-free. Focus on building your savings and investments.")
        }

        _financialPlan.value = plan.toString()
        getAIRecommendation(plan.toString())
    }

    private fun getAIRecommendation(financialData: String) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            delay(2000) // Wait for 2 seconds of inactivity before calling AI
            try {
                val prompt = """
                    You are a professional personalized financial manager and life coach.
                    Analyze the following user data and provide high-value, specific advice.
                    
                    Data Breakdown:
                    ${financialData}
                    
                    Tasks:
                    1. Roadmap: Provide a short, encouraging, and highly ACTIONABLE 3-sentence roadmap. 
                       - Use specific numbers from the data. 
                       - Prioritize high-interest debt or building an emergency fund based on the balance.
                    2. Goals: Suggest monthly budget limits for the top 3 spending categories.
                       - Categories should match the user's data if possible.
                       - Amount should be realistic (e.g., 10-20% reduction from current).
                    3. Challenge: A creative "Savings Challenge" for this week.
                    
                    Rules: 
                    - Return ONLY a JSON object.
                    - Do not use markdown formatting in the roadmap or challenge.
                    
                    JSON Structure:
                    {
                      "roadmap": "Advice string",
                      "goals": [{"category": "Name", "amount": 0.0}],
                      "challenge": "Challenge string"
                    }
                """.trimIndent()
                
                val response = generativeModel.generateContent(prompt)
                val responseText = response.text?.replace("```json", "")?.replace("```", "")?.trim()
                
                if (responseText != null) {
                    val json = JSONObject(responseText)
                    _aiInsight.value = json.optString("roadmap", "Start by tracking every expense.")
                    _savingsChallenge.value = json.optString("challenge", "Try to save ₱500 this week!")
                    
                    val goalsArray = json.optJSONArray("goals")
                    if (goalsArray != null) {
                        val newGoals = mutableListOf<com.example.expenses_debt_saving_tracker.model.MonthlyGoal>()
                        for (i in 0 until goalsArray.length()) {
                            val goalObj = goalsArray.getJSONObject(i)
                            val category = goalObj.getString("category")
                            val amount = goalObj.getDouble("amount")
                            
                            val currentSpending = _transactions.value
                                .filter { it.type == TransactionType.EXPENSE && it.category.equals(category, ignoreCase = true) }
                                .sumOf { it.amount }
                                
                            newGoals.add(com.example.expenses_debt_saving_tracker.model.MonthlyGoal(category, amount, currentSpending))
                        }
                        _monthlyGoals.value = newGoals
                    }
                }
            } catch (e: Exception) {
                _aiInsight.value = "Your path to financial freedom is unique. Keep tracking to see your progress!"
                // Safety Fallback: Set a manual goal for the top category if AI fails
                if (_transactions.value.any { it.type == TransactionType.EXPENSE }) {
                    val topCategory = _transactions.value.filter { it.type == TransactionType.EXPENSE }
                        .groupBy { it.category }.maxByOrNull { it.value.sumOf { t -> t.amount } }
                    topCategory?.let {
                        val spending = it.value.sumOf { t -> t.amount }
                        _monthlyGoals.value = listOf(com.example.expenses_debt_saving_tracker.model.MonthlyGoal(it.key, spending * 0.9, spending))
                    }
                }
            }
        }
    }

    fun suggestCategory(description: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val prompt = "Suggest a 1-word financial category for: '$description'. (e.g., Food, Rent, Salary, Bills, Entertainment). Return ONLY the word."
                val response = generativeModel.generateContent(prompt)
                onResult(response.text?.trim()?.removeSuffix(".") ?: "Other")
            } catch (e: Exception) {
                onResult("Other")
            }
        }
    }
}
