package com.example.kasirkrj

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent

class DashboardActivity : AppCompatActivity() {

    private lateinit var tvPageTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_dashboard)

        tvPageTitle = findViewById(R.id.tvPageTitle)

        setupNavigation()
    }

    private fun setupNavigation() {
        val navDashboard = findViewById<TextView>(R.id.navDashboard)
        val navTransaksi = findViewById<TextView>(R.id.navTransaksi)
        val navBarang = findViewById<TextView>(R.id.navBarang)
        val navAkun = findViewById<TextView>(R.id.navAkun)

        navDashboard.setOnClickListener {
            showPage("Dashboard")
        }

        navTransaksi.setOnClickListener {
            showPage("Transaksi")
        }

        navBarang.setOnClickListener {
            startActivity(
                Intent(this, BarangActivity::class.java)
            )
        }

        navAkun.setOnClickListener {
            showPage("Akun")
        }
    }

    private fun showPage(title: String) {
        tvPageTitle.text = title
    }
}