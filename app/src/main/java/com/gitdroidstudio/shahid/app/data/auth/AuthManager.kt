package com.gitdroidstudio.shahid.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.gitdroidstudio.shahid.app.data.model.AccountInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages GitHub PAT, multiple accounts, and pinned repositories securely.
 */
class AuthManager(context: Context) {

    private val gson = Gson()

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
        context.getSharedPreferences("gitdroid_prefs_fallback", Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun saveUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username.trim()).apply()
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun saveAvatarUrl(avatarUrl: String) {
        prefs.edit().putString(KEY_AVATAR, avatarUrl.trim()).apply()
    }

    fun getAvatarUrl(): String? {
        return prefs.getString(KEY_AVATAR, null)
    }

    fun getAccounts(): List<AccountInfo> {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AccountInfo>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addOrUpdateAccount(account: AccountInfo) {
        val current = getAccounts().toMutableList()
        val index = current.indexOfFirst { it.username.equals(account.username, ignoreCase = true) }
        if (index >= 0) {
            current[index] = account
        } else {
            current.add(account)
        }
        prefs.edit()
            .putString(KEY_ACCOUNTS, gson.toJson(current))
            .putString(KEY_TOKEN, account.token)
            .putString(KEY_USERNAME, account.username)
            .putString(KEY_AVATAR, account.avatarUrl ?: "")
            .apply()
    }

    fun switchAccount(username: String): Boolean {
        val account = getAccounts().find { it.username.equals(username, ignoreCase = true) } ?: return false
        prefs.edit()
            .putString(KEY_TOKEN, account.token)
            .putString(KEY_USERNAME, account.username)
            .putString(KEY_AVATAR, account.avatarUrl ?: "")
            .apply()
        return true
    }

    fun removeAccount(username: String) {
        val current = getAccounts().toMutableList()
        current.removeAll { it.username.equals(username, ignoreCase = true) }
        val editor = prefs.edit().putString(KEY_ACCOUNTS, gson.toJson(current))

        if (getUsername().equals(username, ignoreCase = true)) {
            val next = current.firstOrNull()
            if (next != null) {
                editor.putString(KEY_TOKEN, next.token)
                    .putString(KEY_USERNAME, next.username)
                    .putString(KEY_AVATAR, next.avatarUrl ?: "")
            } else {
                editor.remove(KEY_TOKEN).remove(KEY_USERNAME).remove(KEY_AVATAR)
            }
        }
        editor.apply()
    }

    fun getPinnedRepos(): Set<String> {
        return prefs.getStringSet(KEY_PINNED_REPOS, emptySet()) ?: emptySet()
    }

    fun togglePinRepo(repoFullName: String): Boolean {
        val current = getPinnedRepos().toMutableSet()
        val wasPinned = current.contains(repoFullName)
        if (wasPinned) {
            current.remove(repoFullName)
        } else {
            current.add(repoFullName)
        }
        prefs.edit().putStringSet(KEY_PINNED_REPOS, current).apply()
        return !wasPinned
    }

    fun isRepoPinned(repoFullName: String): Boolean {
        return getPinnedRepos().contains(repoFullName)
    }

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrBlank()
    }

    fun logout() {
        val currentUser = getUsername()
        if (!currentUser.isNullOrBlank()) {
            removeAccount(currentUser)
        } else {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_USERNAME).remove(KEY_AVATAR).apply()
        }
    }

    companion object {
        private const val KEY_TOKEN = "github_token"
        private const val KEY_USERNAME = "github_username"
        private const val KEY_AVATAR = "github_avatar_url"
        private const val KEY_ACCOUNTS = "github_accounts_list"
        private const val KEY_PINNED_REPOS = "github_pinned_repos"
    }
}
