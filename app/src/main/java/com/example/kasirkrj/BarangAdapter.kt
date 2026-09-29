package com.example.kasirkrj

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kasirkrj.api.Barang
import java.text.NumberFormat
import java.util.Locale

class BarangAdapter(
    private val onEdit: (Barang) -> Unit,
    private val onDelete: (Barang) -> Unit
) : ListAdapter<Barang, BarangAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Barang>() {
        override fun areItemsTheSame(a: Barang, b: Barang) = a.idBarang == b.idBarang
        override fun areContentsTheSame(a: Barang, b: Barang) = a == b
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val nama: TextView = v.findViewById(R.id.tvNama)
        val info: TextView = v.findViewById(R.id.tvInfo)
        val harga: TextView = v.findViewById(R.id.tvHarga)
        val status: TextView = v.findViewById(R.id.tvStatus)
        val edit: TextView = v.findViewById(R.id.btnEdit)
        val hapus: TextView = v.findViewById(R.id.btnHapus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_barang, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = getItem(position)
        val rupiah = NumberFormat
            .getCurrencyInstance(Locale("id", "ID"))
            .format(b.harga)

        holder.nama.text = b.nama
        holder.info.text = "${b.jenis} • per ${b.satuan}"
        holder.harga.text = rupiah
        holder.status.text = b.status.replaceFirstChar { it.uppercase() }
        holder.edit.setOnClickListener { onEdit(b) }
        holder.hapus.setOnClickListener { onDelete(b) }
    }
}