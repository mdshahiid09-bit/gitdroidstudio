package com.gitdroidstudio.shahid.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitdroidstudio.shahid.app.data.api.GitHubApiService
import com.gitdroidstudio.shahid.app.data.model.CreateRepoRequest
import com.gitdroidstudio.shahid.app.data.model.FileContentUpdate
import com.gitdroidstudio.shahid.app.data.model.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Base64

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val allRepos: List<Repository>,
        val filteredRepos: List<Repository>,
        val searchQuery: String = ""
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(
    private val apiService: GitHubApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var cachedRepos = listOf<Repository>()

    init {
        loadRepositories()
    }

    fun loadRepositories() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val response = apiService.getUserRepositories()
                if (response.isSuccessful && response.body() != null) {
                    cachedRepos = response.body()!!
                    _uiState.value = HomeUiState.Success(
                        allRepos = cachedRepos,
                        filteredRepos = cachedRepos
                    )
                } else {
                    _uiState.value = HomeUiState.Error("Failed to fetch repositories (HTTP ${response.code()})")
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.localizedMessage ?: "Failed to connect to GitHub")
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success) {
            val filtered = if (query.isBlank()) {
                cachedRepos
            } else {
                cachedRepos.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    (it.description?.contains(query, ignoreCase = true) == true)
                }
            }
            _uiState.value = currentState.copy(filteredRepos = filtered, searchQuery = query)
        }
    }

    fun createProject(
        name: String,
        description: String,
        isPrivate: Boolean,
        template: String,
        onSuccess: (Repository) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val createReq = CreateRepoRequest(
                    name = name.trim(),
                    description = description.ifBlank { "Built with GitDroid Studio" },
                    private = isPrivate,
                    autoInit = true
                )
                val response = apiService.createRepository(createReq)
                if (response.isSuccessful && response.body() != null) {
                    val newRepo = response.body()!!
                    setupTemplateFiles(newRepo.owner?.login ?: "", newRepo.name, template)
                    loadRepositories()
                    onSuccess(newRepo)
                } else {
                    onError("Failed to create repo: HTTP ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Network error creating project")
            }
        }
    }

    private suspend fun setupTemplateFiles(owner: String, repo: String, template: String) {
        try {
            val defaultWorkflow = """
                name: Build APK
                on:
                  push:
                    branches: [ main ]
                  workflow_dispatch:
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/setup-java@v4
                        with:
                          distribution: 'temurin'
                          java-version: '17'
                      - uses: gradle/actions/setup-gradle@v3
                      - run: gradle assembleDebug --stacktrace
                      - uses: actions/upload-artifact@v4
                        with:
                          name: app-debug-apk
                          path: app/build/outputs/apk/debug/*.apk
            """.trimIndent()

            val encodedWorkflow = Base64.getEncoder().encodeToString(defaultWorkflow.toByteArray())
            apiService.createOrUpdateFile(
                owner = owner,
                repo = repo,
                path = ".github/workflows/build.yml",
                body = FileContentUpdate(
                    message = "Add automated GitHub Actions build workflow",
                    content = encodedWorkflow
                )
            )
        } catch (_: Exception) {
            // Best effort template setup
        }
    }
}
