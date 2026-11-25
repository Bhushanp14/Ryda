package com.ryda.app.core.auth

import android.content.Context
import com.ryda.app.core.network.LoginRequest
import com.ryda.app.core.network.RegisterRequest
import com.ryda.app.core.network.RetrofitBuilder

sealed class AuthResult {
    data class Success(val username: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthRepository(private val context: Context) {

    companion object {
        @Volatile private var instance: AuthRepository? = null
        fun getInstance(context: Context): AuthRepository =
            instance ?: synchronized(this) {
                instance ?: AuthRepository(context.applicationContext).also { instance = it }
            }
    }

    private val dataStore = TokenDataStore.getInstance(context)
    private val api get() = RetrofitBuilder.getService(dataStore)

    suspend fun register(username: String, email: String, password: String): AuthResult {
        return try {
            val response = api.register(RegisterRequest(username, email, password))
            if (response.isSuccessful) {
                val token = response.body()?.token ?: return AuthResult.Error("Empty token")
                dataStore.saveToken(token, username)
                AuthResult.Success(username)
            } else {
                val body = response.errorBody()?.string() ?: "Registration failed"
                AuthResult.Error(parseError(body))
            }
        } catch (e: Exception) {
            AuthResult.Error("Network error: ${e.message}")
        }
    }

    suspend fun login(username: String, password: String): AuthResult {
        return try {
            val response = api.login(LoginRequest(username, password))
            if (response.isSuccessful) {
                val token = response.body()?.token ?: return AuthResult.Error("Empty token")
                dataStore.saveToken(token, username)
                AuthResult.Success(username)
            } else {
                val body = response.errorBody()?.string() ?: "Login failed"
                AuthResult.Error(parseError(body))
            }
        } catch (e: Exception) {
            AuthResult.Error("Network error: ${e.message}")
        }
    }

    suspend fun logout() {
        try { api.logout() } catch (_: Exception) {}
        dataStore.clearToken()
    }

    suspend fun deleteAccount(): Boolean {
        return try {
            val response = api.deleteAccount()
            dataStore.clearToken()
            response.isSuccessful
        } catch (e: Exception) {
            dataStore.clearToken()
            false
        }
    }

    private fun parseError(raw: String): String {
        // Basic extraction — avoids a full JSON dep at this layer
        return raw.replace(Regex("[{}\"\\\\'\\[\\]]"), "")
            .replace("non_field_errors:", "")
            .trim()
            .take(120)
    }
}
