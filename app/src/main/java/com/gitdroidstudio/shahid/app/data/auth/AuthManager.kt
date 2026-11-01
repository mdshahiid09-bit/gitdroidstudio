package com.gitdroidstudio.shahid.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages GitHub PAT and authentication tokens securely using Android Keystore.
 */
class AuthManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "gitdroid_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for devices/emulators with keystore compatibility quirks
        context.getSharedPreferences("gitdroid_prefs_fallback", Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun saveUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun saveAvatarUrl(avatarUrl: String) {
        prefs.edit().putString(KEY_AVATAR, avatarUrl).apply()
    }

    fun getAvatarUrl(): String? {
        return prefs.getString(KEY_AVATAR, null)
    }

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrBlank()
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "github_token"
        private const val KEY_USERNAME = "github_username"
        private const val KEY_AVATAR = "github_avatar_url"
    }
}
