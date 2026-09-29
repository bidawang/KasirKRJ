package com.example.kasirkrj.api

import com.google.gson.annotations.SerializedName

data class Barang(
    @SerializedName("id_barang") val idBarang: Int,
    val nama: String,
    val satuan: String,
    val jenis: String,
    val harga: Double,
    val deskripsi: String?,
    val status: String
)

data class BarangRequest(
    val nama: String,
    val satuan: String,
    val jenis: String,
    val harga: Double,
    val deskripsi: String?,
    val status: String
)

data class ApiResponse<T>(
    val message: String?,
    val data: T?
)

data class Paginated<T>(
    @SerializedName("current_page") val currentPage: Int,
    @SerializedName("last_page") val lastPage: Int,
    val data: List<T>
)