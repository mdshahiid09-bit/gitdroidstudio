package com.gitdroidstudio.shahid.app.ui.viewmodel

import android.text.format.DateUtils
import android.util.Base64
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
import java.text.SimpleDateFormat
import java.util.*

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
                            _uiState.value = HomeUiState.Error("Failed to fetch repositories (HTTP ${response.code()})")
                            return@launch
                        }
                    }
                }

                cachedRepos = allFetched
                _uiState.value = HomeUiState.Success(
                    allRepos = cachedRepos,
                    filteredRepos = cachedRepos
                )
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
                            fullName = "$cleanOwner/$cleanRepo",
                            description = null,
                            private = false,
                            htmlUrl = "https://github.com/$cleanOwner/$cleanRepo",
                            defaultBranch = "main",
                            updatedAt = null,
                            owner = null
                        )
                        onSuccess(dummyRepo)
                    } else {
                        onError("Repository $cleanOwner/$cleanRepo not found or accessible.")
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
                        message = "Initialize $path for $template",
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
