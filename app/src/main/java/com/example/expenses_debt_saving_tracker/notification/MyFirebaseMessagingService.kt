package com.example.expenses_debt_saving_tracker.notification

import android.util.Log
import com.example.expenses_debt_saving_tracker.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM", "Message received: ${message.notification?.title}")
        
        message.notification?.let {
            NotificationHelper.showNotification(this, it.title ?: "Debt Reminder", it.body ?: "You have a payment due soon!")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        saveTokenToFirestore(token)
    }

    private fun saveTokenToFirestore(token: String) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid ?: return // Only save if user is logged in
        
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("users").document(userId)
            .update("fcmToken", token)
            .addOnFailureListener {
                // If document doesn't exist, create it
                db.collection("users").document(userId)
                    .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
            }
    }
}
