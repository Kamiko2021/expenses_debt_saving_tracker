package com.example.expenses_debt_saving_tracker.repository

import com.example.expenses_debt_saving_tracker.model.Bill
import com.example.expenses_debt_saving_tracker.model.CreditAccount
import com.example.expenses_debt_saving_tracker.model.Debt
import com.example.expenses_debt_saving_tracker.model.SavingsAccount
import com.example.expenses_debt_saving_tracker.model.Transaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FinanceRepository {
    private val firestore = FirebaseFirestore.getInstance().apply {
        // Ensure local persistence is enabled for a better offline experience
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestoreSettings = settings
    }
    private val transactionsCollection = firestore.collection("transactions")
    private val debtsCollection = firestore.collection("debts")
    private val billsCollection = firestore.collection("bills")
    private val creditAccountsCollection = firestore.collection("credit_accounts")
    private val savingsAccountsCollection = firestore.collection("savings_accounts")

    suspend fun addTransaction(transaction: Transaction) {
        val ref = transactionsCollection.document()
        transactionsCollection.document(ref.id).set(transaction.copy(id = ref.id)).await()
    }

    suspend fun updateTransaction(transaction: Transaction) {
        if (transaction.id.isNotEmpty()) {
            transactionsCollection.document(transaction.id).set(transaction).await()
        }
    }

    fun getTransactionsFlow(userId: String): Flow<List<Transaction>> {
        return transactionsCollection.whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot -> 
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Transaction::class.java)?.copy(id = doc.id)
                }
            }
    }

    suspend fun addDebt(debt: Debt) {
        val ref = debtsCollection.document()
        debtsCollection.document(ref.id).set(debt.copy(id = ref.id)).await()
    }

    suspend fun addBill(bill: Bill) {
        val ref = billsCollection.document()
        billsCollection.document(ref.id).set(bill.copy(id = ref.id)).await()
    }

    suspend fun updateDebt(debt: Debt) {
        if (debt.id.isNotEmpty()) {
            debtsCollection.document(debt.id).set(debt).await()
        }
    }

    suspend fun updateBill(bill: Bill) {
        if (bill.id.isNotEmpty()) {
            billsCollection.document(bill.id).set(bill).await()
        }
    }

    suspend fun deleteTransaction(transactionId: String) {
        if (transactionId.isNotEmpty()) {
            transactionsCollection.document(transactionId).delete().await()
        }
    }

    suspend fun deleteDebt(debtId: String) {
        if (debtId.isNotEmpty()) {
            debtsCollection.document(debtId).delete().await()
        }
    }

    suspend fun deleteBill(billId: String) {
        if (billId.isNotEmpty()) {
            billsCollection.document(billId).delete().await()
        }
    }

    fun getDebtsFlow(userId: String): Flow<List<Debt>> {
        return debtsCollection.whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Debt::class.java)?.copy(id = doc.id)
                }
            }
    }

    fun getBillsFlow(userId: String): Flow<List<Bill>> {
        return billsCollection.whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Bill::class.java)?.copy(id = doc.id)
                }
            }
    }

    suspend fun addCreditAccount(account: CreditAccount) {
        val ref = creditAccountsCollection.document()
        creditAccountsCollection.document(ref.id).set(account.copy(id = ref.id)).await()
    }

    suspend fun updateCreditAccount(account: CreditAccount) {
        if (account.id.isNotEmpty()) {
            creditAccountsCollection.document(account.id).set(account).await()
        }
    }

    suspend fun deleteCreditAccount(accountId: String) {
        if (accountId.isNotEmpty()) {
            creditAccountsCollection.document(accountId).delete().await()
        }
    }

    fun getCreditAccountsFlow(userId: String): Flow<List<CreditAccount>> {
        return creditAccountsCollection.whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(CreditAccount::class.java)?.copy(id = doc.id)
                }
            }
    }

    suspend fun addSavingsAccount(account: SavingsAccount) {
        val ref = savingsAccountsCollection.document()
        savingsAccountsCollection.document(ref.id).set(account.copy(id = ref.id)).await()
    }

    suspend fun updateSavingsAccount(account: SavingsAccount) {
        if (account.id.isNotEmpty()) {
            savingsAccountsCollection.document(account.id).set(account).await()
        }
    }

    suspend fun deleteSavingsAccount(accountId: String) {
        if (accountId.isNotEmpty()) {
            savingsAccountsCollection.document(accountId).delete().await()
        }
    }

    fun getSavingsAccountsFlow(userId: String): Flow<List<SavingsAccount>> {
        return savingsAccountsCollection.whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(SavingsAccount::class.java)?.copy(id = doc.id)
                }
            }
    }

    // Keep one-time fetch for seeding check
    suspend fun getTransactions(userId: String): List<Transaction> {
        return transactionsCollection.whereEqualTo("userId", userId)
            .get().await().documents.mapNotNull { doc ->
                doc.toObject(Transaction::class.java)?.copy(id = doc.id)
            }
    }

    suspend fun getDebts(userId: String): List<Debt> {
        return debtsCollection.whereEqualTo("userId", userId)
            .get().await().documents.mapNotNull { doc ->
                doc.toObject(Debt::class.java)?.copy(id = doc.id)
            }
    }

    suspend fun getBills(userId: String): List<Bill> {
        return billsCollection.whereEqualTo("userId", userId)
            .get().await().documents.mapNotNull { doc ->
                doc.toObject(Bill::class.java)?.copy(id = doc.id)
            }
    }

    suspend fun clearUserData(userId: String) {
        val txs = transactionsCollection.whereEqualTo("userId", userId).get().await()
        txs.documents.forEach { it.reference.delete().await() }
        
        val dbt = debtsCollection.whereEqualTo("userId", userId).get().await()
        dbt.documents.forEach { it.reference.delete().await() }

        val bls = billsCollection.whereEqualTo("userId", userId).get().await()
        bls.documents.forEach { it.reference.delete().await() }

        val crd = creditAccountsCollection.whereEqualTo("userId", userId).get().await()
        crd.documents.forEach { it.reference.delete().await() }

        val svg = savingsAccountsCollection.whereEqualTo("userId", userId).get().await()
        svg.documents.forEach { it.reference.delete().await() }
    }
}
