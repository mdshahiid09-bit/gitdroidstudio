package com.gitdroidstudio.shahid.app.ui.viewmodel

import android.text.format.DateUtils
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitdroidstudio.shahid.app.data.api.GitHubApiService
import com.gitdroidstudio.shahid.app.data.auth.AuthManager
import com.gitdroidstudio.shahid.app.data.model.CreateRepoRequest
import com.gitdroidstudio.shahid.app.data.model.FileContentUpdate
import com.gitdroidstudio.shahid.app.data.model.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class RepoSort {
    UPDATED,
    NAME
}

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
    private val apiService: GitHubApiService,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _sortOption = MutableStateFlow(RepoSort.UPDATED)
    val sortOption: StateFlow<RepoSort> = _sortOption.asStateFlow()

    private val _pinnedRepos = MutableStateFlow<Set<String>>(emptySet())
    val pinnedRepos: StateFlow<Set<String>> = _pinnedRepos.asStateFlow()

    private var cachedRepos = listOf<Repository>()

    init {
        _pinnedRepos.value = authManager.getPinnedRepos()
        loadRepositories()
    }

    /**
     * Loads all user repositories, paginating until all repositories are retrieved.
     */
    fun loadRepositories() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val allFetched = mutableListOf<Repository>()
                var page = 1
                var hasMore = true

                while (hasMore && page <= 10) { // Safety cap of 10 pages (up to 1,000 repos)
                    val response = apiService.getUserRepositories(page = page, perPage = 100)
                    if (response.isSuccessful && response.body() != null) {
                        val list = response.body()!!
                        allFetched.addAll(list)
                        if (list.size < 100) {
                            hasMore = false
                        } else {
                            page++
                        }
                    } else {
                        hasMore = false
                        if (allFetched.isEmpty()) {
                            _uiState.value = HomeUiState.Error("Failed to fetch repositories (HTTP ${'$'}{response.code()})")
                            return@launch
                        }
                    }
                }

                cachedRepos = allFetched
                applySortAndFilter(searchQuery = (_uiState.value as? HomeUiState.Success)?.searchQuery ?: "")
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.localizedMessage ?: "Failed to connect to GitHub")
            }
        }
    }

    fun toggleSort() {
        _sortOption.value = if (_sortOption.value == RepoSort.UPDATED) RepoSort.NAME else RepoSort.UPDATED
        val currentQuery = (_uiState.value as? HomeUiState.Success)?.searchQuery ?: ""
        applySortAndFilter(currentQuery)
    }

    fun togglePin(repoFullName: String) {
        authManager.togglePinRepo(repoFullName)
        _pinnedRepos.value = authManager.getPinnedRepos()
        val currentQuery = (_uiState.value as? HomeUiState.Success)?.searchQuery ?: ""
        applySortAndFilter(currentQuery)
    }

    fun isRepoPinned(repoFullName: String): Boolean {
        return _pinnedRepos.value.contains(repoFullName)
    }

    fun onSearchQueryChanged(query: String) {
        applySortAndFilter(query)
    }

    private fun applySortAndFilter(searchQuery: String) {
        val filtered = if (searchQuery.isBlank()) {
            cachedRepos
        } else {
            cachedRepos.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                (it.description?.contains(searchQuery, ignoreCase = true) == true)
            }
        }

        val pinned = _pinnedRepos.value
        val sorted = filtered.sortedWith(
            compareBy<Repository> { repo ->
                // Pinned repos first: if pinned, return 0; else 1
                if (pinned.contains(repo.fullName)) 0 else 1
            }.thenComparator { a, b ->
                when (_sortOption.value) {
                    RepoSort.UPDATED -> {
                        // Newest date first
                        (b.updatedAt ?: "").compareTo(a.updatedAt ?: "")
                    }
                    RepoSort.NAME -> {
                        a.name.lowercase().compareTo(b.name.lowercase())
                    }
                }
            }
        )

        _uiState.value = HomeUiState.Success(
            allRepos = cachedRepos,
            filteredRepos = sorted,
            searchQuery = searchQuery
        )
    }

    fun renameRepo(
        owner: String,
        oldName: String,
        newName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val cleanNewName = newName.trim()
                val response = apiService.updateRepository(
                    owner = owner,
                    repo = oldName,
                    body = mapOf("name" to cleanNewName)
                )
                if (response.isSuccessful) {
                    loadRepositories()
                    onSuccess()
                } else {
                    onError("Failed to rename repository (HTTP ${'$'}{response.code()}: ${'$'}{response.message()})")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to rename repository")
            }
        }
    }

    fun changeVisibility(
        owner: String,
        repo: String,
        isPrivate: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = apiService.updateRepository(
                    owner = owner,
                    repo = repo,
                    body = mapOf("private" to isPrivate)
                )
                if (response.isSuccessful) {
                    loadRepositories()
                    onSuccess()
                } else {
                    onError("Failed to change visibility (HTTP ${'$'}{response.code()}: ${'$'}{response.message()})")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to change visibility")
            }
        }
    }

    fun deleteRepo(
        owner: String,
        repo: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteRepository(owner = owner, repo = repo)
                if (response.isSuccessful || response.code() == 204) {
                    loadRepositories()
                    onSuccess()
                } else if (response.code() == 403) {
                    onError("Forbidden (HTTP 403): Personal Access Token requires 'delete_repo' scope to delete repositories on GitHub.")
                } else {
                    onError("Failed to delete repository (HTTP ${'$'}{response.code()}: ${'$'}{response.message()})")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to delete repository")
            }
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
                    onError("Failed to create repo: HTTP ${'$'}{response.code()} ${'$'}{response.message()}")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Network error creating project")
            }
        }
    }

    fun openSpecificRepo(
        owner: String,
        repoName: String,
        onSuccess: (Repository) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val cleanOwner = owner.trim()
                val cleanRepo = repoName.trim()
                val found = cachedRepos.find {
                    it.name.equals(cleanRepo, ignoreCase = true) &&
                    (it.owner?.login?.equals(cleanOwner, ignoreCase = true) == true)
                }
                if (found != null) {
                    onSuccess(found)
                } else {
                    // Try to fetch files to verify it exists
                    val test = apiService.getContents(cleanOwner, cleanRepo, "")
                    if (test.isSuccessful) {
                        val dummyRepo = Repository(
                            id = System.currentTimeMillis(),
                            name = cleanRepo,
                            fullName = "${'$'}cleanOwner/${'$'}cleanRepo",
                            description = null,
                            private = false,
                            htmlUrl = "https://github.com/${'$'}cleanOwner/${'$'}cleanRepo",
                            defaultBranch = "main",
                            updatedAt = null,
                            owner = null
                        )
                        onSuccess(dummyRepo)
                    } else {
                        onError("Repository ${'$'}cleanOwner/${'$'}cleanRepo not found or accessible.")
                    }
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to open repository")
            }
        }
    }

    private suspend fun setupTemplateFiles(owner: String, repo: String, template: String) {
        try {
            val buildWorkflow = """
                name: Build APK
                on:
                  push:
                    branches: [main]
                  workflow_dispatch:
                permissions:
                  contents: read
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/setup-java@v4
                        with:
                          distribution: temurin
                          java-version: 17
                          cache: gradle
                      - run: chmod +x gradlew
                      - run: ./gradlew assembleDebug --stacktrace
                      - uses: actions/upload-artifact@v4
                        with:
                          name: app-debug
                          path: app/build/outputs/apk/debug/*.apk
                          retention-days: 7
            """.trimIndent()

            val filesToCreate = mutableMapOf<String, String>()
            filesToCreate[".github/workflows/build.yml"] = buildWorkflow

            when (template) {
                "Compose Activity" -> {
                    filesToCreate["app/src/main/java/com/example/app/MainActivity.kt"] = """
                        package com.example.app

                        import android.os.Bundle
                        import androidx.activity.ComponentActivity
                        import androidx.activity.compose.setContent
                        import androidx.compose.foundation.layout.fillMaxSize
                        import androidx.compose.material3.MaterialTheme
                        import androidx.compose.material3.Surface
                        import androidx.compose.material3.Text
                        import androidx.compose.ui.Modifier

                        class MainActivity : ComponentActivity() {
                            override fun onCreate(savedInstanceState: Bundle?) {
                                super.onCreate(savedInstanceState)
                                setContent {
                                    MaterialTheme {
                                        Surface(modifier = Modifier.fillMaxSize()) {
                                            Text("Hello from GitDroid Studio!")
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent()
                }
                "Basic Activity" -> {
                    filesToCreate["app/src/main/java/com/example/app/MainActivity.kt"] = """
                        package com.example.app

                        import android.os.Bundle
                        import androidx.appcompat.app.AppCompatActivity

                        class MainActivity : AppCompatActivity() {
                            override fun onCreate(savedInstanceState: Bundle?) {
                                super.onCreate(savedInstanceState)
                                setContentView(R.layout.activity_main)
                            }
                        }
                    """.trimIndent()
                    filesToCreate["app/src/main/res/layout/activity_main.xml"] = """
                        <?xml version="1.0" encoding="utf-8"?>
                        <androidx.constraintlayout.widget.ConstraintLayout
                            xmlns:android="http://schemas.android.com/apk/res/android"
                            xmlns:app="http://schemas.android.com/apk/res-auto"
                            android:layout_width="match_parent"
                            android:layout_height="match_parent">

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="Welcome to Basic Activity"
                                app:layout_constraintBottom_toBottomOf="parent"
                                app:layout_constraintEnd_toEndOf="parent"
                                app:layout_constraintStart_toStartOf="parent"
                                app:layout_constraintTop_toTopOf="parent" />

                        </androidx.constraintlayout.widget.ConstraintLayout>
                    """.trimIndent()
                }
                "Navigation Drawer" -> {
                    filesToCreate["app/src/main/java/com/example/app/MainActivity.kt"] = """
                        package com.example.app

                        import android.os.Bundle
                        import androidx.appcompat.app.AppCompatActivity

                        class MainActivity : AppCompatActivity() {
                            override fun onCreate(savedInstanceState: Bundle?) {
                                super.onCreate(savedInstanceState)
                                setContentView(R.layout.activity_main)
                            }
                        }
                    """.trimIndent()
                    filesToCreate["app/src/main/res/layout/activity_main.xml"] = """
                        <?xml version="1.0" encoding="utf-8"?>
                        <androidx.drawerlayout.widget.DrawerLayout
                            xmlns:android="http://schemas.android.com/apk/res/android"
                            android:id="@+id/drawer_layout"
                            android:layout_width="match_parent"
                            android:layout_height="match_parent">

                            <FrameLayout
                                android:id="@+id/content_frame"
                                android:layout_width="match_parent"
                                android:layout_height="match_parent" />

                        </androidx.drawerlayout.widget.DrawerLayout>
                    """.trimIndent()
                }
                else -> { // Empty Activity
                    filesToCreate["app/src/main/java/com/example/app/MainActivity.kt"] = """
                        package com.example.app

                        import android.os.Bundle
                        import android.app.Activity
                        import android.widget.TextView

                        class MainActivity : Activity() {
                            override fun onCreate(savedInstanceState: Bundle?) {
                                super.onCreate(savedInstanceState)
                                val tv = TextView(this)
                                tv.text = "Empty Activity by GitDroid Studio"
                                setContentView(tv)
                            }
                        }
                    """.trimIndent()
                }
            }

            for ((path, content) in filesToCreate) {
                val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
                apiService.createOrUpdateFile(
                    owner = owner,
                    repo = repo,
                    path = path,
                    body = FileContentUpdate(
                        message = "Initialize ${'$'}path for ${'$'}template",
                        content = encoded
                    )
                )
            }
        } catch (_: Exception) {
            // Best effort template creation
        }
    }

    companion object {
        fun formatRelativeTime(dateString: String?): String {
            if (dateString.isNullOrBlank()) return "recently"
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = format.parse(dateString) ?: return dateString.take(10)
                DateUtils.getRelativeTimeSpanString(
                    date.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()
            } catch (_: Exception) {
                dateString.take(10)
            }
        }
    }
}
