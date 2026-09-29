package com.example.kasirkrj

import android.content.Intent
import android.content.MutableContextWrapper
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.kasirkrj.api.ApiClient
import com.example.kasirkrj.api.GoogleLoginRequest
import com.example.kasirkrj.api.LoginRequest
import com.example.kasirkrj.api.LoginResponse
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Login KasirKRJ:
 * 1. Login manual (email + password) ke Laravel.
 * 2. Login Google (Credential Manager), ID Token dikirim ke Laravel.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "KASIR_KRJ"

        // Harus Web Client ID, bukan Android Client ID
        private const val WEB_CLIENT_ID =
            "830693118070-b08ghnlmu6orovskk27f868cnliies46.apps.googleusercontent.com"
    }

    private lateinit var credentialManager: CredentialManager

    // =========================================================
    // LIFECYCLE
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        credentialManager = CredentialManager.create(this)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoogleLogin = findViewById<Button>(R.id.btnGoogleLogin)

        // ---------------- LOGIN MANUAL ----------------

        btnLogin.setOnClickListener {

            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty()) {
                showInfoDialog("Data Belum Lengkap", "Email belum diisi.")
                etEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                showInfoDialog("Data Belum Lengkap", "Password belum diisi.")
                etPassword.requestFocus()
                return@setOnClickListener
            }

            showLoadingToast("Menghubungkan ke server...")

            Log.d(TAG, "LOGIN MANUAL")
            Log.d(TAG, "Email: $email")

            val request = LoginRequest(
                email = email,
                password = password
            )

            ApiClient.instance
                .login(request)
                .enqueue(object : Callback<LoginResponse> {

                    override fun onResponse(
                        call: Call<LoginResponse>,
                        response: Response<LoginResponse>
                    ) {
                        Log.d(TAG, "Manual login response")
                        Log.d(TAG, "HTTP Code: ${response.code()}")

                        if (response.isSuccessful) {

                            val loginData = response.body()
                            val user = loginData?.user
                            val role = user?.role

                            // BARU: simpan token supaya bisa dipakai halaman lain
                            saveToken(loginData?.token)

                            Log.d(TAG, "Manual login berhasil")
                            Log.d(TAG, "User: ${user?.name}")
                            Log.d(TAG, "Role: $role")

                            if (role == "admin" || role == "owner" || role == "developer") {
                                Log.d(TAG, "Role memiliki akses. Membuka Dashboard.")
                                openDashboard()
                            } else {
                                showErrorDialog(
                                    title = "Akses Ditolak",
                                    message =
                                        "Akun berhasil ditemukan, tetapi role akun ini tidak memiliki akses ke aplikasi.\n\n" +
                                                "Role akun: ${role ?: "Tidak diketahui"}\n\n" +
                                                "Akses hanya diperbolehkan untuk Admin, Owner, atau Developer."
                                )
                            }
                            return
                        }

                        val errorBody = response.errorBody()?.string()

                        Log.e(TAG, "========================================")
                        Log.e(TAG, "MANUAL LOGIN DITOLAK SERVER")
                        Log.e(TAG, "HTTP Code: ${response.code()}")
                        Log.e(TAG, "HTTP Message: ${response.message()}")
                        Log.e(TAG, "Response: $errorBody")
                        Log.e(TAG, "========================================")

                        showErrorDialog(
                            title = "Login Gagal",
                            message =
                                "Server menolak login.\n\n" +
                                        "Kode HTTP: ${response.code()}\n\n" +
                                        "Pesan server:\n${errorBody ?: "Tidak ada pesan dari server."}"
                        )
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        Log.e(TAG, "========================================")
                        Log.e(TAG, "KONEKSI LOGIN MANUAL GAGAL")
                        Log.e(TAG, "Exception: ${t.javaClass.name}")
                        Log.e(TAG, "Message: ${t.message}")
                        Log.e(TAG, "========================================", t)

                        showConnectionError(
                            title = "Tidak Bisa Terhubung",
                            message =
                                "Aplikasi tidak dapat terhubung ke server login.\n\n" +
                                        "Periksa:\n" +
                                        "• Koneksi internet\n" +
                                        "• Alamat API Laravel\n" +
                                        "• Server Laravel sedang aktif\n\n" +
                                        "Detail teknis:\n" +
                                        "${t.javaClass.simpleName}: ${t.message ?: "Tidak ada pesan"}"
                        )
                    }
                })
        }

        // ---------------- LOGIN GOOGLE ----------------

        btnGoogleLogin.setOnClickListener {
            Log.d(TAG, "========================================")
            Log.d(TAG, "TOMBOL LOGIN GOOGLE DITEKAN")
            Log.d(TAG, "========================================")

            processGoogleSignIn()
        }
    }

    // =========================================================
    // TOKEN & NAVIGATION
    // =========================================================

    /**
     * BARU: Menyimpan token API supaya bisa dibaca ApiClient di halaman lain.
     */
    private fun saveToken(token: String?) {
        if (token.isNullOrBlank()) {
            Log.e(TAG, "Token dari server kosong, tidak disimpan")
            return
        }

        getSharedPreferences("auth", MODE_PRIVATE)
            .edit()
            .putString("token", token)
            .apply()

        Log.d(TAG, "Token berhasil disimpan")
    }

    /**
     * Buka Dashboard lalu tutup MainActivity supaya tombol Back
     * tidak kembali ke halaman Login.
     */
    private fun openDashboard() {
        Log.d(TAG, "Membuka DashboardActivity")
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    // =========================================================
    // GOOGLE SIGN-IN
    // =========================================================

    private fun processGoogleSignIn() {

        lifecycleScope.launch {

            try {
                Log.d(TAG, "Memulai Google Sign-In")

                val signInWithGoogleOption =
                    GetSignInWithGoogleOption.Builder(
                        serverClientId = WEB_CLIENT_ID
                    ).build()

                Log.d(TAG, "GetSignInWithGoogleOption berhasil dibuat")

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                Log.d(TAG, "GetCredentialRequest berhasil dibuat")

                val mutableContext = MutableContextWrapper(this@MainActivity)

                Log.d(TAG, "Membuka Google Sign-In")

                val result = credentialManager.getCredential(
                    request = request,
                    context = mutableContext
                )

                Log.d(TAG, "Google mengembalikan credential")
                Log.d(TAG, "Credential type: ${result.credential.type}")

                val credential = result.credential

                if (
                    credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    Log.d(TAG, "Google ID Token Credential ditemukan")

                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleCredential.idToken

                    if (idToken.isBlank()) {
                        Log.e(TAG, "Google mengembalikan ID Token kosong")

                        showErrorDialog(
                            title = "Login Google Gagal",
                            message =
                                "Google berhasil merespons, tetapi ID Token tidak ditemukan.\n\n" +
                                        "Silakan coba login kembali."
                        )
                        return@launch
                    }

                    Log.d(TAG, "Google ID Token berhasil diperoleh")

                    // Jangan mencetak isi ID Token ke Logcat (sensitif)
                    Log.d(TAG, "Token diterima. Panjang token: ${idToken.length}")

                    sendGoogleTokenToBackend(idToken)

                } else {
                    Log.e(TAG, "Credential bukan Google ID Token")
                    Log.e(TAG, "Credential type: ${credential.type}")

                    showErrorDialog(
                        title = "Login Google Gagal",
                        message =
                            "Google mengembalikan credential yang tidak dikenali oleh aplikasi.\n\n" +
                                    "Credential Type:\n${credential.type}\n\n" +
                                    "Silakan coba login kembali."
                    )
                }

            } catch (e: GetCredentialCancellationException) {

                Log.e(TAG, "========================================")
                Log.e(TAG, "GOOGLE SIGN-IN CANCELED")
                Log.e(TAG, "Exception: ${e.javaClass.name}")
                Log.e(TAG, "Message: ${e.message}")
                Log.e(TAG, "========================================", e)

                showErrorDialog(
                    title = "Login Google Tidak Selesai",
                    message =
                        "Proses autentikasi Google tidak selesai dan ditutup oleh sistem.\n\n" +
                                "Jika Anda memang menutup layar Google, silakan coba lagi.\n\n" +
                                "Jika layar Google tertutup sendiri setelah memilih akun, kemungkinan terdapat masalah pada konfigurasi Google Sign-In atau Google Play Services.\n\n" +
                                "Kode teknis:\n" +
                                "GetCredentialCancellationException"
                )

            } catch (e: GetCredentialException) {

                Log.e(TAG, "========================================")
                Log.e(TAG, "GOOGLE CREDENTIAL ERROR")
                Log.e(TAG, "Exception: ${e.javaClass.name}")
                Log.e(TAG, "Message: ${e.message}")
                Log.e(TAG, "========================================", e)

                showErrorDialog(
                    title = "Login Google Gagal",
                    message =
                        "Aplikasi tidak berhasil mendapatkan akun Google.\n\n" +
                                "Kemungkinan penyebab:\n" +
                                "• Google Play Services bermasalah\n" +
                                "• Konfigurasi OAuth belum sesuai\n" +
                                "• Akun Google tidak dapat digunakan\n" +
                                "• Proses autentikasi Google mengalami gangguan\n\n" +
                                "Kode teknis:\n" +
                                "${e.javaClass.simpleName}\n\n" +
                                "Detail:\n" +
                                "${e.message ?: "Tidak ada detail tambahan."}"
                )

            } catch (e: Exception) {

                Log.e(TAG, "========================================")
                Log.e(TAG, "GOOGLE UNKNOWN ERROR")
                Log.e(TAG, "Exception: ${e.javaClass.name}")
                Log.e(TAG, "Message: ${e.message}")
                Log.e(TAG, "========================================", e)

                showErrorDialog(
                    title = "Login Google Gagal",
                    message =
                        "Terjadi kesalahan yang tidak terduga saat proses login Google.\n\n" +
                                "Silakan coba lagi.\n\n" +
                                "Kode teknis:\n" +
                                "${e.javaClass.simpleName}\n\n" +
                                "Detail:\n" +
                                "${e.message ?: "Tidak ada detail tambahan."}"
                )
            }
        }
    }

    // =========================================================
    // KIRIM GOOGLE TOKEN KE LARAVEL
    // =========================================================

    private fun sendGoogleTokenToBackend(idToken: String) {

        Log.d(TAG, "========================================")
        Log.d(TAG, "MENGIRIM GOOGLE ID TOKEN KE LARAVEL")
        Log.d(TAG, "========================================")

        val request = GoogleLoginRequest(idToken)

        ApiClient.instance
            .loginGoogle(request)
            .enqueue(object : Callback<LoginResponse> {

                override fun onResponse(
                    call: Call<LoginResponse>,
                    response: Response<LoginResponse>
                ) {
                    Log.d(TAG, "Laravel memberikan response")
                    Log.d(TAG, "HTTP Code: ${response.code()}")

                    if (response.isSuccessful) {

                        val user = response.body()?.user

                        // BARU: simpan token supaya bisa dipakai halaman lain
                        saveToken(response.body()?.token)

                        Log.d(TAG, "========================================")
                        Log.d(TAG, "GOOGLE LOGIN BERHASIL")
                        Log.d(TAG, "User: ${user?.name}")
                        Log.d(TAG, "Role: ${user?.role}")
                        Log.d(TAG, "========================================")

                        openDashboard()
                        return
                    }

                    val errorBody = response.errorBody()?.string()

                    Log.e(TAG, "========================================")
                    Log.e(TAG, "LARAVEL MENOLAK GOOGLE LOGIN")
                    Log.e(TAG, "HTTP Code: ${response.code()}")
                    Log.e(TAG, "HTTP Message: ${response.message()}")
                    Log.e(TAG, "Response: $errorBody")
                    Log.e(TAG, "========================================")

                    showErrorDialog(
                        title = "Login Google Ditolak Server",
                        message =
                            "Google berhasil melakukan autentikasi, tetapi server Laravel menolak login tersebut.\n\n" +
                                    "HTTP Code: ${response.code()}\n\n" +
                                    "Pesan dari Laravel:\n" +
                                    (errorBody ?: "Server tidak memberikan pesan error.")
                    )
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Log.e(TAG, "========================================")
                    Log.e(TAG, "KONEKSI GOOGLE -> LARAVEL GAGAL")
                    Log.e(TAG, "Exception: ${t.javaClass.name}")
                    Log.e(TAG, "Message: ${t.message}")
                    Log.e(TAG, "========================================", t)

                    showConnectionError(
                        title = "Server Tidak Terjangkau",
                        message =
                            "Google berhasil melakukan proses login, tetapi aplikasi tidak dapat menghubungi server Laravel.\n\n" +
                                    "Periksa:\n" +
                                    "• Koneksi internet\n" +
                                    "• URL API Laravel\n" +
                                    "• Server Laravel aktif\n" +
                                    "• Konfigurasi jaringan Android\n\n" +
                                    "Detail teknis:\n" +
                                    "${t.javaClass.simpleName}: ${t.message ?: "Tidak ada pesan"}"
                    )
                }
            })
    }

    // =========================================================
    // HELPER DIALOG & TOAST
    // =========================================================

    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showInfoDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showConnectionError(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Coba Lagi", null)
            .show()
    }

    private fun showLoadingToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}