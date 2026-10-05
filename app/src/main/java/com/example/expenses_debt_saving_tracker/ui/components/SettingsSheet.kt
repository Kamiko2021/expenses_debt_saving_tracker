package com.example.expenses_debt_saving_tracker.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expenses_debt_saving_tracker.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    authViewModel: AuthViewModel,
    onClearData: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            title = { Text("Privacy Policy") },
            text = {
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(id = com.example.expenses_debt_saving_tracker.R.string.privacy_policy_content),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicy = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            title = { Text("Delete Account?") },
            text = { Text("This will permanently delete your account and all your financial data. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        authViewModel.deleteAccount { success ->
                            if (success) {
                                onDeleteAccount()
                            }
                        }
                        showDeleteAccountConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings & Privacy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        HorizontalDivider()

        SettingsItem(
            icon = Icons.Default.Person,
            title = "Account Information",
            subtitle = authViewModel.user.collectAsState().value?.email ?: "Not logged in"
        )

        var isFloatingEnabled by remember {
            mutableStateOf(Settings.canDrawOverlays(context))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AddCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Floating Quick-Add Bubble", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("Draw floating + button over apps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            Switch(
                checked = isFloatingEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (!Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            val serviceIntent = Intent(context, com.example.expenses_debt_saving_tracker.service.FloatingWidgetService::class.java)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            isFloatingEnabled = true
                        }
                    } else {
                        val serviceIntent = Intent(context, com.example.expenses_debt_saving_tracker.service.FloatingWidgetService::class.java)
                        context.stopService(serviceIntent)
                        isFloatingEnabled = false
                    }
                }
            )
        }

        SettingsItem(
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            title = "Privacy Policy",
            subtitle = "View our data usage policies",
            onClick = { showPrivacyPolicy = true }
        )

        SettingsItem(
            icon = Icons.Default.DeleteForever,
            title = "Delete Account",
            subtitle = "Permanently remove your data",
            color = MaterialTheme.colorScheme.error,
            onClick = { showDeleteAccountConfirm = true }
        )

        SettingsItem(
            icon = Icons.Default.DeleteSweep,
            title = "Clear Financial Data",
            subtitle = "Wipe all transactions and debts",
            color = MaterialTheme.colorScheme.error,
            onClick = onClearData
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = color)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.6f))
            }
        }
    }
}
