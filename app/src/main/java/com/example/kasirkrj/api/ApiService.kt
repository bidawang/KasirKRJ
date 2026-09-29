package com.example.kasirkrj.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("auth/login")
    fun login(
        @Body request: LoginRequest
    ): Call<LoginResponse>

    @POST("auth/google")
    fun loginGoogle(
        @Body request: GoogleLoginRequest
    ): Call<LoginResponse>
}