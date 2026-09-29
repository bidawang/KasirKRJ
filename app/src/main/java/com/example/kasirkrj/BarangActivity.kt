package com.example.kasirkrj

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kasirkrj.api.ApiClient
import com.example.kasirkrj.api.Barang
import com.example.kasirkrj.api.BarangRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

class BarangActivity : AppCompatActivity() {

    private lateinit var adapter: BarangAdapter
    private lateinit var rv: RecyclerView
    private lateinit var emptyState: View
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barang)

        // Wajib: supaya ApiClient bisa membaca token
        ApiClient.init(this)

        rv = findViewById(R.id.rvBarang)
        emptyState = findViewById(R.id.emptyState)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        adapter = BarangAdapter(
            onEdit = { showForm(it) },
            onDelete = { confirmDelete(it) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // Pencarian dengan jeda 400 ms supaya tidak memanggil API di setiap ketikan
        findViewById<EditText>(R.id.etSearch).doAfterTextChanged { text ->
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(400)
                load(text?.toString())
            }
        }

        findViewById<Button>(R.id.btnTambahBarang).setOnClickListener {
            showForm(null)
        }

        load()
    }

    // ---------- LOAD ----------

    private fun load(search: String? = null) {
        lifecycleScope.launch {
            try {
                val res = ApiClient.instance.getBarang(
                    search = search?.takeIf { it.isNotBlank() }
                )
                val list = res.data?.data.orEmpty()
                adapter.submitList(list)
                emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                rv.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            } catch (e: Exception) {
                toast(errorMessage(e))
            }
        }
    }

    // ---------- FORM TAMBAH / EDIT ----------

    private fun showForm(barang: Barang?) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_barang, null)
        val etNama = v.findViewById<EditText>(R.id.etNama)
        val etSatuan = v.findViewById<EditText>(R.id.etSatuan)
        val etJenis = v.findViewById<EditText>(R.id.etJenis)
        val etHarga = v.findViewById<EditText>(R.id.etHarga)
        val etDeskripsi = v.findViewById<EditText>(R.id.etDeskripsi)
        val spStatus = v.findViewById<Spinner>(R.id.spStatus)

        val statusList = listOf("aktif", "nonaktif")
        spStatus.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            statusList
        )

        // Kalau mode edit, isi form dengan data lama
        barang?.let {
            etNama.setText(it.nama)
            etSatuan.setText(it.satuan)
            etJenis.setText(it.jenis)
            etHarga.setText(it.harga.toLong().toString())
            etDeskripsi.setText(it.deskripsi)
            spStatus.setSelection(statusList.indexOf(it.status).coerceAtLeast(0))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (barang == null) "Tambah Barang" else "Edit Barang")
            .setView(v)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null) // di-override di bawah
            .create()

        // Di-override supaya dialog tidak tertutup saat input belum valid
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nama = etNama.text.toString().trim()
                val satuan = etSatuan.text.toString().trim()
                val jenis = etJenis.text.toString().trim()
                val harga = etHarga.text.toString().toDoubleOrNull()

                if (nama.isEmpty() || satuan.isEmpty() || jenis.isEmpty() || harga == null) {
                    toast("Nama, satuan, jenis, dan harga wajib diisi")
                    return@setOnClickListener
                }

                val body = BarangRequest(
                    nama = nama,
                    satuan = satuan,
                    jenis = jenis,
                    harga = harga,
                    deskripsi = etDeskripsi.text.toString().trim().ifEmpty { null },
                    status = spStatus.selectedItem as String
                )
                save(barang?.idBarang, body) { dialog.dismiss() }
            }
        }
        dialog.show()
    }

    // ---------- SIMPAN ----------

    private fun save(id: Int?, body: BarangRequest, onSuccess: () -> Unit) {
        lifecycleScope.launch {
            try {
                val res = if (id == null) {
                    ApiClient.instance.createBarang(body)
                } else {
                    ApiClient.instance.updateBarang(id, body)
                }
                toast(res.message ?: "Berhasil")
                onSuccess()
                load()
            } catch (e: Exception) {
                toast(errorMessage(e))
            }
        }
    }

    // ---------- HAPUS ----------

    private fun confirmDelete(b: Barang) {
        AlertDialog.Builder(this)
            .setTitle("Hapus barang?")
            .setMessage("\"${b.nama}\" akan dihapus.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val res = ApiClient.instance.deleteBarang(b.idBarang)
                        toast(res.message ?: "Terhapus")
                        load()
                    } catch (e: Exception) {
                        toast(errorMessage(e))
                    }
                }
            }
            .show()
    }

    // ---------- HELPER ----------

    // Ambil pesan error dari response Laravel (401/403/422/500)
    private fun errorMessage(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string()
            return try {
                val json = JSONObject(body ?: "")
                val errors = json.optJSONObject("errors")
                if (errors != null) {
                    val firstKey = errors.keys().next()
                    errors.getJSONArray(firstKey).getString(0)
                } else {
                    json.optString("message", "Error ${e.code()}")
                }
            } catch (_: Exception) {
                "Error ${e.code()}"
            }
        }
        return "Gagal terhubung ke server"
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}