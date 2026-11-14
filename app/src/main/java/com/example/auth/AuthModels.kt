package com.example.auth

data class AuthenticatedUser(
    val uid: String,
    val displayName: String,
    val email: String?,
    val photoUrl: String?,
    val isEmailVerified: Boolean
)

sealed interface AuthActionState {
    data object Idle : AuthActionState
    data object Loading : AuthActionState
    data class Error(val message: String) : AuthActionState
}
