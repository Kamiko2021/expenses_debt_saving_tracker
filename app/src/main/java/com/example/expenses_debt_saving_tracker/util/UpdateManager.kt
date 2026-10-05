package com.example.expenses_debt_saving_tracker.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.expenses_debt_saving_tracker.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.io.File

object UpdateManager {
    fun checkForUpdates(context: Context, onUpdateAvailable: (String) -> Unit) {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val latestVersionCode = remoteConfig.getLong("latest_version_code")
                val updateUrl = remoteConfig.getString("update_url")
                
                if (latestVersionCode > BuildConfig.VERSION_CODE && updateUrl.isNotEmpty()) {
                    onUpdateAvailable(updateUrl)
                }
            }
        }
    }

    fun downloadAndInstallApk(context: Context, url: String) {
        val destination = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/update.apk"
        val file = File(destination)
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading Update")
            .setDescription("Smart Financial Manager is updating...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(file))

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(ctx, file)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
