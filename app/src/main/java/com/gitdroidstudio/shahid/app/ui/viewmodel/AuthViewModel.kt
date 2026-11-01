package com.gitdroidstudio.shahid.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitdroidstudio.shahid.app.data.api.GitHubApiService
import com.gitdroidstudio.shahid.app.data.auth.AuthManager
import com.gitdroidstudio.shahid.app.data.model.GitHubUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: GitHubUser) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(
    private val authManager: AuthManager,
    private val apiServiceFactory: (String) -> GitHubApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun loginWithToken(token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter a valid GitHub token")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val api = apiServiceFactory(cleanToken)
                val response = api.getCurrentUser()

                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    authManager.saveToken(cleanToken)
                    authManager.saveUsername(user.login)
                    user.avatarUrl?.let { authManager.saveAvatarUrl(it) }
                    _uiState.value = AuthUiState.Success(user)
                } else {
                    val errorMsg = when (response.code()) {
                        401 -> "Bad credentials: Token is invalid or expired."
                        403 -> "Forbidden: Token lacks required scopes ('repo', 'workflow')."
                        else -> "Login failed (HTTP ${response.code()})"
                    }
                    _uiState.value = AuthUiState.Error(errorMsg)
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "Network connection error")
            }
        }
    }

    fun logout() {
        authManager.logout()
        _uiState.value = AuthUiState.Idle
    }
}
