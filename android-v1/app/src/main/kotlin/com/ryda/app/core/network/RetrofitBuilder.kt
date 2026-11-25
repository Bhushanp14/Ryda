package com.ryda.app.core.network

import com.ryda.app.core.auth.TokenDataStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitBuilder {

    // 10.0.2.2 = Standard Android Emulator loopback
    // 127.0.0.1 = Physical Device forwarded via ADB reverse (adb reverse tcp:8000 tcp:8000)
    private const val BASE_URL_EMULATOR = "http://10.0.2.2:8000/api/v1/"
    private const val BASE_URL_DEVICE   = "http://127.0.0.1:8000/api/v1/"

    // Auto-detect: emulator uses 10.0.2.2 as its gateway; physical devices use 127.0.0.1 via ADB reverse
    private val BASE_URL: String get() {
        return try {
            val isEmulator = android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || "google_sdk" == android.os.Build.PRODUCT
                || (java.net.NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()).any { iface ->
                    iface.inetAddresses.toList().any { addr -> addr.hostAddress?.startsWith("10.0.2.") == true }
                }
            if (isEmulator) BASE_URL_EMULATOR else BASE_URL_DEVICE
        } catch (e: Exception) {
            BASE_URL_DEVICE
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun getServerOrigin(): String {
        val url = BASE_URL
        return try {
            val uri = java.net.URI(url)
            val scheme = uri.scheme ?: "http"
            val host = uri.host ?: "127.0.0.1"
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "$scheme://$host$port"
        } catch (_: Exception) {
            "http://127.0.0.1:8000"
        }
    }

    fun resolveMediaUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("content://") || path.startsWith("file://")) {
            return path
        }
        val origin = getServerOrigin()
        return if (path.startsWith("/")) {
            "$origin$path"
        } else {
            "$origin/$path"
        }
    }

    @Volatile private var instance: RydaApiService? = null

    fun getService(tokenDataStore: TokenDataStore): RydaApiService =
        instance ?: synchronized(this) {
            instance ?: build(tokenDataStore).also { instance = it }
        }

    private fun build(tokenDataStore: TokenDataStore): RydaApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenDataStore))
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RydaApiService::class.java)
    }
}
