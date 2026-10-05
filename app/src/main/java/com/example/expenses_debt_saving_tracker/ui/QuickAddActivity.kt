package com.example.expenses_debt_saving_tracker.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.expenses_debt_saving_tracker.model.Transaction
import com.example.expenses_debt_saving_tracker.model.TransactionType
import com.example.expenses_debt_saving_tracker.repository.FinanceRepository
import com.example.expenses_debt_saving_tracker.ui.theme.Expenses_debt_saving_trackerTheme
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickAddActivity : ComponentActivity() {

    private val repository = FinanceRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            Expenses_debt_saving_trackerTheme {
                QuickAddDialog(
                    onDismiss = { finishAndRemoveTask() },
                    onSave = { transaction ->
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        if (currentUser == null) {
                            Toast.makeText(this, "Please log in to the app first", Toast.LENGTH_SHORT).show()
                            finishAndRemoveTask()
                            return@QuickAddDialog
                        }

                        val txWithUser = transaction.copy(userId = currentUser.uid)
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                repository.addTransaction(txWithUser)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@QuickAddActivity,
                                        "Transaction saved successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    finishAndRemoveTask()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@QuickAddActivity,
                                        "Error saving transaction: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    finishAndRemoveTask()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddDialog(
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("Cash") }
    var destinationSource by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }

    val allTypes = remember { TransactionType.entries }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Quick Add Transaction",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₱)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Payments, null) },
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                if (type != TransactionType.TRANSFER) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (e.g., Food, Deposit, Bills)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text(if (type == TransactionType.TRANSFER) "From Source (e.g. Bank)" else "Source (e.g. GCash, Bank, Cash)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                if (type == TransactionType.TRANSFER) {
                    OutlinedTextField(
                        value = destinationSource,
                        onValueChange = { destinationSource = it },
                        label = { Text("To Source (e.g. GCash)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Input, null) },
                        singleLine = true
                    )
                }

                Text(
                    text = "Transaction Type:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    allTypes.forEachIndexed { index, t ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = { type = t },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = allTypes.size)
                        ) {
                            Text(
                                text = t.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val parsedAmount = amount.toDoubleOrNull() ?: 0.0
                        if (parsedAmount <= 0) {
                            return@Button
                        }

                        val tx = Transaction(
                            amount = parsedAmount,
                            description = description.ifEmpty { "Quick ${type.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }}" },
                            category = if (type == TransactionType.TRANSFER) "Transfer" else category.ifEmpty { "General" },
                            source = source,
                            destinationSource = destinationSource,
                            type = type,
                            timestamp = Timestamp.now()
                        )
                        onSave(tx)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Transaction", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
