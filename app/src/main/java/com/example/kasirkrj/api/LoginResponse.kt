package com.example.kasirkrj.api

data class LoginResponse(
    val token: String?,
    val message: String?,
    val user: UserData?
)

data class UserData(
    val id: Int,
    val name: String,
    val email: String,
    val role: String // Penting untuk cek apakah user ini admin atau owner
)