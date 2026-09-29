package com.example.kasirkrj.api

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = "http://192.168.137.1:8000/api/"

    private var appContext: Context? = null

    // Dipanggil sekali dari Application/MainActivity supaya interceptor bisa baca token
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val instance: ApiService by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = appContext
                    ?.getSharedPreferences("auth", Context.MODE_PRIVATE)
                    ?.getString("token", null)

                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .apply {
                        if (token != null) addHeader("Authorization", "Bearer $token")
                    }
                    .build()

                chain.proceed(request)
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}