package com.example.kasirkrj.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("auth/login")
    fun login(
        @Body request: LoginRequest
    ): Call<LoginResponse>

    @POST("auth/google")
    fun loginGoogle(
        @Body request: GoogleLoginRequest
    ): Call<LoginResponse>

    // ---------- BARANG ----------

    @GET("barang")
    suspend fun getBarang(
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1
    ): ApiResponse<Paginated<Barang>>

    @POST("barang")
    suspend fun createBarang(
        @Body body: BarangRequest
    ): ApiResponse<Barang>

    @PUT("barang/{id}")
    suspend fun updateBarang(
        @Path("id") id: Int,
        @Body body: BarangRequest
    ): ApiResponse<Barang>

    @DELETE("barang/{id}")
    suspend fun deleteBarang(
        @Path("id") id: Int
    ): ApiResponse<Unit>
}