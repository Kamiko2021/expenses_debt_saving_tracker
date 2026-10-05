package com.example.expenses_debt_saving_tracker.ui.screens

import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expenses_debt_saving_tracker.R
import com.example.expenses_debt_saving_tracker.model.Debt
import com.example.expenses_debt_saving_tracker.model.Transaction
import com.example.expenses_debt_saving_tracker.model.TransactionType
import com.example.expenses_debt_saving_tracker.viewmodel.FinanceViewModel
import com.example.expenses_debt_saving_tracker.viewmodel.AuthViewModel
import com.example.expenses_debt_saving_tracker.ui.components.SettingsSheet
import com.example.expenses_debt_saving_tracker.util.PdfReportGenerator
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

data class DeleteTarget(val id: String, val type: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userId: String,
    viewModel: FinanceViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
    onLogout: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val creditAccounts by viewModel.creditAccounts.collectAsState()
    val financialPlan by viewModel.financialPlan.collectAsState()
    val aiInsight by viewModel.aiInsight.collectAsState()
    val monthlyGoals by viewModel.monthlyGoals.collectAsState()
    val savingsChallenge by viewModel.savingsChallenge.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAddCreditDialog by remember { mutableStateOf(false) }
    var sheetType by remember { mutableStateOf("transaction") }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val settingsSheetState = rememberModalBottomSheetState()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<DeleteTarget?>(null) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Overview/Expenses, 1 = Savings Tracker
    var transactionPage by remember { mutableIntStateOf(0) }
    val pageSize = 10
    var isTransactionsExpanded by remember { mutableStateOf(true) }
    var dateFilter by remember { mutableStateOf("All") } // "All", "Today", "2 Days", "3 Days", "7 Days", "30 Days"

    val filteredTransactions = remember(transactions, dateFilter) {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val twoDaysAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -2)
        }.timeInMillis

        val threeDaysAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -3)
        }.timeInMillis

        val sevenDaysAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
        }.timeInMillis

        val thirtyDaysAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30)
        }.timeInMillis

        transactions.filter { tx ->
            val txTime = tx.timestamp.toDate().time
            when (dateFilter) {
                "Today" -> txTime >= todayStart
                "2 Days" -> txTime >= twoDaysAgo
                "3 Days" -> txTime >= threeDaysAgo
                "7 Days" -> txTime >= sevenDaysAgo
                "30 Days" -> txTime >= thirtyDaysAgo
                else -> true
            }
        }
    }

    LaunchedEffect(userId) {
        viewModel.loadData(userId)
    }

    val balancesBySource = mutableMapOf<String, Double>().apply {
        creditAccounts.forEach { account ->
            val name = account.name.ifEmpty { "Credit Account" }
            if (account.creditLimit > 0) {
                put(name, account.creditLimit)
            }
        }

        transactions.forEach { tx ->
            val src = tx.source.ifEmpty { "Cash on Hand" }
            val currentSrcVal = getOrDefault(src, 0.0)
            
            when(tx.type) {
                TransactionType.INCOME, TransactionType.SALARY, TransactionType.ADJUSTMENT, TransactionType.LOAN_PROCEEDS, TransactionType.CASH_ON_HAND -> 
                    put(src, currentSrcVal + tx.amount)
                TransactionType.EXPENSE, TransactionType.SAVINGS, 
                TransactionType.DEBT_PAYMENT, TransactionType.BILL_PAYMENT, TransactionType.SUBSCRIPTION, TransactionType.WIRED_TRANSFER ->
                    put(src, currentSrcVal - tx.amount)
                TransactionType.TRANSFER -> {
                    put(src, currentSrcVal - tx.amount)
                    val dest = tx.destinationSource.ifEmpty { "Cash on Hand" }
                    val currentDestVal = getOrDefault(dest, 0.0)
                    put(dest, currentDestVal + tx.amount)
                }
                TransactionType.WITHDRAWAL -> {
                    put(src, currentSrcVal - tx.amount)
                    val dest = tx.destinationSource.ifEmpty { "Cash on Hand" }
                    val currentDestVal = getOrDefault(dest, 0.0)
                    put(dest, currentDestVal + tx.amount)
                }
                TransactionType.DEPOSIT -> {
                    // Auto-deduct from Cash on Hand, Add to Destination/Bank
                    val dest = tx.destinationSource.ifEmpty { src }
                    val cashSrc = if (tx.destinationSource.isNotEmpty()) tx.source.ifEmpty { "Cash on Hand" } else "Cash on Hand"
                    put(dest, getOrDefault(dest, 0.0) + tx.amount)
                    put(cashSrc, getOrDefault(cashSrc, 0.0) - tx.amount)
                }
            }
        }
    }

    val totalBalance = balancesBySource.values.sum()

    val todayCalendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStartTime = todayCalendar.timeInMillis

    val spentToday = transactions.filter { 
        it.type == TransactionType.EXPENSE && it.timestamp.toDate().time >= todayStartTime
    }.sumOf { it.amount }

    val context = LocalContext.current

    if (showDeleteConfirm && itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirm = false 
                itemToDelete = null
            },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete this ${itemToDelete?.type}? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete?.let { target ->
                            if (target.type == "transaction") {
                                viewModel.deleteTransaction(target.id, userId)
                            } else {
                                viewModel.deleteDebt(target.id, userId)
                            }
                        }
                        itemToDelete = null
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteConfirm = false 
                    itemToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddCreditDialog) {
        AddCreditAccountDialog(
            userId = userId,
            onDismiss = { showAddCreditDialog = false },
            onAdd = { viewModel.addCreditAccount(it) }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.icon_picture),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "My Finance Manager",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        if (transactions.isNotEmpty()) {
                            PdfReportGenerator.generateAndShareReport(context, transactions, debts)
                        }
                    }) {
                        Icon(
                            Icons.Default.PictureAsPdf, 
                            contentDescription = "Export PDF",
                            tint = if (transactions.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(onClick = { 
                        if (transactions.isNotEmpty()) {
                            val csv = generateCsv(transactions, debts)
                            shareCsv(context, csv)
                        }
                    }) {
                        Icon(
                            Icons.Default.Share, 
                            contentDescription = "Export CSV", 
                            tint = if (transactions.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            Icons.Default.Settings, 
                            contentDescription = "Settings", 
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = { showAddCreditDialog = true },
                    icon = { Icon(Icons.Default.AddCard, "Credit Line") },
                    text = { Text("Credit Line") },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ExtendedFloatingActionButton(
                    onClick = { 
                        sheetType = "debt"
                        showAddSheet = true 
                    },
                    icon = { Icon(Icons.Default.CreditCard, "Debt") },
                    text = { Text("Debt") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FloatingActionButton(
                    onClick = { 
                        sheetType = "transaction"
                        showAddSheet = true 
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Transaction", tint = Color.White)
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Overview") },
                    label = { Text("Overview", fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Savings, contentDescription = "Savings Tracker") },
                    label = { Text("Savings Tracker", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { padding ->
        if (showAddSheet) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showAddSheet = false 
                    transactionToEdit = null
                },
                sheetState = sheetState,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                if (sheetType == "transaction") {
                    AddTransactionForm(
                        userId = userId,
                        onDismiss = { 
                            showAddSheet = false 
                            transactionToEdit = null
                        },
                        onAdd = { 
                            if (it.id.isEmpty()) viewModel.addTransaction(it)
                            else viewModel.updateTransaction(it)
                        },
                        initialTransaction = transactionToEdit
                    )
                } else {
                    AddDebtForm(
                        userId = userId,
                        onDismiss = { showAddSheet = false },
                        onAdd = { viewModel.addDebt(it) },
                        viewModel = viewModel
                    )
                }
            }
        }

        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                sheetState = settingsSheetState
            ) {
                SettingsSheet(
                    onDismiss = { showSettingsSheet = false },
                    onLogout = onLogout,
                    authViewModel = authViewModel,
                    onClearData = { viewModel.clearData(userId) },
                    onDeleteAccount = onLogout
                )
            }
        }

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                BalanceCard(
                    balance = totalBalance, 
                    spentToday = spentToday,
                    balancesBySource = balancesBySource,
                    onAdjust = { source, _ ->
                        transactionToEdit = Transaction(
                            userId = userId,
                            description = "Balance Correction",
                            category = "Adjustment",
                            source = source,
                            type = TransactionType.ADJUSTMENT,
                            amount = 0.0
                        )
                        sheetType = "transaction"
                        showAddSheet = true
                    }
                )
            }

            item {
                MonthlyCashFlowSummaryCard(transactions = transactions, debts = debts)
            }

            item {
                CreditAccountsCard(
                    creditAccounts = creditAccounts,
                    balancesBySource = balancesBySource,
                    onAddCredit = { showAddCreditDialog = true },
                    onDeleteCredit = { id ->
                        itemToDelete = DeleteTarget(id, "credit")
                        showDeleteConfirm = true
                    }
                )
            }

            item {
                DailyBudgetRecommendationCard(totalBalance = totalBalance, transactions = transactions)
            }

            item {
                SalaryHistoryCard(transactions = transactions)
            }

            item {
                CashOnHandFlowCard(transactions = transactions, balancesBySource = balancesBySource)
            }

            item {
                MilestonesAndConsistencyCard(transactions, debts, totalBalance)
            }

            item {
                AICard(aiInsight)
            }

            item {
                SavingsChallengeCard(savingsChallenge)
            }

            item {
                SectionHeader("Monthly Budget Goals")
            }

            if (monthlyGoals.isEmpty()) {
                item { 
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            "AI is analyzing your spending to set personalized goals...",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                items(monthlyGoals) { goal ->
                    GoalItem(goal)
                }
            }
            
            if (transactions.isNotEmpty()) {
                item {
                    FinancialOverviewChart(transactions)
                }
                
                item {
                    SourcePieChart(transactions)
                }
            }

            if (transactions.any { it.type == TransactionType.EXPENSE }) {
                item {
                    SpendingPieChart(transactions)
                }
                
                item {
                    DailySpendingChart(transactions)
                }
            }

            if (transactions.any { it.type == TransactionType.DEBT_PAYMENT }) {
                item {
                    DebtPaymentBreakdownChart(transactions)
                }

                item {
                    DebtPaymentChart(transactions)
                }
            }

            if (transactions.any { it.type == TransactionType.BILL_PAYMENT || it.type == TransactionType.SUBSCRIPTION }) {
                item {
                    BillsAndSubscriptionsBreakdownChart(transactions)
                }

                item {
                    BillsAndSubscriptionsChart(transactions)
                }
            }

            if (transactions.any { it.type == TransactionType.WIRED_TRANSFER }) {
                item {
                    WiredTransferBreakdownChart(transactions)
                }

                item {
                    WiredTransferChart(transactions)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Recent Transactions")
                    if (transactions.isNotEmpty()) {
                        IconButton(onClick = { isTransactionsExpanded = !isTransactionsExpanded }) {
                            Icon(
                                if (isTransactionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle"
                            )
                        }
                    }
                }
            }

            if (isTransactionsExpanded && transactions.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Today", "2 Days", "3 Days", "7 Days", "30 Days").forEach { filter ->
                            FilterChip(
                                selected = dateFilter == filter,
                                onClick = { 
                                    dateFilter = filter
                                    transactionPage = 0
                                },
                                label = { Text(filter) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            if (!isTransactionsExpanded) {
                item {
                    val count = filteredTransactions.size
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { isTransactionsExpanded = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Show $count transaction history",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            } else if (filteredTransactions.isEmpty()) {
                item { EmptyState("No transactions found for this period") }
            } else {
                val totalPages = (filteredTransactions.size + pageSize - 1) / pageSize
                val paginatedTxs = filteredTransactions.drop(transactionPage * pageSize).take(pageSize)
                
                items(paginatedTxs) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        onDelete = { 
                            itemToDelete = DeleteTarget(transaction.id, "transaction")
                            showDeleteConfirm = true
                        },
                        onEdit = {
                            transactionToEdit = transaction
                            sheetType = "transaction"
                            showAddSheet = true
                        }
                    )
                }

                if (totalPages > 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { transactionPage = (transactionPage - 1).coerceAtLeast(0) },
                                enabled = transactionPage > 0
                            ) {
                                Text("Previous")
                            }
                            Text(
                                "Page ${transactionPage + 1} of $totalPages",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            TextButton(
                                onClick = { transactionPage = (transactionPage + 1).coerceAtMost(totalPages - 1) },
                                enabled = transactionPage < totalPages - 1
                            ) {
                                Text("Next")
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("Debts & Installments")
            }

            if (debts.isEmpty()) {
                item { EmptyState("No active debts") }
            } else {
                items(debts) { debt ->
                    DebtItem(
                        debt = debt,
                        onToggleInstallment = { month -> 
                            viewModel.toggleInstallmentPaid(debt, month)
                        },
                        onDelete = { 
                            itemToDelete = DeleteTarget(debt.id, "debt")
                            showDeleteConfirm = true
                        }
                    )
                }
            }
            
            item {
                DeveloperFooter()
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
        } else {
            SavingsTrackerView(
                userId = userId,
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun DeveloperFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Developed by Karl Michael Lopez",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text = "Version ${com.example.expenses_debt_saving_tracker.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
fun ProUpsellCard(productDetails: com.android.billingclient.api.ProductDetails, onBuy: (com.android.billingclient.api.ProductDetails) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Upgrade to Pro", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Unlock unlimited AI reports and advanced debt analytics.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onBuy(productDetails) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Subscribe Now")
            }
        }
    }
}

@Composable
fun SavingsChallengeCard(challenge: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.4f)
            ) {
                Icon(
                    Icons.Default.EmojiEvents, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    "WEEKLY CHALLENGE", 
                    fontWeight = FontWeight.Black, 
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    challenge, 
                    style = MaterialTheme.typography.bodyLarge, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
fun BalanceCard(
    balance: Double, 
    spentToday: Double, 
    balancesBySource: Map<String, Double>, 
    onAdjust: (String, Double) -> Unit = { _, _ -> }
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle background design element
            Surface(
                modifier = Modifier
                    .size(150.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-40).dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.05f)
            ) {}

            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "AVAILABLE BALANCE", 
                    color = Color.White.copy(alpha = 0.6f), 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "₱%,.2f", balance),
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null, 
                            tint = Color.White, 
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Spent Today: ₱${String.format(Locale.US, "%,.2f", spentToday)}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (balancesBySource.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    balancesBySource.forEach { (source, amount) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                source, 
                                color = Color.White.copy(alpha = 0.8f), 
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    String.format(Locale.US, "₱%,.2f", amount), 
                                    color = Color.White, 
                                    fontSize = 14.sp, 
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { onAdjust(source, amount) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit, 
                                        contentDescription = "Adjust", 
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class PaydayInfo(
    val nextPayday: Calendar,
    val followingPayday: Calendar,
    val daysToNext: Int,
    val daysToFollowing: Int,
    val isAdvanceSalary: Boolean,
    val advanceSalaryDate: Date?
)

fun calculatePaydayInfo(now: Calendar, transactions: List<Transaction>): PaydayInfo {
    fun getNextPayday(from: Calendar): Calendar {
        val next = from.clone() as Calendar
        val day = next.get(Calendar.DAY_OF_MONTH)
        return if (day < 15) {
            next.apply { set(Calendar.DAY_OF_MONTH, 15) }
        } else if (day < 30) {
            val maxDays = next.getActualMaximum(Calendar.DAY_OF_MONTH)
            next.apply { set(Calendar.DAY_OF_MONTH, 30.coerceAtMost(maxDays)) }
        } else {
            next.apply {
                add(Calendar.MONTH, 1)
                set(Calendar.DAY_OF_MONTH, 15)
            }
        }
    }

    var candidateNext = getNextPayday(now)

    val salaryTransactions = transactions.filter {
        it.type == TransactionType.SALARY ||
        (it.type == TransactionType.INCOME && it.category.contains("salary", ignoreCase = true)) ||
        it.description.contains("salary", ignoreCase = true)
    }
    val latestSalary = salaryTransactions.maxByOrNull { it.timestamp.toDate().time }

    var isAdvanceSalary = false
    var advanceSalaryDate: Date? = null

    if (latestSalary != null) {
        val salaryDate = latestSalary.timestamp.toDate()
        val salaryCal = Calendar.getInstance().apply { time = salaryDate }

        val candidateDay = candidateNext.get(Calendar.DAY_OF_MONTH)
        val cycleStart = (candidateNext.clone() as Calendar).apply {
            if (candidateDay == 15) {
                set(Calendar.DAY_OF_MONTH, 1)
            } else {
                set(Calendar.DAY_OF_MONTH, 16)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (salaryCal.timeInMillis >= cycleStart.timeInMillis && salaryCal.timeInMillis <= now.timeInMillis + (24 * 60 * 60 * 1000)) {
            isAdvanceSalary = true
            advanceSalaryDate = salaryDate
            candidateNext = getNextPayday((candidateNext.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) })
        }
    }

    val followingPayday = getNextPayday((candidateNext.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) })

    val daysToNext = ((candidateNext.timeInMillis - now.timeInMillis) / (24 * 60 * 60 * 1000) + 1).toInt().coerceAtLeast(1)
    val daysToFollowing = ((followingPayday.timeInMillis - now.timeInMillis) / (24 * 60 * 60 * 1000) + 1).toInt().coerceAtLeast(1)

    return PaydayInfo(
        nextPayday = candidateNext,
        followingPayday = followingPayday,
        daysToNext = daysToNext,
        daysToFollowing = daysToFollowing,
        isAdvanceSalary = isAdvanceSalary,
        advanceSalaryDate = advanceSalaryDate
    )
}

@Composable
fun MilestonesAndConsistencyCard(transactions: List<Transaction>, debts: List<Debt>, totalBalance: Double) {
    val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val paydayInfo = remember(now.timeInMillis, transactions) {
        calculatePaydayInfo(now, transactions)
    }

    val dailyBudgetThreshold = (totalBalance / paydayInfo.daysToNext).coerceAtLeast(0.0)
    
    // 1. Calculate Daily Spending Consistency for the last 7 days
    val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
    val last7Days = (0..6).map { i ->
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -i)
        dateFormat.format(cal.time)
    }.reversed()

    val spendingMap = transactions
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { dateFormat.format(it.timestamp.toDate()) }
        .mapValues { it.value.sumOf { t -> t.amount } }

    var consistentDaysCount = 0
    last7Days.forEach { day ->
        val daySpending = spendingMap[day] ?: 0.0
        // Aligned: Staying under the recommended daily budget based on payday
        if (daySpending <= dailyBudgetThreshold && dailyBudgetThreshold > 0) {
            consistentDaysCount++
        }
    }

    // 2. Calculate Debt Payment Milestones
    val totalInstallments = debts.sumOf { it.totalMonths }
    val paidInstallments = debts.sumOf { it.installments.count { inst -> inst.isPaid } }
    val debtProgress = if (totalInstallments > 0) paidInstallments.toFloat() / totalInstallments.toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "MILESTONES & CONSISTENCY",
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Budget Consistency Section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = (if (consistentDaysCount >= 5) Color(0xFF2D6A4F) else Color(0xFFF9C74F)).copy(alpha = 0.15f)
                ) {
                    Icon(
                        imageVector = if (consistentDaysCount >= 5) Icons.Default.VerifiedUser else Icons.Default.ElectricBolt,
                        contentDescription = null,
                        tint = if (consistentDaysCount >= 5) Color(0xFF2D6A4F) else Color(0xFFE9C46A),
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Budget Consistency",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "You stayed within budget on $consistentDaysCount/7 days this week.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(16.dp))

            // Debt Milestones Section
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Debt Freedom Milestone",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (totalInstallments > 0) {
                        Text(
                            text = "Paid $paidInstallments of $totalInstallments installments total.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { debtProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = Color(0xFF2D6A4F),
                            trackColor = Color(0xFF2D6A4F).copy(alpha = 0.1f)
                        )
                        Text(
                            text = "${(debtProgress * 100).toInt()}% completed",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D6A4F),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(
                            text = "No active debts! You're completely debt free.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF2D6A4F),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyBudgetRecommendationCard(totalBalance: Double, transactions: List<Transaction> = emptyList()) {
    val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val paydayInfo = remember(now.timeInMillis, transactions) {
        calculatePaydayInfo(now, transactions)
    }

    val dailyBudgetNext = (totalBalance / paydayInfo.daysToNext).coerceAtLeast(0.0)
    val dailyBudgetFollowing = (totalBalance / paydayInfo.daysToFollowing).coerceAtLeast(0.0)

    val targetBalance150 = 150.0 * paydayInfo.daysToNext
    val targetBalance200 = 200.0 * paydayInfo.daysToNext

    val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
    val salaryDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    @Composable
    fun getBudgetColor(amount: Double): Color {
        return when {
            amount >= 200 -> Color(0xFF2D6A4F) // Green (Target reached)
            amount >= 150 -> Color(0xFFE9C46A) // Yellow/Gold (Manageable)
            else -> Color(0xFFE63946) // Red (Below basis)
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.TipsAndUpdates,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recommended Daily Budget",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            if (paydayInfo.isAdvanceSalary && paydayInfo.advanceSalaryDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF2D6A4F).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = null,
                            tint = Color(0xFF2D6A4F),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Salary received in advance (${salaryDateFormat.format(paydayInfo.advanceSalaryDate)}). Payday adjusted!",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D6A4F)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(Locale.US, "₱%,.2f", dailyBudgetNext),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = getBudgetColor(dailyBudgetNext)
                        )
                    )
                    Text(
                        text = "Next Payday",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${dateFormat.format(paydayInfo.nextPayday.time)} (${paydayInfo.daysToNext} days)",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                    )
                }

                VerticalDivider(
                    modifier = Modifier.height(50.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(Locale.US, "₱%,.2f", dailyBudgetFollowing),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = getBudgetColor(dailyBudgetFollowing)
                        )
                    )
                    Text(
                        text = "Second Payday",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${dateFormat.format(paydayInfo.followingPayday.time)} (${paydayInfo.daysToFollowing} days)",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "RECOMMENDED BALANCE TARGETS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            val isBelow150 = totalBalance < targetBalance150
            val isBelow200 = totalBalance < targetBalance200

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recommended Balance (₱150/day):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = String.format(Locale.US, "₱%,.2f", targetBalance150),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isBelow150) Color(0xFFE63946) else Color(0xFF2D6A4F)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recommended Balance (₱200/day):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = String.format(Locale.US, "₱%,.2f", targetBalance200),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isBelow200) Color(0xFFE63946) else Color(0xFF2D6A4F)
                )
            }

            if (isBelow150) {
                // Critical Warning (Red) - Below both targets
                val warningColor = Color(0xFFE63946)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = warningColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = warningColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Warning: Available balance is below recommended targets, putting your budget in the red.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = warningColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (isBelow200) {
                // Moderate Warning (Orange) - Met ₱150 target (Green), but below ₱200 target (Red)
                val warningColor = Color(0xFFE65100)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = warningColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = warningColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Warning: You are still within budget, but it is tight with a high risk of exceeding your targets. Lower your spending to stick to your target budget.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = warningColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Safe / On Target (Green) - Met both targets!
                val successColor = Color(0xFF2D6A4F)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = successColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = successColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Great job! You're on target. Keep it up to build long-term financial stability!",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = successColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Remaining balance distributed until your next paydays",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SalaryHistoryCard(transactions: List<Transaction>) {
    val salaryTransactions = remember(transactions) {
        transactions.filter {
            it.type == TransactionType.SALARY ||
            (it.type == TransactionType.INCOME && it.category.contains("salary", ignoreCase = true)) ||
            it.description.contains("salary", ignoreCase = true)
        }.sortedByDescending { it.timestamp.toDate().time }
    }

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }
    val dateTimeFormat = remember { SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.US) }

    val groupedSalaries = remember(salaryTransactions) {
        salaryTransactions.groupBy { monthFormat.format(it.timestamp.toDate()) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = Color(0xFF2D6A4F).copy(alpha = 0.12f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = Color(0xFF2D6A4F),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "SALARY PAYOUT HISTORY",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (salaryTransactions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2D6A4F).copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "${salaryTransactions.size} Record${if (salaryTransactions.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D6A4F),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (groupedSalaries.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = "No salary payouts recorded yet. Select 'Salary' as transaction type when adding income to track salary dates & times.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                groupedSalaries.forEach { (monthYear, salaries) ->
                    val monthlyTotal = salaries.sumOf { it.amount }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        // Month Segregation Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = monthYear,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Total: ₱${String.format(Locale.US, "%,.2f", monthlyTotal)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF2D6A4F)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        salaries.forEach { salary ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF2D6A4F).copy(alpha = 0.1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        tint = Color(0xFF2D6A4F),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = salary.description.ifEmpty { "Salary Payout" },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (salary.source.isNotEmpty()) {
                                        Text(
                                            text = "Source: ${salary.source}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    Text(
                                        text = dateTimeFormat.format(salary.timestamp.toDate()),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }

                                Text(
                                    text = String.format(Locale.US, "+ ₱%,.2f", salary.amount),
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF2D6A4F),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CashOnHandFlowCard(transactions: List<Transaction>, balancesBySource: Map<String, Double>) {
    val totalWithdrawn = remember(transactions) {
        transactions.filter { it.type == TransactionType.WITHDRAWAL }.sumOf { it.amount }
    }
    val totalDeposited = remember(transactions) {
        transactions.filter { it.type == TransactionType.DEPOSIT }.sumOf { it.amount }
    }
    val cashOnHandBalance = balancesBySource["Cash on Hand"] ?: 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = Color(0xFF4895EF).copy(alpha = 0.12f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Atm,
                            contentDescription = null,
                            tint = Color(0xFF4895EF),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "CASH ON HAND FLOW",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF4895EF).copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "Cash: ₱${String.format(Locale.US, "%,.2f", cashOnHandBalance)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4895EF),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bank Withdrawal (to Cash)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallReceived,
                            contentDescription = null,
                            tint = Color(0xFF2D6A4F),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Cash Withdrawn",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "+ ₱%,.2f", totalWithdrawn),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF2D6A4F)
                    )
                    Text(
                        text = "Bank ➔ Cash",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                VerticalDivider(
                    modifier = Modifier.height(48.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // Cash Deposit (Cash to Bank)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallMade,
                            contentDescription = null,
                            tint = Color(0xFFE63946),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Bank Deposited",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "- ₱%,.2f", totalDeposited),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFE63946)
                    )
                    Text(
                        text = "Cash ➔ Bank",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}



@Composable
fun AICard(insight: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "AI Financial Advisor", 
                        fontWeight = FontWeight.ExtraBold, 
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        insight, 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Educational purposes only. Not financial advice.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}



@Composable
fun MonthlyCashFlowSummaryCard(transactions: List<Transaction>, debts: List<Debt>) {
    val now = Calendar.getInstance()
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

    val currentMonthName = SimpleDateFormat("MMMM yyyy", Locale.US).format(now.time)

    val thisMonthTxs = remember(transactions, startOfMonthMs, endOfMonthMs) {
        transactions.filter {
            val t = it.timestamp.toDate().time
            t >= startOfMonthMs && t < endOfMonthMs
        }
    }

    // 1. Real Income (Inflow): Salary + Income + Positive Adjustments
    val realIncome = remember(thisMonthTxs) {
        thisMonthTxs.filter {
            it.type == TransactionType.SALARY || it.type == TransactionType.INCOME ||
            (it.type == TransactionType.ADJUSTMENT && it.amount > 0)
        }.sumOf { it.amount }
    }

    // 2. Real Expenses (Outflow): Expense + Debt Payment + Bill Payment + Subscription + Wired Transfer
    val realExpenses = remember(thisMonthTxs) {
        thisMonthTxs.filter {
            it.type == TransactionType.EXPENSE || it.type == TransactionType.DEBT_PAYMENT ||
            it.type == TransactionType.BILL_PAYMENT || it.type == TransactionType.SUBSCRIPTION ||
            it.type == TransactionType.WIRED_TRANSFER
        }.sumOf { it.amount }
    }

    // 3. Money Borrowed This Month: Debts added + Loan Proceeds transactions
    val borrowedFromDebts = remember(debts, startOfMonthMs, endOfMonthMs) {
        debts.filter { debt ->
            val t = debt.startDate.toDate().time
            t >= startOfMonthMs && t < endOfMonthMs
        }.sumOf { if (it.principalAmount > 0) it.principalAmount else it.totalAmount }
    }

    val borrowedFromTxs = remember(thisMonthTxs) {
        thisMonthTxs.filter {
            it.type == TransactionType.LOAN_PROCEEDS ||
            (it.type == TransactionType.INCOME && (
                it.category.contains("loan", ignoreCase = true) ||
                it.category.contains("borrow", ignoreCase = true) ||
                it.description.contains("loan", ignoreCase = true) ||
                it.description.contains("borrow", ignoreCase = true)
            ))
        }.sumOf { it.amount }
    }

    val moneyBorrowedThisMonth = maxOf(borrowedFromDebts, borrowedFromTxs)

    // 4. Internal Movements (Transfers, Withdrawals, Deposits)
    val internalMovements = remember(thisMonthTxs) {
        thisMonthTxs.filter {
            it.type == TransactionType.TRANSFER || it.type == TransactionType.WITHDRAWAL || it.type == TransactionType.DEPOSIT
        }.sumOf { it.amount }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "MONTHLY CASH FLOW",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = currentMonthName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real Inflow Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = Color(0xFF2D6A4F),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Real Income (Inflow)", style = MaterialTheme.typography.bodyMedium, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Salary & external earnings", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format(Locale.US, "₱%,.2f", realIncome),
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF2D6A4F)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(10.dp))

            // Real Outflow Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = Color(0xFFE63946),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Real Expenses (Outflow)", style = MaterialTheme.typography.bodyMedium, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Spending, bills & debt payments", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format(Locale.US, "₱%,.2f", realExpenses),
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFE63946)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(10.dp))

            // Money Borrowed This Month Line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RequestQuote,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Money Borrowed This Month", style = MaterialTheme.typography.bodyMedium, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("New loans & borrowed proceeds", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format(Locale.US, "₱%,.2f", moneyBorrowedThisMonth),
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFD97706)
                )
            }

            if (internalMovements > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))

                // Internal Movements Line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = null,
                            tint = Color(0xFF4895EF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Internal Movements", style = MaterialTheme.typography.bodyMedium, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Transfers/withdrawals (not income/expense)", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format(Locale.US, "₱%,.2f", internalMovements),
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4895EF)
                    )
                }
            }
        }
    }
}

@Composable
fun CreditAccountsCard(
    creditAccounts: List<com.example.expenses_debt_saving_tracker.model.CreditAccount>,
    balancesBySource: Map<String, Double>,
    onAddCredit: () -> Unit,
    onDeleteCredit: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = Color(0xFFD97706).copy(alpha = 0.12f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "CREDIT CARDS & LINES",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onAddCredit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Credit Line",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (creditAccounts.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    onClick = onAddCredit
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Credit Limit (e.g., Salmon Credit, GCredit)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                creditAccounts.forEach { account ->
                    val availableCredit = balancesBySource[account.name] ?: account.creditLimit
                    val progress = if (account.creditLimit > 0) (availableCredit / account.creditLimit).toFloat().coerceIn(0f, 1f) else 0f

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Limit: ₱${String.format(Locale.US, "%,.2f", account.creditLimit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Available:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "₱%,.2f", availableCredit),
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 14.sp,
                                        color = if (availableCredit > 0) Color(0xFF2D6A4F) else Color(0xFFE63946)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { onDeleteCredit(account.id) }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = Color(0xFF2D6A4F),
                            trackColor = Color(0xFF2D6A4F).copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddCreditAccountDialog(
    userId: String,
    onDismiss: () -> Unit,
    onAdd: (com.example.expenses_debt_saving_tracker.model.CreditAccount) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var creditLimit by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Credit Line / Card", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Set up a credit limit (e.g., Salmon Credit, GCredit). The available credit will be added to your Total Available Balance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Salmon Credit, GCredit)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = creditLimit,
                    onValueChange = { creditLimit = it },
                    label = { Text("Credit Limit (₱)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limitVal = creditLimit.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && limitVal > 0) {
                        onAdd(
                            com.example.expenses_debt_saving_tracker.model.CreditAccount(
                                userId = userId,
                                name = name.trim(),
                                creditLimit = limitVal
                            )
                        )
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank() && (creditLimit.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("Add Credit Line", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TransactionItem(transaction: Transaction, onDelete: () -> Unit, onEdit: () -> Unit) {
    val icon = when(transaction.type) {
        TransactionType.INCOME, TransactionType.SALARY -> Icons.AutoMirrored.Filled.TrendingUp
        TransactionType.EXPENSE, TransactionType.BILL_PAYMENT, TransactionType.SUBSCRIPTION -> Icons.AutoMirrored.Filled.TrendingDown
        TransactionType.SAVINGS -> Icons.Default.Savings
        TransactionType.ADJUSTMENT -> Icons.Default.AccountBalanceWallet
        TransactionType.DEPOSIT -> Icons.Default.AddCard
        TransactionType.WITHDRAWAL -> Icons.Default.Atm
        TransactionType.TRANSFER -> Icons.AutoMirrored.Filled.CompareArrows
        TransactionType.DEBT_PAYMENT -> Icons.Default.CreditCard
        TransactionType.WIRED_TRANSFER -> Icons.AutoMirrored.Filled.Send
        TransactionType.LOAN_PROCEEDS -> Icons.Default.RequestQuote
        TransactionType.CASH_ON_HAND -> Icons.Default.Money
    }
    val iconColor = when(transaction.type) {
        TransactionType.EXPENSE, TransactionType.WITHDRAWAL, TransactionType.BILL_PAYMENT, TransactionType.SUBSCRIPTION -> Color(0xFFE63946)
        TransactionType.SAVINGS -> MaterialTheme.colorScheme.secondary
        TransactionType.ADJUSTMENT -> MaterialTheme.colorScheme.tertiary
        TransactionType.DEPOSIT, TransactionType.INCOME, TransactionType.SALARY -> Color(0xFF2D6A4F)
        TransactionType.TRANSFER -> Color(0xFF4895EF)
        TransactionType.DEBT_PAYMENT -> Color(0xFF1B263B)
        TransactionType.WIRED_TRANSFER -> Color(0xFF8E24AA)
        TransactionType.LOAN_PROCEEDS -> Color(0xFFD97706)
        TransactionType.CASH_ON_HAND -> Color(0xFF2563EB)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        onClick = onEdit,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(10.dp),
                color = iconColor.copy(alpha = 0.1f)
            ) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = iconColor, 
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1.0f)) {
                Text(
                    transaction.description, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                val sourceText = if (transaction.type == TransactionType.TRANSFER) {
                    "${transaction.source} ➔ ${transaction.destinationSource}"
                } else {
                    "${transaction.category}${if (transaction.source.isNotEmpty()) " • ${transaction.source}" else ""}"
                }
                Text(
                    text = sourceText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val symbol = when(transaction.type) {
                    TransactionType.EXPENSE, TransactionType.WITHDRAWAL, TransactionType.SAVINGS, 
                    TransactionType.DEBT_PAYMENT, TransactionType.BILL_PAYMENT, TransactionType.SUBSCRIPTION, TransactionType.WIRED_TRANSFER -> "-"
                    TransactionType.TRANSFER -> ""
                    else -> "+"
                }
                Text(
                    text = String.format(Locale.US, "$symbol ₱%,.2f", transaction.amount),
                    fontWeight = FontWeight.Black,
                    color = iconColor,
                    fontSize = 15.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = SimpleDateFormat("MMM dd", Locale.US).format(transaction.timestamp.toDate()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Delete", 
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.3f), 
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DebtItem(debt: Debt, onToggleInstallment: (Int) -> Unit = {}, onDelete: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val sdf = remember(locale) { SimpleDateFormat("MMM dd, yyyy", locale) }
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        onClick = { expanded = !expanded },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = (if (debt.isPaid) Color(0xFF2D6A4F) else Color(0xFFE63946)).copy(alpha = 0.1f)
                ) {
                    Icon(
                        if (debt.isPaid) Icons.Default.CheckCircle else Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = if (debt.isPaid) Color(0xFF2D6A4F) else Color(0xFFE63946),
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1.0f)) {
                    Text(debt.name, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Remaining: ${String.format(Locale.US, "₱%,.2f", debt.remainingAmount)}", 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = (if (debt.isPaid) Color(0xFF2D6A4F) else MaterialTheme.colorScheme.error).copy(alpha = 0.1f)
                    ) {
                        Text(
                            if (debt.isPaid) "PAID" else "ACTIVE",
                            fontWeight = FontWeight.Black,
                            color = if (debt.isPaid) Color(0xFF2D6A4F) else MaterialTheme.colorScheme.error,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                    }
                }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
                
                debt.installments.forEach { installment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "${installment.month}${getOrdinal(installment.month)} Installment", 
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Due: ${sdf.format(installment.dueDate.toDate())}", 
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Surface(
                            onClick = { onToggleInstallment(installment.month) },
                            shape = RoundedCornerShape(12.dp),
                            color = (if (installment.isPaid) Color(0xFF2D6A4F) else Color.Gray).copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (installment.isPaid) "Paid" else "Pay",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (installment.isPaid) Color(0xFF2D6A4F) else Color.Gray
                                )
                                Checkbox(
                                    checked = installment.isPaid,
                                    onCheckedChange = { onToggleInstallment(installment.month) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF2D6A4F),
                                        uncheckedColor = Color.Gray.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getOrdinal(n: Int): String {
    if (n in 11..13) return "th"
    return when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}

@Composable
fun GoalItem(goal: com.example.expenses_debt_saving_tracker.model.MonthlyGoal) {
    val progress = if (goal.amount > 0) (goal.currentSpending / goal.amount).toFloat() else 0f
    val color = if (progress > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(goal.category, fontWeight = FontWeight.Bold)
                Text(
                    text = "${String.format(Locale.US, "₱%,.0f", goal.currentSpending)} / ${String.format(Locale.US, "₱%,.0f", goal.amount)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceAtMost(1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = color,
                trackColor = color.copy(alpha = 0.1f),
            )
            if (progress > 1f) {
                Text(
                    "Exceeded by ${String.format(Locale.US, "₱%,.2f", goal.currentSpending - goal.amount)}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.2.sp
    )
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun AddTransactionForm(
    userId: String,
    onDismiss: () -> Unit,
    onAdd: (Transaction) -> Unit,
    viewModel: FinanceViewModel = viewModel(),
    initialTransaction: Transaction? = null
) {
    var amount by remember { mutableStateOf(initialTransaction?.amount?.toString() ?: "") }
    var description by remember { mutableStateOf(initialTransaction?.description ?: "") }
    var category by remember { mutableStateOf(initialTransaction?.category ?: "") }
    var source by remember { mutableStateOf(initialTransaction?.source ?: "") }
    var destinationSource by remember { mutableStateOf(initialTransaction?.destinationSource ?: "") }
    var type by remember { mutableStateOf(initialTransaction?.type ?: TransactionType.EXPENSE) }

    // Smart category suggestion
    LaunchedEffect(description) {
        if (description.length > 3 && category.isEmpty()) {
            viewModel.suggestCategory(description) { suggested ->
                category = suggested
            }
        }
    }

    Column(
        modifier = Modifier.padding(24.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (initialTransaction == null) "New Transaction" else "Edit Transaction",
            style = MaterialTheme.typography.headlineSmall, 
            fontWeight = FontWeight.Bold
        )
        
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount (₱)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Payments, null) }
        )
        
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        
        if (type != TransactionType.TRANSFER) {
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category (e.g. Food, Rent)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            label = { Text(if (type == TransactionType.TRANSFER) "From Source (e.g. Landbank)" else "Source (e.g. GCash, Bank)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null) }
        )

        if (type == TransactionType.TRANSFER) {
            OutlinedTextField(
                value = destinationSource,
                onValueChange = { destinationSource = it },
                label = { Text("To Source (e.g. GCash)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Input, null) }
            )
        }
        
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            TransactionType.entries.forEachIndexed { index, t ->
                SegmentedButton(
                    selected = type == t,
                    onClick = { type = t },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TransactionType.entries.size)
                ) {
                    Text(
                        text = t.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
        
        Button(
            onClick = {
                val transaction = Transaction(
                    id = initialTransaction?.id ?: "",
                    userId = userId,
                    amount = amount.toDoubleOrNull() ?: 0.0,
                    description = description,
                    category = if (type == TransactionType.TRANSFER) "Transfer" else category,
                    source = source,
                    destinationSource = destinationSource,
                    type = type,
                    timestamp = initialTransaction?.timestamp ?: Timestamp.now()
                )
                onAdd(transaction)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (initialTransaction == null) "Add Transaction" else "Update Transaction", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SpendingPieChart(transactions: List<Transaction>) {
    val expensesByCategory = transactions
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.category }
        .mapValues { it.value.sumOf { t -> t.amount } }
        .toList()
        .sortedByDescending { it.second }

    if (expensesByCategory.isEmpty()) return

    val totalExpense = expensesByCategory.sumOf { it.second }
    val colors = listOf(
        Color(0xFFE63946), Color(0xFFF1FAEE), Color(0xFFA8DADC), 
        Color(0xFF457B9D), Color(0xFF1D3557), Color(0xFF2A9D8F), 
        Color(0xFFE9C46A), Color(0xFFF4A261), Color(0xFFE76F51)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SPENDING BREAKDOWN", 
                    fontWeight = FontWeight.Black, 
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE63946).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Total: ₱${String.format(Locale.US, "%,.2f", totalExpense)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE63946),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var startAngle = -90f
                        expensesByCategory.forEachIndexed { index, entry ->
                            val sweepAngle = (entry.second.toFloat() / totalExpense.toFloat()) * 360f
                            drawArc(
                                color = colors[index % colors.size],
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = true
                            )
                            startAngle += sweepAngle
                        }
                    }
                    Surface(
                        modifier = Modifier.size(90.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "TOTAL SPENT",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = String.format(Locale.US, "₱%,.0f", totalExpense),
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFE63946)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(20.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    expensesByCategory.take(5).forEachIndexed { index, entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(colors[index % colors.size], CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    entry.first, 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(Locale.US, "₱%,.0f", entry.second),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DebtPaymentBreakdownChart(transactions: List<Transaction>) {
    val debtPaymentsByName = transactions
        .filter { it.type == TransactionType.DEBT_PAYMENT }
        .groupBy { it.description.ifEmpty { "General Debt" } }
        .mapValues { entry -> entry.value.sumOf { t -> t.amount } }
        .toList()
        .sortedByDescending { it.second }

    if (debtPaymentsByName.isEmpty()) return

    val totalDebtPayments = debtPaymentsByName.sumOf { it.second }
    val colors = listOf(
        Color(0xFF1B263B), Color(0xFF415A77), Color(0xFF778DA9), 
        Color(0xFF2D6A4F), Color(0xFFE63946), Color(0xFF4895EF),
        Color(0xFFE9C46A), Color(0xFFF4A261)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DEBT PAYMENT BREAKDOWN", 
                    fontWeight = FontWeight.Black, 
                    color = Color(0xFF1B263B),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp
                )
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2D6A4F).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Total: ₱${String.format(Locale.US, "%,.2f", totalDebtPayments)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2D6A4F),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var startAngle = -90f
                        debtPaymentsByName.forEachIndexed { index, entry ->
                            val sweepAngle = (entry.second.toFloat() / totalDebtPayments.toFloat()) * 360f
                            drawArc(
                                color = colors[index % colors.size],
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = true
                            )
                            startAngle += sweepAngle
                        }
                    }
                    Surface(
                        modifier = Modifier.size(90.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "TOTAL PAID",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = String.format(Locale.US, "₱%,.0f", totalDebtPayments),
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF2D6A4F)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(20.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    debtPaymentsByName.take(5).forEachIndexed { index, entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(colors[index % colors.size], CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    entry.first, 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(Locale.US, "₱%,.0f", entry.second),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DebtPaymentChart(transactions: List<Transaction>) {
    val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
    
    // 1. Generate the last 7 dates (including today)
    val last7Days = (0..6).map { i ->
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -i)
        dateFormat.format(cal.time)
    }.reversed()

    // 2. Map debt payments to those dates
    val paymentMap = transactions
        .filter { it.type == TransactionType.DEBT_PAYMENT }
        .groupBy { dateFormat.format(it.timestamp.toDate()) }
        .mapValues { it.value.sumOf { t -> t.amount } }

    val paymentsByDay = last7Days.map { day ->
        day to (paymentMap[day] ?: 0.0)
    }

    if (paymentsByDay.all { it.second == 0.0 }) return

    val maxAmount = paymentsByDay.maxOf { it.second }.coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "DEBT PAYMENT TREND", 
                fontWeight = FontWeight.Black, 
                color = Color(0xFF1B263B),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                paymentsByDay.forEach { (day, amount) ->
                    val barHeight = (amount / maxAmount * 120).dp
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (amount > 0) {
                            Text(
                                text = String.format(Locale.US, "₱%.0f", amount),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1B263B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(barHeight.coerceAtLeast(4.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF1B263B),
                                            Color(0xFF1B263B).copy(alpha = 0.6f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = day, 
                            style = MaterialTheme.typography.labelSmall, 
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BillsAndSubscriptionsBreakdownChart(transactions: List<Transaction>) {
    val billsByCategory = transactions
        .filter { it.type == TransactionType.BILL_PAYMENT || it.type == TransactionType.SUBSCRIPTION }
        .groupBy { it.category.ifEmpty { it.description.ifEmpty { "Bills & Subs" } } }
        .mapValues { entry -> entry.value.sumOf { t -> t.amount } }
        .toList()
        .sortedByDescending { it.second }

    if (billsByCategory.isEmpty()) return

    val totalBills = billsByCategory.sumOf { it.second }
    val colors = listOf(
        Color(0xFFFF5722), Color(0xFF673AB7), Color(0xFFFF9800), 
        Color(0xFF3F51B5), Color(0xFF009688), Color(0xFFE91E63),
        Color(0xFF2196F3), Color(0xFF4CAF50)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "BILLS & SUBS BREAKDOWN", 
                    fontWeight = FontWeight.Black, 
                    color = Color(0xFFFF5722),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp
                )
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFF5722).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Total: ₱${String.format(Locale.US, "%,.2f", totalBills)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFF5722),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var startAngle = -90f
                        billsByCategory.forEachIndexed { index, entry ->
                            val sweepAngle = (entry.second.toFloat() / totalBills.toFloat()) * 360f
                            drawArc(
                                color = colors[index % colors.size],
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = true
                            )
                            startAngle += sweepAngle
                        }
                    }
                    Surface(
                        modifier = Modifier.size(90.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "TOTAL BILLS",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = String.format(Locale.US, "₱%,.0f", totalBills),
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF5722)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(20.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    billsByCategory.take(5).forEachIndexed { index, entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(colors[index % colors.size], CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    entry.first, 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(Locale.US, "₱%,.0f", entry.second),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BillsAndSubscriptionsChart(transactions: List<Transaction>) {
    val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
    
    val last7Days = (0..6).map { i ->
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -i)
        dateFormat.format(cal.time)
    }.reversed()

    val billsMap = transactions
        .filter { it.type == TransactionType.BILL_PAYMENT || it.type == TransactionType.SUBSCRIPTION }
        .groupBy { dateFormat.format(it.timestamp.toDate()) }
        .mapValues { it.value.sumOf { t -> t.amount } }

    val billsByDay = last7Days.map { day ->
        day to (billsMap[day] ?: 0.0)
    }

    if (billsByDay.all { it.second == 0.0 }) return

    val maxAmount = billsByDay.maxOf { it.second }.coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "BILLS & SUBSCRIPTIONS TREND", 
                fontWeight = FontWeight.Black, 
                color = Color(0xFFFF5722),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                billsByDay.forEach { (day, amount) ->
                    val barHeight = (amount / maxAmount * 120).dp
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (amount > 0) {
                            Text(
                                text = String.format(Locale.US, "₱%.0f", amount),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF5722)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(barHeight.coerceAtLeast(4.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFFFF5722),
                                            Color(0xFFFF5722).copy(alpha = 0.6f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = day, 
                            style = MaterialTheme.typography.labelSmall, 
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailySpendingChart(transactions: List<Transaction>) {
    val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
    
    // 1. Generate the last 7 dates (including today)
    val last7Days = (0..6).map { i ->
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -i)
        dateFormat.format(cal.time)
    }.reversed()

    // 2. Map expenses to those dates
    val spendingMap = transactions
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { dateFormat.format(it.timestamp.toDate()) }
        .mapValues { it.value.sumOf { t -> t.amount } }

    val spendingByDay = last7Days.map { day ->
        day to (spendingMap[day] ?: 0.0)
    }

    if (spendingByDay.all { it.second == 0.0 }) return

    val maxAmount = spendingByDay.maxOf { it.second }.coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "DAILY SPENDING TREND", 
                fontWeight = FontWeight.Black, 
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                spendingByDay.forEach { (day, amount) ->
                    val barHeight = (amount / maxAmount * 120).dp
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (amount > 0) {
                            Text(
                                text = String.format(Locale.US, "₱%.0f", amount),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(barHeight.coerceAtLeast(4.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = day, 
                            style = MaterialTheme.typography.labelSmall, 
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SourcePieChart(transactions: List<Transaction>) {
    val balanceBySource = transactions.groupBy { it.source.ifEmpty { "Cash on Hand" } }
        .mapValues { entry ->
            entry.value.sumOf { it.amount * if (it.type in listOf(TransactionType.INCOME, TransactionType.SALARY, TransactionType.ADJUSTMENT, TransactionType.DEPOSIT, TransactionType.LOAN_PROCEEDS, TransactionType.CASH_ON_HAND)) 1 else -1 }
        }.filter { it.value > 0 }

    if (balanceBySource.isEmpty()) return

    val totalValue = balanceBySource.values.sum()
    val colors = listOf(
        Color(0xFF1B263B), Color(0xFF415A77), Color(0xFF778DA9), 
        Color(0xFF0D1B2A), Color(0xFF2D6A4F), Color(0xFF52B788)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "BALANCE BY SOURCE", 
                fontWeight = FontWeight.Black, 
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var startAngle = -90f
                        balanceBySource.values.forEachIndexed { index, amount ->
                            val sweepAngle = (amount.toFloat() / totalValue.toFloat()) * 360f
                            drawArc(
                                color = colors[index % colors.size],
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = true
                            )
                            startAngle += sweepAngle
                        }
                    }
                    Surface(
                        modifier = Modifier.size(90.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {}
                }
                
                Spacer(modifier = Modifier.width(24.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    balanceBySource.entries.take(5).forEachIndexed { index, entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(colors[index % colors.size], CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    entry.key, 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(Locale.US, "₱%,.0f", entry.value),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialOverviewChart(transactions: List<Transaction>) {
    val totalsByType = transactions
        .groupBy { it.type }
        .mapValues { it.value.sumOf { t -> t.amount } }

    if (totalsByType.isEmpty()) return

    val totalValue = totalsByType.values.sum()
    val colors = mapOf(
        TransactionType.INCOME to Color(0xFF2D6A4F),
        TransactionType.SALARY to Color(0xFF1B263B),
        TransactionType.EXPENSE to Color(0xFFE63946),
        TransactionType.SAVINGS to Color(0xFF52B788),
        TransactionType.ADJUSTMENT to Color(0xFF415A77),
        TransactionType.DEPOSIT to Color(0xFF4895EF),
        TransactionType.WITHDRAWAL to Color(0xFFF94144),
        TransactionType.TRANSFER to Color(0xFF8D99AE),
        TransactionType.DEBT_PAYMENT to Color(0xFF1B263B),
        TransactionType.BILL_PAYMENT to Color(0xFFFF5722),
        TransactionType.SUBSCRIPTION to Color(0xFF673AB7),
        TransactionType.WIRED_TRANSFER to Color(0xFF8E24AA),
        TransactionType.LOAN_PROCEEDS to Color(0xFFD97706),
        TransactionType.CASH_ON_HAND to Color(0xFF2563EB)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CASH FLOW MIX", 
                    fontWeight = FontWeight.Black, 
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Total: ₱${String.format(Locale.US, "%,.2f", totalValue)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var startAngle = -90f
                        totalsByType.forEach { (type, amount) ->
                            val sweepAngle = (amount.toFloat() / totalValue.toFloat()) * 360f
                            drawArc(
                                color = colors[type] ?: Color.Gray,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = true
                            )
                            startAngle += sweepAngle
                        }
                    }
                    // Inner hole for donut style
                    Surface(
                        modifier = Modifier.size(90.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "TOTAL FLOW",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = String.format(Locale.US, "₱%,.0f", totalValue),
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(20.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    totalsByType.forEach { (type, amount) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(colors[type] ?: Color.Gray, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    type.name, 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(Locale.US, "₱%,.0f", amount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WiredTransferBreakdownChart(transactions: List<Transaction>) {
    val wiredByName = transactions
        .filter { it.type == TransactionType.WIRED_TRANSFER }
        .groupBy { it.description.ifEmpty { "Wired Transfer" } }
        .mapValues { entry -> entry.value.sumOf { t -> t.amount } }
        .toList()
        .sortedByDescending { it.second }

    if (wiredByName.isEmpty()) return

    val totalWired = wiredByName.sumOf { it.second }
    val colors = listOf(
        Color(0xFF8E24AA), Color(0xFFAB47BC), Color(0xFFBA68C8), 
        Color(0xFFCE93D8), Color(0xFF4895EF), Color(0xFF1B263B),
        Color(0xFF2D6A4F), Color(0xFFE9C46A)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "WIRED TRANSFER BREAKDOWN", 
                    fontWeight = FontWeight.Black, 
                    color = Color(0xFF8E24AA),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp
                )
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF8E24AA).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Total: ₱${String.format(Locale.US, "%,.2f", totalWired)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF8E24AA),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var startAngle = -90f
                        wiredByName.forEachIndexed { index, entry ->
                            val sweepAngle = (entry.second.toFloat() / totalWired.toFloat()) * 360f
                            drawArc(
                                color = colors[index % colors.size],
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = true
                            )
                            startAngle += sweepAngle
                        }
                    }
                    Surface(
                        modifier = Modifier.size(90.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "TOTAL WIRED",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = String.format(Locale.US, "₱%,.0f", totalWired),
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF8E24AA)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(20.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    wiredByName.take(5).forEachIndexed { index, entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(colors[index % colors.size], CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    entry.first, 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(Locale.US, "₱%,.0f", entry.second),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WiredTransferChart(transactions: List<Transaction>) {
    val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
    
    val last7Days = (0..6).map { i ->
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -i)
        dateFormat.format(cal.time)
    }.reversed()

    val transferMap = transactions
        .filter { it.type == TransactionType.WIRED_TRANSFER }
        .groupBy { dateFormat.format(it.timestamp.toDate()) }
        .mapValues { it.value.sumOf { t -> t.amount } }

    val transfersByDay = last7Days.map { day ->
        day to (transferMap[day] ?: 0.0)
    }

    if (transfersByDay.all { it.second == 0.0 }) return

    val maxAmount = transfersByDay.maxOf { it.second }.coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "WIRED TRANSFER TREND", 
                fontWeight = FontWeight.Black, 
                color = Color(0xFF8E24AA),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                transfersByDay.forEach { (day, amount) ->
                    val barHeight = (amount / maxAmount * 120).dp
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (amount > 0) {
                            Text(
                                text = String.format(Locale.US, "₱%.0f", amount),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF8E24AA)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(barHeight.coerceAtLeast(4.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF8E24AA),
                                            Color(0xFF8E24AA).copy(alpha = 0.6f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = day, 
                            style = MaterialTheme.typography.labelSmall, 
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

fun generateCsv(transactions: List<Transaction>, debts: List<Debt>): String {
    val sb = StringBuilder()
    sb.append("Type,Category,Description,Source,Destination,Amount,Date\n")
    transactions.forEach {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it.timestamp.toDate())
        sb.append("${it.type},${it.category},${it.description},${it.source},${it.destinationSource},${it.amount},$date\n")
    }
    sb.append("\nDebt Name,Original Principal,Total Repayment (P+I),Remaining,Interest Rate,Status\n")
    debts.forEach {
        val status = if (it.isPaid) "PAID" else "ACTIVE"
        sb.append("${it.name},${it.principalAmount},${it.totalAmount},${it.remainingAmount},${it.interestRate}%,$status\n")
    }
    return sb.toString()
}

fun shareCsv(context: android.content.Context, csvData: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, csvData)
        type = "text/csv"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Export Financial Data")
    context.startActivity(shareIntent)
}

@Composable
fun AddDebtForm(
    userId: String,
    onDismiss: () -> Unit,
    onAdd: (Debt) -> Unit,
    viewModel: FinanceViewModel = viewModel()
) {
    var name by remember { mutableStateOf("") }
    var totalAmount by remember { mutableStateOf("") }
    var interestRate by remember { mutableStateOf("") }
    var installmentAmount by remember { mutableStateOf("") }
    var totalMonths by remember { mutableStateOf("12") }
    var isAnalyzing by remember { mutableStateOf(false) }

    // Enhanced logic for dual-way calculation (Accuracy)
    val principal = totalAmount.toDoubleOrNull() ?: 0.0
    val months = totalMonths.toIntOrNull() ?: 0
    val rate = interestRate.toDoubleOrNull() ?: 0.0
    val monthly = installmentAmount.toDoubleOrNull() ?: 0.0

    // Strictly following your formula for exact interest calculation
    val summaryData = remember(totalAmount, totalMonths, interestRate, installmentAmount) {
        if (principal > 0 && months > 0) {
            if (monthly > 0) {
                // 1. installment * months to pay = total balance
                val totalRepayment = monthly * months
                // 2. total balance - principal = total amount interest to pay
                val totalInterest = totalRepayment - principal
                // 3. (total amount interest to pay / principal) * 100 = interest %
                val calculatedRate = (totalInterest / principal) * 100
                Triple(totalRepayment, totalInterest, String.format(Locale.US, "%.2f", calculatedRate))
            } else if (rate > 0) {
                // reverse logic if only rate is known
                val totalInterest = principal * (rate / 100)
                val totalRepayment = principal + totalInterest
                val calculatedMonthly = totalRepayment / months
                Triple(totalRepayment, totalInterest, String.format(Locale.US, "%.2f", calculatedMonthly))
            } else {
                null
            }
        } else {
            null
        }
    }

    // Apply auto-calculated values to the fields
    LaunchedEffect(summaryData) {
        summaryData?.let { (_, _, calculatedValue) ->
            if (monthly > 0 && (interestRate.isEmpty() || interestRate == "0.0")) {
                interestRate = calculatedValue
            } else if (rate > 0 && (installmentAmount.isEmpty() || installmentAmount == "0.0")) {
                installmentAmount = calculatedValue
            }
        }
    }
    
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            isAnalyzing = true
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, selectedUri)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, selectedUri)
            }
            
            viewModel.extractDebtFromImage(bitmap) { extractedDebt ->
                isAnalyzing = false
                extractedDebt?.let { d ->
                    name = d.name
                    totalAmount = d.totalAmount.toString()
                    interestRate = d.interestRate.toString()
                    installmentAmount = d.installmentAmount.toString()
                    totalMonths = d.totalMonths.toString()
                }
            }
        }
    }

    Column(
        modifier = Modifier.padding(24.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Add New Debt", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            
            IconButton(
                onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !isAnalyzing
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Scan Screenshot", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        if (isAnalyzing) {
            Text("AI is analyzing your screenshot...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Debt Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = totalAmount,
                onValueChange = { totalAmount = it },
                label = { Text("Total Amount (₱)") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = interestRate,
                onValueChange = { interestRate = it },
                label = { Text("Interest %") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = installmentAmount,
                onValueChange = { installmentAmount = it },
                label = { Text("Installment (₱)") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = totalMonths,
                onValueChange = { totalMonths = it },
                label = { Text("Months") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        summaryData?.let { (totalRepayment, totalInterest, _) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Auto-Computed Summary", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Interest Rate (Accurate):", style = MaterialTheme.typography.bodySmall)
                        Text("${if (rate > 0) rate else interestRate}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Repayment:", style = MaterialTheme.typography.bodySmall)
                        Text(String.format(Locale.US, "₱%,.2f", totalRepayment), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Interest Amount:", style = MaterialTheme.typography.bodySmall)
                        Text(String.format(Locale.US, "₱%,.2f", totalInterest), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        
        Button(
            onClick = {
                val repaymentAmount = summaryData?.first ?: (installmentAmount.toDoubleOrNull()?.let { it * (totalMonths.toIntOrNull() ?: 12) } ?: (totalAmount.toDoubleOrNull() ?: 0.0))
                val debt = Debt(
                    userId = userId,
                    name = name,
                    totalAmount = repaymentAmount,
                    principalAmount = totalAmount.toDoubleOrNull() ?: 0.0,
                    remainingAmount = repaymentAmount,
                    interestRate = interestRate.toDoubleOrNull() ?: 0.0,
                    installmentAmount = installmentAmount.toDoubleOrNull() ?: 0.0,
                    totalMonths = totalMonths.toIntOrNull() ?: 12,
                    dueDate = Timestamp.now(),
                    startDate = Timestamp.now()
                )
                onAdd(debt)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Save Debt Account", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
