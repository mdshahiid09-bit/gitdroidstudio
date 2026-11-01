package com.gitdroidstudio.shahid.app.ui.viewmodel

import android.content.Context
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitdroidstudio.shahid.app.data.api.GitHubApiService
import com.gitdroidstudio.shahid.app.data.model.*
import com.gitdroidstudio.shahid.app.util.ApkInstaller
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    var sha: String?,
    val children: MutableList<FileNode> = mutableListOf(),
    var isExpanded: Boolean = false,
    var isLoading: Boolean = false
)

data class EditorTab(
    val file: GitHubFile,
    var content: String,
    var originalContent: String,
    var isModified: Boolean = false,
    var sha: String?,
    val undoStack: MutableList<String> = mutableListOf(),
    val redoStack: MutableList<String> = mutableListOf()
)

data class ConflictState(
    val file: GitHubFile,
    val localContent: String,
    val remoteContent: String,
    val remoteSha: String
)

enum class DiffType {
    ADDED,
    DELETED,
    UNCHANGED
}

data class DiffLine(
    val type: DiffType,
    val text: String,
    val lineNumber: Int? = null
)

class ProjectViewModel(
    val owner: String,
    val repo: String,
    val defaultBranch: String,
    private val apiService: GitHubApiService,
    private val authToken: String?
) : ViewModel() {

    private val _fileTree = MutableStateFlow<List<FileNode>>(emptyList())
    val fileTree: StateFlow<List<FileNode>> = _fileTree.asStateFlow()

    private val _openTabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val openTabs: StateFlow<List<EditorTab>> = _openTabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    val currentTab: EditorTab?
        get() = _openTabs.value.getOrNull(_activeTabIndex.value)

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastCommitSha = MutableStateFlow<String?>(null)
    val lastCommitSha: StateFlow<String?> = _lastCommitSha.asStateFlow()

    private val _buildStatus = MutableStateFlow(BuildStatus.IDLE)
    val buildStatus: StateFlow<BuildStatus> = _buildStatus.asStateFlow()

    private val _currentRunId = MutableStateFlow<Long?>(null)
    val currentRunId: StateFlow<Long?> = _currentRunId.asStateFlow()

    private val _buildLogs = MutableStateFlow("")
    val buildLogs: StateFlow<String> = _buildLogs.asStateFlow()

    private val _workflowSteps = MutableStateFlow<List<WorkflowStep>>(emptyList())
    val workflowSteps: StateFlow<List<WorkflowStep>> = _workflowSteps.asStateFlow()

    private val _canInstallApk = MutableStateFlow(false)
    val canInstallApk: StateFlow<Boolean> = _canInstallApk.asStateFlow()

    private val _latestExtractedApk = MutableStateFlow<File?>(null)
    val latestExtractedApk: StateFlow<File?> = _latestExtractedApk.asStateFlow()

    private val _branches = MutableStateFlow<List<Branch>>(emptyList())
    val branches: StateFlow<List<Branch>> = _branches.asStateFlow()

    private val _activeBranch = MutableStateFlow(defaultBranch)
    val activeBranch: StateFlow<String> = _activeBranch.asStateFlow()

    private val _pullRequests = MutableStateFlow<List<PullRequest>>(emptyList())
    val pullRequests: StateFlow<List<PullRequest>> = _pullRequests.asStateFlow()

    private val _conflictDialog = MutableStateFlow<ConflictState?>(null)
    val conflictDialog: StateFlow<ConflictState?> = _conflictDialog.asStateFlow()

    private val _searchResults = MutableStateFlow<List<CodeSearchItem>>(emptyList())
    val searchResults: StateFlow<List<CodeSearchItem>> = _searchResults.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _showMissingWorkflowDialog = MutableStateFlow(false)
    val showMissingWorkflowDialog: StateFlow<Boolean> = _showMissingWorkflowDialog.asStateFlow()

    private val _workflowDispatchError = MutableStateFlow<String?>(null)
    val workflowDispatchError: StateFlow<String?> = _workflowDispatchError.asStateFlow()

    private var autoSaveJob: Job? = null
    private var pollingJob: Job? = null

    private val standardKeywords = listOf(
        "package", "import", "class", "interface", "fun", "val", "var", "if", "else",
        "return", "for", "while", "when", "data", "object", "sealed", "private", "public",
        "protected", "override", "suspend", "companion", "true", "false", "null",
        "Modifier", "Composable", "Text", "Column", "Row", "Box", "Button", "Card"
    )

    init {
        loadFileTreeRecursive()
        loadBranches()
        loadPullRequests()
    }

    fun isAndroidProject(): Boolean {
        fun searchTree(nodes: List<FileNode>): Boolean {
            for (node in nodes) {
                if (!node.isDirectory) {
                    if (node.name == "build.gradle.kts" ||
                        node.name == "build.gradle" ||
                        node.name == "AndroidManifest.xml"
                    ) {
                        return true
                    }
                } else {
                    if (searchTree(node.children)) return true
                }
            }
            return false
        }
        return searchTree(_fileTree.value)
    }

    /**
     * Loads the entire repository file tree in one call using the Git Trees API.
     */
    fun loadFileTreeRecursive() {
        viewModelScope.launch {
            _statusMessage.value = "Fetching repository tree..."
            try {
                val response = apiService.getGitTree(owner, repo, _activeBranch.value, recursive = 1)
                if (response.isSuccessful && response.body() != null) {
                    val treeResponse = response.body()!!
                    val rootNodes = buildTreeHierarchy(treeResponse.tree)
                    _fileTree.value = rootNodes
                    _statusMessage.value = "Tree synced (${'$'}{treeResponse.tree.size} items)"
                } else {
                    loadContentsShallow("")
                }
            } catch (e: Exception) {
                _statusMessage.value = "Sync error: ${'$'}{e.message}"
                loadContentsShallow("")
            }
        }
    }

    private fun buildTreeHierarchy(items: List<GitTreeNode>): List<FileNode> {
        val rootMap = mutableMapOf<String, FileNode>()
        val allNodes = mutableMapOf<String, FileNode>()

        val sorted = items.sortedBy { it.path }
        for (item in sorted) {
            val isDir = item.type == "tree"
            val parts = item.path.split("/")
            val name = parts.last()
            val node = FileNode(name = name, path = item.path, isDirectory = isDir, sha = item.sha)
            allNodes[item.path] = node

            if (parts.size == 1) {
                rootMap[name] = node
            } else {
                val parentPath = parts.dropLast(1).joinToString("/")
                allNodes[parentPath]?.children?.add(node)
            }
        }

        return rootMap.values.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    private fun loadContentsShallow(dirPath: String) {
        viewModelScope.launch {
            try {
                val resp = apiService.getContents(owner, repo, dirPath, _activeBranch.value)
                if (resp.isSuccessful && resp.body() != null) {
                    val nodes = resp.body()!!.map {
                        FileNode(
                            name = it.name,
                            path = it.path,
                            isDirectory = it.type == "dir",
                            sha = it.sha
                        )
                    }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    if (dirPath.isEmpty()) {
                        _fileTree.value = nodes
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun toggleFolder(node: FileNode) {
        if (!node.isDirectory) return
        node.isExpanded = !node.isExpanded
        _fileTree.value = _fileTree.value.toList()
    }

    fun openFile(node: FileNode) {
        if (node.isDirectory) return

        val existingIndex = _openTabs.value.indexOfFirst { it.file.path == node.path }
        if (existingIndex >= 0) {
            _activeTabIndex.value = existingIndex
            return
        }

        viewModelScope.launch {
            _statusMessage.value = "Opening ${'$'}{node.name}..."
            try {
                val resp = apiService.getSingleFile(owner, repo, node.path, _activeBranch.value)
                if (resp.isSuccessful && resp.body() != null) {
                    val file = resp.body()!!
                    val decoded = if (file.content != null) {
                        try {
                            val cleanBase64 = file.content.replace("\n", "").replace("\r", "")
                            String(Base64.decode(cleanBase64, Base64.DEFAULT))
                        } catch (_: Exception) {
                            file.content
                        }
                    } else ""

                    val newTab = EditorTab(
                        file = file,
                        content = decoded,
                        originalContent = decoded,
                        isModified = false,
                        sha = file.sha
                    )
                    _openTabs.value = _openTabs.value + newTab
                    _activeTabIndex.value = _openTabs.value.size - 1
                    _statusMessage.value = "Editing ${'$'}{file.name}"
                    updateSuggestions(decoded)
                } else {
                    _statusMessage.value = "Error opening file (HTTP ${'$'}{resp.code()})"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${'$'}{e.localizedMessage}"
            }
        }
    }

    fun selectTab(index: Int) {
        if (index in _openTabs.value.indices) {
            _activeTabIndex.value = index
            currentTab?.let { updateSuggestions(it.content) }
        }
    }

    fun closeTab(index: Int) {
        if (index in _openTabs.value.indices) {
            val updated = _openTabs.value.toMutableList().apply { removeAt(index) }
            _openTabs.value = updated
            if (_activeTabIndex.value >= updated.size) {
                _activeTabIndex.value = (updated.size - 1).coerceAtLeast(0)
            }
            currentTab?.let { updateSuggestions(it.content) } ?: run { _suggestions.value = emptyList() }
        }
    }

    fun onContentChanged(newContent: String) {
        val tab = currentTab ?: return
        if (tab.content != newContent) {
            val autoIndented = applyAutoIndent(tab.content, newContent)
            tab.undoStack.add(tab.content)
            tab.redoStack.clear()
            tab.content = autoIndented
            tab.isModified = (tab.content != tab.originalContent)
            _openTabs.value = _openTabs.value.toList()
            updateSuggestions(autoIndented)

            // Autosave after 3 seconds of inactivity
            autoSaveJob?.cancel()
            autoSaveJob = viewModelScope.launch {
                delay(3000)
                saveAndCommit(tab, null)
            }
        }
    }

    private fun applyAutoIndent(oldText: String, newText: String): String {
        // If Enter key was pressed (new text has 1 more newline and length is bigger)
        if (newText.length > oldText.length && newText.count { it == '\n' } == oldText.count { it == '\n' } + 1) {
            for (i in newText.indices) {
                if (newText[i] == '\n' && (i >= oldText.length || oldText[i] != '\n')) {
                    val prevLineStart = newText.lastIndexOf('\n', i - 1)
                    val prevLine = if (prevLineStart == -1) newText.substring(0, i) else newText.substring(prevLineStart + 1, i)
                    val leadingWhitespace = prevLine.takeWhile { it == ' ' || it == '\t' }
                    if (leadingWhitespace.isNotEmpty()) {
                        return newText.substring(0, i + 1) + leadingWhitespace + newText.substring(i + 1)
                    }
                    break
                }
            }
        }
        return newText
    }

    private fun updateSuggestions(content: String) {
        val currentWord = content.takeLastWhile { it.isLetterOrDigit() || it == '_' }
        if (currentWord.length >= 2) {
            val wordsInFile = "\\b[a-zA-Z_][a-zA-Z0-9_]{2,}\\b".toRegex().findAll(content).map { it.value }.toSet()
            val pool = wordsInFile + standardKeywords
            val matches = pool.filter {
                it.startsWith(currentWord, ignoreCase = true) && !it.equals(currentWord, ignoreCase = true)
            }.take(6)
            _suggestions.value = matches
        } else {
            _suggestions.value = emptyList()
        }
    }

    fun applySuggestion(suggestion: String) {
        val tab = currentTab ?: return
        val currentWord = tab.content.takeLastWhile { it.isLetterOrDigit() || it == '_' }
        if (currentWord.isNotEmpty()) {
            val prefix = tab.content.dropLast(currentWord.length)
            onContentChanged(prefix + suggestion)
        } else {
            insertSymbol(suggestion)
        }
    }

    fun undo() {
        val tab = currentTab ?: return
        if (tab.undoStack.isNotEmpty()) {
            val previous = tab.undoStack.removeAt(tab.undoStack.size - 1)
            tab.redoStack.add(tab.content)
            tab.content = previous
            tab.isModified = (tab.content != tab.originalContent)
            _openTabs.value = _openTabs.value.toList()
            updateSuggestions(previous)
        }
    }

    fun redo() {
        val tab = currentTab ?: return
        if (tab.redoStack.isNotEmpty()) {
            val next = tab.redoStack.removeAt(tab.redoStack.size - 1)
            tab.undoStack.add(tab.content)
            tab.content = next
            tab.isModified = (tab.content != tab.originalContent)
            _openTabs.value = _openTabs.value.toList()
            updateSuggestions(next)
        }
    }

    fun insertSymbol(symbol: String) {
        val tab = currentTab ?: return
        onContentChanged(tab.content + symbol)
    }

    fun saveCurrentFile(commitMessage: String? = null) {
        currentTab?.let { tab ->
            autoSaveJob?.cancel()
            saveAndCommit(tab, commitMessage)
        }
    }

    private fun saveAndCommit(tab: EditorTab, commitMessage: String?) {
        viewModelScope.launch {
            _statusMessage.value = "Saving ${'$'}{tab.file.name} to GitHub..."
            try {
                val message = commitMessage?.ifBlank { "Update ${'$'}{tab.file.name}" } ?: "Update ${'$'}{tab.file.name}"
                val encodedContent = Base64.encodeToString(tab.content.toByteArray(), Base64.NO_WRAP)

                val updateReq = FileContentUpdate(
                    message = message,
                    content = encodedContent,
                    sha = tab.sha,
                    branch = _activeBranch.value
                )

                val response = apiService.createOrUpdateFile(owner, repo, tab.file.path, updateReq)
                if (response.isSuccessful) {
                    tab.isModified = false
                    tab.originalContent = tab.content
                    _openTabs.value = _openTabs.value.toList()

                    // Refresh latest SHA from remote
                    val ref = apiService.getSingleFile(owner, repo, tab.file.path, _activeBranch.value)
                    if (ref.isSuccessful && ref.body() != null) {
                        tab.sha = ref.body()!!.sha
                    }
                    _statusMessage.value = "Committed & Pushed successfully! 🚀"
                } else if (response.code() == 409) {
                    // Conflict: remote file changed
                    val remote = apiService.getSingleFile(owner, repo, tab.file.path, _activeBranch.value)
                    val remoteDecoded = if (remote.body()?.content != null) {
                        try {
                            String(Base64.decode(remote.body()!!.content!!.replace("\n", ""), Base64.DEFAULT))
                        } catch (_: Exception) { "" }
                    } else ""

                    _conflictDialog.value = ConflictState(
                        file = tab.file,
                        localContent = tab.content,
                        remoteContent = remoteDecoded,
                        remoteSha = remote.body()?.sha ?: ""
                    )
                } else {
                    _statusMessage.value = "Push failed: HTTP ${'$'}{response.code()}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Save error: ${'$'}{e.localizedMessage}"
            }
        }
    }

    fun resolveConflict(keepLocal: Boolean) {
        val state = _conflictDialog.value ?: return
        val tab = _openTabs.value.find { it.file.path == state.file.path }
        if (tab != null) {
            tab.sha = state.remoteSha
            if (!keepLocal) {
                tab.content = state.remoteContent
                tab.originalContent = state.remoteContent
                tab.isModified = false
            } else {
                saveAndCommit(tab, "Resolve conflict: keep local version")
            }
            _openTabs.value = _openTabs.value.toList()
        }
        _conflictDialog.value = null
    }

    fun getDiffForCurrentTab(): List<DiffLine> {
        val tab = currentTab ?: return emptyList()
        val originalLines = tab.originalContent.lines()
        val currentLines = tab.content.lines()

        val diffs = mutableListOf<DiffLine>()
        var i = 0
        var j = 0

        while (i < originalLines.size || j < currentLines.size) {
            if (i < originalLines.size && j < currentLines.size && originalLines[i] == currentLines[j]) {
                diffs.add(DiffLine(DiffType.UNCHANGED, originalLines[i], lineNumber = j + 1))
                i++
                j++
            } else if (j < currentLines.size && (i >= originalLines.size || !originalLines.contains(currentLines[j]))) {
                diffs.add(DiffLine(DiffType.ADDED, currentLines[j], lineNumber = j + 1))
                j++
            } else if (i < originalLines.size && (j >= currentLines.size || !currentLines.contains(originalLines[i]))) {
                diffs.add(DiffLine(DiffType.DELETED, originalLines[i], lineNumber = null))
                i++
            } else {
                diffs.add(DiffLine(DiffType.DELETED, originalLines[i], lineNumber = null))
                diffs.add(DiffLine(DiffType.ADDED, currentLines[j], lineNumber = j + 1))
                i++
                j++
            }
        }
        return diffs
    }

    fun createFile(filePath: String, initialContent: String = "") {
        viewModelScope.launch {
            _statusMessage.value = "Creating ${'$'}filePath on GitHub..."
            try {
                val encoded = Base64.encodeToString(initialContent.toByteArray(), Base64.NO_WRAP)
                val response = apiService.createOrUpdateFile(
                    owner = owner,
                    repo = repo,
                    path = filePath,
                    body = FileContentUpdate(
                        message = "Create ${'$'}filePath",
                        content = encoded,
                        branch = _activeBranch.value
                    )
                )
                if (response.isSuccessful) {
                    _statusMessage.value = "File created: ${'$'}filePath"
                    loadFileTreeRecursive()
                } else {
                    _statusMessage.value = "Create failed: HTTP ${'$'}{response.code()}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error creating file: ${'$'}{e.message}"
            }
        }
    }

    fun createFolder(folderPath: String) {
        val gitKeepPath = folderPath.trimEnd('/') + "/.gitkeep"
        createFile(gitKeepPath, "# Folder placeholder\n")
    }

    fun renameFile(oldNode: FileNode, newPath: String) {
        viewModelScope.launch {
            _statusMessage.value = "Renaming ${'$'}{oldNode.name} to ${'$'}newPath..."
            try {
                val oldFileResp = apiService.getSingleFile(owner, repo, oldNode.path, _activeBranch.value)
                if (!oldFileResp.isSuccessful || oldFileResp.body() == null) {
                    _statusMessage.value = "Failed to read old file"
                    return@launch
                }
                val content = oldFileResp.body()!!.content ?: ""
                val createResp = apiService.createOrUpdateFile(
                    owner = owner,
                    repo = repo,
                    path = newPath,
                    body = FileContentUpdate(
                        message = "Rename ${'$'}{oldNode.path} to ${'$'}newPath",
                        content = content.replace("\n", ""),
                        branch = _activeBranch.value
                    )
                )
                if (createResp.isSuccessful) {
                    oldNode.sha?.let { sha ->
                        apiService.deleteFile(
                            owner = owner,
                            repo = repo,
                            path = oldNode.path,
                            body = FileDeleteRequest(
                                message = "Delete old file during rename",
                                sha = sha,
                                branch = _activeBranch.value
                            )
                        )
                    }
                    _statusMessage.value = "Renamed successfully"
                    loadFileTreeRecursive()
                }
            } catch (e: Exception) {
                _statusMessage.value = "Rename error: ${'$'}{e.localizedMessage}"
            }
        }
    }

    fun deleteFile(fileNode: FileNode) {
        val sha = fileNode.sha ?: return
        viewModelScope.launch {
            _statusMessage.value = "Deleting ${'$'}{fileNode.name} on GitHub..."
            try {
                val response = apiService.deleteFile(
                    owner = owner,
                    repo = repo,
                    path = fileNode.path,
                    body = FileDeleteRequest(
                        message = "Delete ${'$'}{fileNode.name}",
                        sha = sha,
                        branch = _activeBranch.value
                    )
                )
                if (response.isSuccessful) {
                    val tabIdx = _openTabs.value.indexOfFirst { it.file.path == fileNode.path }
                    if (tabIdx >= 0) closeTab(tabIdx)
                    _statusMessage.value = "Deleted ${'$'}{fileNode.name}"
                    loadFileTreeRecursive()
                } else {
                    _statusMessage.value = "Delete failed (HTTP ${'$'}{response.code()})"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${'$'}{e.localizedMessage}"
            }
        }
    }

    fun deleteFolder(folderNode: FileNode) {
        viewModelScope.launch {
            _statusMessage.value = "Deleting folder ${'$'}{folderNode.name}..."
            try {
                fun collectFiles(node: FileNode, acc: MutableList<FileNode>) {
                    if (!node.isDirectory) {
                        acc.add(node)
                    } else {
                        node.children.forEach { collectFiles(it, acc) }
                    }
                }
                val files = mutableListOf<FileNode>()
                collectFiles(folderNode, files)

                for (f in files) {
                    f.sha?.let { sha ->
                        apiService.deleteFile(
                            owner = owner,
                            repo = repo,
                            path = f.path,
                            body = FileDeleteRequest(
                                message = "Delete ${'$'}{f.path} during folder deletion",
                                sha = sha,
                                branch = _activeBranch.value
                            )
                        )
                    }
                }
                _statusMessage.value = "Deleted folder ${'$'}{folderNode.name}"
                loadFileTreeRecursive()
            } catch (e: Exception) {
                _statusMessage.value = "Error deleting folder: ${'$'}{e.message}"
            }
        }
    }

    fun triggerBuild() {
        viewModelScope.launch {
            _statusMessage.value = "Checking build workflow..."
            // Check if build.yml exists in repository
            val checkResp = apiService.getSingleFile(owner, repo, ".github/workflows/build.yml", _activeBranch.value)
            if (checkResp.code() == 404) {
                _showMissingWorkflowDialog.value = true
                return@launch
            }

            dispatchBuildInternal()
        }
    }

    fun dismissMissingWorkflowDialog() {
        _showMissingWorkflowDialog.value = false
    }

    fun dismissWorkflowDispatchError() {
        _workflowDispatchError.value = null
    }

    fun createWorkflowAndBuild(buildCommand: String) {
        _showMissingWorkflowDialog.value = false
        viewModelScope.launch {
            _statusMessage.value = "Generating .github/workflows/build.yml..."
            val cmd = buildCommand.ifBlank { "./gradlew assembleDebug --stacktrace" }
            val workflowContent = """
                name: Build APK
                on:
                  push:
                    branches: [main]
                  workflow_dispatch:
                permissions:
                  contents: read
                concurrency:
                  group: build-${'$'}{{ github.ref }}
                  cancel-in-progress: true
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    timeout-minutes: 30
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/setup-java@v4
                        with:
                          distribution: temurin
                          java-version: 17
                          cache: gradle
                      - run: chmod +x gradlew
                      - run: ${'$'}cmd
                      - uses: actions/upload-artifact@v4
                        with:
                          name: app-debug
                          path: app/build/outputs/apk/debug/*.apk
                          retention-days: 7
            """.trimIndent()

            val encoded = Base64.encodeToString(workflowContent.toByteArray(), Base64.NO_WRAP)
            val createResp = apiService.createOrUpdateFile(
                owner = owner,
                repo = repo,
                path = ".github/workflows/build.yml",
                body = FileContentUpdate(
                    message = "Add build.yml CI workflow",
                    content = encoded,
                    branch = _activeBranch.value
                )
            )

            if (createResp.isSuccessful) {
                loadFileTreeRecursive()
                delay(1500)
                dispatchBuildInternal()
            } else {
                _statusMessage.value = "Failed to create workflow (HTTP ${'$'}{createResp.code()})"
            }
        }
    }

    private fun dispatchBuildInternal() {
        viewModelScope.launch {
            _buildStatus.value = BuildStatus.RUNNING
            _canInstallApk.value = false
            _buildLogs.value = "🚀 Dispatching GitHub Actions build workflow...\n"
            try {
                val dispatch = apiService.dispatchWorkflow(
                    owner = owner,
                    repo = repo,
                    workflowId = "build.yml",
                    body = WorkflowDispatch(ref = _activeBranch.value)
                )

                if (dispatch.isSuccessful) {
                    _buildLogs.value += "Workflow dispatched. Waiting for runner to pick up job...\n"
                    delay(3000)
                    startPollingWorkflow()
                } else if (dispatch.code() == 404) {
                    _buildStatus.value = BuildStatus.FAILURE
                    _workflowDispatchError.value = "Workflow 'build.yml' was not found on branch '${'$'}{_activeBranch.value}', or your GitHub PAT is missing the 'workflow' scope."
                    _buildLogs.value += "Dispatch failed: HTTP 404 (Workflow not found or missing 'workflow' scope).\n"
                } else {
                    _buildStatus.value = BuildStatus.FAILURE
                    _buildLogs.value += "Dispatch failed: HTTP ${'$'}{dispatch.code()} ${'$'}{dispatch.message()}\n"
                }
            } catch (e: Exception) {
                _buildStatus.value = BuildStatus.FAILURE
                _buildLogs.value += "Error: ${'$'}{e.localizedMessage}\n"
            }
        }
    }

    private fun startPollingWorkflow() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            for (attempt in 1..60) {
                if (!isActive) break
                try {
                    val runsResp = apiService.getWorkflowRuns(owner, repo, branch = _activeBranch.value)
                    if (runsResp.isSuccessful && !runsResp.body()?.workflowRuns.isNullOrEmpty()) {
                        val latestRun = runsResp.body()!!.workflowRuns.first()
                        _currentRunId.value = latestRun.id

                        val jobsResp = apiService.getWorkflowJobs(owner, repo, latestRun.id)
                        if (jobsResp.isSuccessful && !jobsResp.body()?.jobs.isNullOrEmpty()) {
                            val job = jobsResp.body()!!.jobs.first()
                            _workflowSteps.value = job.steps ?: emptyList()

                            // Check logs
                            val logsResp = apiService.getJobLogs(owner, repo, job.id)
                            if (logsResp.isSuccessful && logsResp.body() != null) {
                                _buildLogs.value = logsResp.body()!!.string().takeLast(15000)
                            }
                        }

                        if (latestRun.status == "completed") {
                            if (latestRun.conclusion == "success") {
                                _buildStatus.value = BuildStatus.SUCCESS
                                _canInstallApk.value = true
                                _buildLogs.value += "\n\n🎉 BUILD FINISHED SUCCESSFULLY! Artifact ready to install."
                            } else {
                                _buildStatus.value = BuildStatus.FAILURE
                                _canInstallApk.value = false
                                _buildLogs.value += "\n\n❌ BUILD FAILED (${'$'}{latestRun.conclusion})"
                            }
                            break
                        }
                    }
                } catch (_: Exception) {}
                delay(3500)
            }
        }
    }

    fun downloadAndInstallArtifact(context: Context) {
        val runId = _currentRunId.value ?: return
        viewModelScope.launch {
            _statusMessage.value = "Fetching artifact for run #${'$'}runId..."
            try {
                val artifactsResp = apiService.getArtifacts(owner, repo, runId)
                if (artifactsResp.isSuccessful && !artifactsResp.body()?.artifacts.isNullOrEmpty()) {
                    val artifact = artifactsResp.body()!!.artifacts.find { it.name == "app-debug" }
                        ?: artifactsResp.body()!!.artifacts.first()

                    _statusMessage.value = "Downloading ${'$'}{artifact.name} (${'$'}{artifact.sizeInBytes / 1024} KB)..."
                    val result = ApkInstaller.downloadArtifactAndInstall(
                        context = context,
                        archiveUrl = artifact.archiveDownloadUrl,
                        authToken = authToken
                    )

                    if (result.isSuccess) {
                        _latestExtractedApk.value = result.getOrNull()
                        _statusMessage.value = "APK extracted and package installer opened! 📱"
                    } else {
                        _statusMessage.value = "Install failed: ${'$'}{result.exceptionOrNull()?.message}"
                    }
                } else {
                    _statusMessage.value = "No build artifact found for run #${'$'}runId"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${'$'}{e.localizedMessage}"
            }
        }
    }

    fun saveApkToDownloads(context: Context) {
        val apk = _latestExtractedApk.value
        if (apk != null) {
            viewModelScope.launch {
                val result = ApkInstaller.saveApkToDownloads(context, apk)
                _statusMessage.value = if (result.isSuccess) result.getOrNull() ?: "Saved to Downloads"
                else "Failed to export: ${'$'}{result.exceptionOrNull()?.message}"
            }
        } else {
            // Download directly from latest artifact
            val runId = _currentRunId.value ?: return
            viewModelScope.launch {
                val artifactsResp = apiService.getArtifacts(owner, repo, runId)
                val artifact = artifactsResp.body()?.artifacts?.firstOrNull() ?: return@launch
                val result = ApkInstaller.downloadGenericArtifactZipToDownloads(
                    context = context,
                    archiveUrl = artifact.archiveDownloadUrl,
                    artifactName = artifact.name,
                    authToken = authToken
                )
                _statusMessage.value = if (result.isSuccess) result.getOrNull() ?: "Saved to Downloads"
                else "Failed: ${'$'}{result.exceptionOrNull()?.message}"
            }
        }
    }

    fun searchAcrossRepo(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _statusMessage.value = "Searching '${'$'}query' across ${'$'}repo..."
            try {
                val resp = apiService.searchCode("${'$'}query repo:${'$'}owner/${'$'}repo")
                if (resp.isSuccessful && resp.body() != null) {
                    _searchResults.value = resp.body()!!.items
                    _statusMessage.value = "Found ${'$'}{_searchResults.value.size} results for '${'$'}query'"
                } else {
                    _statusMessage.value = "Search returned HTTP ${'$'}{resp.code()}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Search error: ${'$'}{e.localizedMessage}"
            }
        }
    }

    private fun loadBranches() {
        viewModelScope.launch {
            try {
                val resp = apiService.getBranches(owner, repo)
                if (resp.isSuccessful && resp.body() != null) {
                    _branches.value = resp.body()!!
                }
            } catch (_: Exception) {}
        }
    }

    fun switchBranch(branch: String) {
        _activeBranch.value = branch
        _openTabs.value = emptyList()
        _activeTabIndex.value = 0
        loadFileTreeRecursive()
    }

    fun createBranch(newBranchName: String) {
        viewModelScope.launch {
            try {
                val currentSha = _branches.value.find { it.name == _activeBranch.value }?.commit?.sha
                if (currentSha != null) {
                    val req = CreateRefRequest(ref = "refs/heads/${'$'}newBranchName", sha = currentSha)
                    val resp = apiService.createRef(owner, repo, req)
                    if (resp.isSuccessful) {
                        _statusMessage.value = "Branch '${'$'}newBranchName' created!"
                        loadBranches()
                        switchBranch(newBranchName)
                    } else {
                        _statusMessage.value = "Failed to create branch: HTTP ${'$'}{resp.code()}"
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${'$'}{e.localizedMessage}"
            }
        }
    }

    private fun loadPullRequests() {
        viewModelScope.launch {
            try {
                val resp = apiService.getPullRequests(owner, repo)
                if (resp.isSuccessful && resp.body() != null) {
                    _pullRequests.value = resp.body()!!
                }
            } catch (_: Exception) {}
        }
    }

    fun createPullRequest(title: String, head: String, base: String, body: String? = null) {
        viewModelScope.launch {
            try {
                val req = CreatePullRequest(title = title, head = head, base = base, body = body)
                val resp = apiService.createPullRequest(owner, repo, req)
                if (resp.isSuccessful) {
                    _statusMessage.value = "PR #${'$'}{resp.body()?.number} created!"
                    loadPullRequests()
                } else {
                    _statusMessage.value = "Failed to create PR: HTTP ${'$'}{resp.code()}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${'$'}{e.localizedMessage}"
            }
        }
    }

    fun mergePullRequest(prNumber: Int) {
        viewModelScope.launch {
            _statusMessage.value = "Merging PR #${'$'}prNumber..."
            try {
                val resp = apiService.mergePullRequest(owner, repo, prNumber, MergePullRequest())
                if (resp.isSuccessful && resp.body()?.merged == true) {
                    _statusMessage.value = "PR #${'$'}prNumber merged successfully!"
                    loadPullRequests()
                } else {
                    _statusMessage.value = "Merge failed: ${'$'}{resp.body()?.message ?: "HTTP ${'$'}{resp.code()}"}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${'$'}{e.localizedMessage}"
            }
        }
    }
}
