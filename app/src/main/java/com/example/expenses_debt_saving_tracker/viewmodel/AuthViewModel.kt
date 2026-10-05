package com.example.expenses_debt_saving_tracker.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    
    private val _user = MutableStateFlow(auth.currentUser)
    val user: StateFlow<com.google.firebase.auth.FirebaseUser?> = _user

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun signIn(email: String, pass: String, onSuccess: () -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                _user.value = it.user
                updateToken()
                onSuccess()
            }
            .addOnFailureListener {
                _error.value = it.message
            }
    }

    fun signUp(email: String, pass: String, onSuccess: () -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                _user.value = it.user
                updateToken()
                onSuccess()
            }
            .addOnFailureListener {
                _error.value = it.message
            }
    }

    private fun updateToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                val userId = auth.currentUser?.uid ?: return@addOnSuccessListener
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(userId)
                    .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
            }
    }

    fun signOut() {
        auth.signOut()
        _user.value = null
    }

    fun deleteAccount(onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser
        user?.delete()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                _user.value = null
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }
}
