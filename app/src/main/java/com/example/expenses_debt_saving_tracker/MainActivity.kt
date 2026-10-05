package com.example.expenses_debt_saving_tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.expenses_debt_saving_tracker.ui.screens.DashboardScreen
import com.example.expenses_debt_saving_tracker.ui.screens.LoginScreen
import com.example.expenses_debt_saving_tracker.ui.theme.Expenses_debt_saving_trackerTheme
import com.example.expenses_debt_saving_tracker.viewmodel.AuthViewModel
import com.google.firebase.FirebaseApp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.expenses_debt_saving_tracker.util.UpdateManager

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        askNotificationPermission()
        enableEdgeToEdge()
        setContent {
            var updateUrl by remember { mutableStateOf<String?>(null) }
            
            LaunchedEffect(Unit) {
                UpdateManager.checkForUpdates(this@MainActivity) { url ->
                    updateUrl = url
                }
            }

            if (updateUrl != null) {
                AlertDialog(
                    onDismissRequest = { updateUrl = null },
                    title = { Text("Update Available") },
                    text = { Text("A new version of the app is available. Would you like to download and install it now?") },
                    confirmButton = {
                        TextButton(onClick = {
                            UpdateManager.downloadAndInstallApk(this@MainActivity, updateUrl!!)
                            updateUrl = null
                        }) {
                            Text("Update")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { updateUrl = null }) {
                            Text("Later")
                        }
                    }
                )
            }

            Expenses_debt_saving_trackerTheme {
                val authViewModel: AuthViewModel = viewModel()
                val user by authViewModel.user.collectAsState()

                if (user == null) {
                    LoginScreen(onLoginSuccess = { /* User state updates automatically */ })
                } else {
                    DashboardScreen(
                        userId = user!!.uid,
                        authViewModel = authViewModel,
                        onLogout = { authViewModel.signOut() }
                    )
                }
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
