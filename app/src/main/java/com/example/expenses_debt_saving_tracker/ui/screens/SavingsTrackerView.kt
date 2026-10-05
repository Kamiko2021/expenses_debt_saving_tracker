package com.example.expenses_debt_saving_tracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expenses_debt_saving_tracker.model.SavingsAccount
import com.example.expenses_debt_saving_tracker.viewmodel.FinanceViewModel
import java.util.Locale

@Composable
fun SavingsTrackerView(
    userId: String,
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val savingsAccounts by viewModel.savingsAccounts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountForDeposit by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountForWithdraw by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToDelete by remember { mutableStateOf<SavingsAccount?>(null) }

    val totalSaved = savingsAccounts.sumOf { it.currentBalance }
    val totalTarget = savingsAccounts.sumOf { it.targetAmount }
    val overallProgress = if (totalTarget > 0) (totalSaved / totalTarget).toFloat().coerceIn(0f, 1f) else 0f

    if (showAddDialog || accountToEdit != null) {
        AddOrEditSavingsAccountDialog(
            userId = userId,
            initialAccount = accountToEdit,
            onDismiss = {
                showAddDialog = false
                accountToEdit = null
            },
            onSave = { account ->
                if (account.id.isEmpty()) {
                    viewModel.addSavingsAccount(account)
                } else {
                    viewModel.updateSavingsAccount(account)
                }
                showAddDialog = false
                accountToEdit = null
            }
        )
    }

    if (accountForDeposit != null) {
        DepositSavingsDialog(
            account = accountForDeposit!!,
            onDismiss = { accountForDeposit = null },
            onDeposit = { account, amount, source ->
                viewModel.depositToSavingsAccount(account, amount, source)
                accountForDeposit = null
            }
        )
    }

    if (accountForWithdraw != null) {
        WithdrawSavingsDialog(
            account = accountForWithdraw!!,
            onDismiss = { accountForWithdraw = null },
            onWithdraw = { account, amount, destination ->
                viewModel.withdrawFromSavingsAccount(account, amount, destination)
                accountForWithdraw = null
            }
        )
    }

    if (accountToDelete != null) {
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Delete Savings Account", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${accountToDelete?.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        accountToDelete?.let { viewModel.deleteSavingsAccount(it.id, userId) }
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Streamlined Total Savings Overview Card (Removed extra Open Account button)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = Color(0xFF2D6A4F).copy(alpha = 0.15f)
                            ) {
                                Icon(
                                    Icons.Default.Savings,
                                    contentDescription = null,
                                    tint = Color(0xFF2D6A4F),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "TOTAL SAVINGS TRACKER",
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.labelMedium,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${savingsAccounts.size} Custom Account${if (savingsAccounts.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2D6A4F).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${(overallProgress * 100).toInt()}% Goal",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF2D6A4F),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text("TOTAL SAVED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Text(
                                text = String.format(Locale.US, "₱%,.2f", totalSaved),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF2D6A4F)
                            )
                        }

                        if (totalTarget > 0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("TOTAL TARGET", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                Text(
                                    text = String.format(Locale.US, "₱%,.2f", totalTarget),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (totalTarget > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { overallProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = Color(0xFF2D6A4F),
                            trackColor = Color(0xFF2D6A4F).copy(alpha = 0.15f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${(overallProgress * 100).toInt()}% of total savings target reached",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D6A4F)
                        )
                    }
                }
            }
        }

        // Section Action Bar: Header + "+ Open Account" Primary Action Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SAVINGS ACCOUNTS & GOALS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp
                )

                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open Account", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (savingsAccounts.isNotEmpty()) {
            // Chart 1: Savings Distribution Donut Chart
            item {
                SavingsDistributionChart(savingsAccounts)
            }

            // Chart 2: Savings Goals Breakdown Chart
            item {
                SavingsBreakdownChart(savingsAccounts)
            }
        }

        if (savingsAccounts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No Savings Accounts Yet",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Create customized savings accounts like 'Emergency Savings Account (₱30,000)' or 'Daily Saving (₱20,000)' to track your goals!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("+ Open Savings Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(savingsAccounts) { account ->
                SavingsAccountCard(
                    account = account,
                    onDeposit = { accountForDeposit = account },
                    onWithdraw = { accountForWithdraw = account },
                    onEdit = { accountToEdit = account },
                    onDelete = { accountToDelete = account }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun SavingsDistributionChart(savingsAccounts: List<SavingsAccount>) {
    val activeAccounts = savingsAccounts.filter { it.currentBalance > 0 }
    if (activeAccounts.isEmpty()) return

    val totalSaved = activeAccounts.sumOf { it.currentBalance }
    val colors = listOf(
        Color(0xFF2D6A4F), Color(0xFF52B788), Color(0xFF4895EF), 
        Color(0xFFD97706), Color(0xFF8E24AA), Color(0xFF1B263B),
        Color(0xFFE63946), Color(0xFF457B9D)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SAVINGS DISTRIBUTION", 
                    fontWeight = FontWeight.Black, 
                    color = Color(0xFF2D6A4F),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2D6A4F).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Total: ₱${String.format(Locale.US, "%,.2f", totalSaved)}",
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
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var startAngle = -90f
                        activeAccounts.forEachIndexed { index, acc ->
                            val sweepAngle = ((acc.currentBalance / totalSaved) * 360f).toFloat()
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
                        modifier = Modifier.size(85.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "SAVED",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = String.format(Locale.US, "₱%,.0f", totalSaved),
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF2D6A4F)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeAccounts.take(5).forEachIndexed { index, acc ->
                        val percent = ((acc.currentBalance / totalSaved) * 100).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(colors[index % colors.size], CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    acc.name, 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "₱${String.format(Locale.US, "%,.0f", acc.currentBalance)} ($percent%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
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
fun SavingsBreakdownChart(savingsAccounts: List<SavingsAccount>) {
    if (savingsAccounts.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "SAVINGS GOALS BREAKDOWN", 
                fontWeight = FontWeight.Black, 
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            savingsAccounts.forEachIndexed { index, account ->
                val progress = if (account.targetAmount > 0) (account.currentBalance / account.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                val remaining = (account.targetAmount - account.currentBalance).coerceAtLeast(0.0)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = account.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "₱${String.format(Locale.US, "%,.0f", account.currentBalance)} / ₱${String.format(Locale.US, "%,.0f", account.targetAmount)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF2D6A4F)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = Color(0xFF2D6A4F),
                        trackColor = Color(0xFF2D6A4F).copy(alpha = 0.12f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}% reached",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D6A4F)
                        )
                        if (remaining > 0 && account.targetAmount > 0) {
                            Text(
                                text = "₱${String.format(Locale.US, "%,.0f", remaining)} needed",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        } else if (account.targetAmount > 0) {
                            Text(
                                text = "Goal Reached! 🎉",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2D6A4F)
                            )
                        }
                    }
                }

                if (index < savingsAccounts.size - 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
fun SavingsAccountCard(
    account: SavingsAccount,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = if (account.targetAmount > 0) (account.currentBalance / account.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val isCompleted = account.targetAmount > 0 && account.currentBalance >= account.targetAmount

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = (if (isCompleted) Color(0xFF2D6A4F) else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f)
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Default.Verified else Icons.Default.Savings,
                            contentDescription = null,
                            tint = if (isCompleted) Color(0xFF2D6A4F) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = account.name,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (account.targetAmount > 0) {
                            Text(
                                text = "Target: ₱${String.format(Locale.US, "%,.2f", account.targetAmount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text("CURRENT SAVINGS", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text(
                        text = String.format(Locale.US, "₱%,.2f", account.currentBalance),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF2D6A4F)
                    )
                }

                if (isCompleted) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2D6A4F).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "GOAL REACHED!",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2D6A4F),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (account.targetAmount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = Color(0xFF2D6A4F),
                    trackColor = Color(0xFF2D6A4F).copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(progress * 100).toInt()}% completed",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D6A4F)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDeposit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Deposit", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onWithdraw,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = account.currentBalance > 0
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Withdraw", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddOrEditSavingsAccountDialog(
    userId: String,
    initialAccount: SavingsAccount?,
    onDismiss: () -> Unit,
    onSave: (SavingsAccount) -> Unit
) {
    var name by remember { mutableStateOf(initialAccount?.name ?: "") }
    var targetAmount by remember { mutableStateOf(initialAccount?.targetAmount?.takeIf { it > 0 }?.toString() ?: "") }
    var initialBalance by remember { mutableStateOf(initialAccount?.currentBalance?.takeIf { it > 0 }?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialAccount == null) "Open Savings Account" else "Edit Savings Account", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Customize your savings account (e.g. Emergency Savings Account: ₱30,000, Daily Saving: ₱20,000).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name (e.g. Emergency Savings Account)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = targetAmount,
                    onValueChange = { targetAmount = it },
                    label = { Text("Savings Target Goal (₱ e.g. 30000)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = initialBalance,
                    onValueChange = { initialBalance = it },
                    label = { Text("Current Balance / Initial Saved (₱)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val target = targetAmount.toDoubleOrNull() ?: 0.0
                        val current = initialBalance.toDoubleOrNull() ?: 0.0
                        onSave(
                            SavingsAccount(
                                id = initialAccount?.id ?: "",
                                userId = userId,
                                name = name.trim(),
                                targetAmount = target,
                                currentBalance = current
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (initialAccount == null) "Open Account" else "Save Changes", fontWeight = FontWeight.Bold)
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
fun DepositSavingsDialog(
    account: SavingsAccount,
    onDismiss: () -> Unit,
    onDeposit: (SavingsAccount, Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("Cash on Hand") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Deposit to ${account.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Deposit Amount (₱)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("From Source (e.g. GCash, Cash on Hand)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        onDeposit(account, amt, source.trim())
                    }
                },
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("Deposit", fontWeight = FontWeight.Bold)
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
fun WithdrawSavingsDialog(
    account: SavingsAccount,
    onDismiss: () -> Unit,
    onWithdraw: (SavingsAccount, Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("Cash on Hand") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Withdraw from ${account.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Available in account: ₱${String.format(Locale.US, "%,.2f", account.currentBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Withdrawal Amount (₱)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("To Destination (e.g. Bank, Cash on Hand)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0 && amt <= account.currentBalance) {
                        onWithdraw(account, amt, destination.trim())
                    }
                },
                enabled = (amount.toDoubleOrNull() ?: 0.0) in 0.01..account.currentBalance
            ) {
                Text("Withdraw", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
