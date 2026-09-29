package com.example.kasirkrj.api

import com.google.gson.annotations.SerializedName

data class GoogleLoginRequest(
    @SerializedName("id_token")
    val idToken: String
)
