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

    private var autoSaveJob: Job? = null
    private var pollingJob: Job? = null

    init {
        loadFileTreeRecursive()
        loadBranches()
        loadPullRequests()
    }

    /**
     * Loads the entire repository file tree in one call using the Git Trees API,
     * building a hierarchy in memory.
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
                    _statusMessage.value = "Tree synced (${treeResponse.tree.size} items)"
                } else {
                    // Fallback to contents API for shallow listing
                    loadContentsShallow("")
                }
            } catch (e: Exception) {
                _statusMessage.value = "Sync error: ${e.message}"
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
        _fileTree.value = _fileTree.value.toList() // trigger recomposition
    }

    fun openFile(node: FileNode) {
        if (node.isDirectory) return

        val existingIndex = _openTabs.value.indexOfFirst { it.file.path == node.path }
        if (existingIndex >= 0) {
            _activeTabIndex.value = existingIndex
            return
        }

        viewModelScope.launch {
            _statusMessage.value = "Opening ${node.name}..."
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
                        isModified = false,
                        sha = file.sha
                    )
                    _openTabs.value = _openTabs.value + newTab
                    _activeTabIndex.value = _openTabs.value.size - 1
                    _statusMessage.value = "Editing ${file.name}"
                } else {
                    _statusMessage.value = "Error opening file (HTTP ${resp.code()})"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            }
        }
    }

    fun selectTab(index: Int) {
        if (index in _openTabs.value.indices) {
            _activeTabIndex.value = index
        }
    }

    fun closeTab(index: Int) {
        if (index in _openTabs.value.indices) {
            val updated = _openTabs.value.toMutableList().apply { removeAt(index) }
            _openTabs.value = updated
            if (_activeTabIndex.value >= updated.size) {
                _activeTabIndex.value = (updated.size - 1).coerceAtLeast(0)
            }
        }
    }

    fun onContentChanged(newContent: String) {
        val tab = currentTab ?: return
        if (tab.content != newContent) {
            tab.undoStack.add(tab.content)
            tab.redoStack.clear()
            tab.content = newContent
            tab.isModified = true
            _openTabs.value = _openTabs.value.toList() // trigger state update

            // Autosave after 3 seconds of inactivity
            autoSaveJob?.cancel()
            autoSaveJob = viewModelScope.launch {
                delay(3000)
                saveAndCommit(tab, null)
            }
        }
    }

    fun undo() {
        val tab = currentTab ?: return
        if (tab.undoStack.isNotEmpty()) {
            val previous = tab.undoStack.removeAt(tab.undoStack.size - 1)
            tab.redoStack.add(tab.content)
            tab.content = previous
            tab.isModified = true
            _openTabs.value = _openTabs.value.toList()
        }
    }

    fun redo() {
        val tab = currentTab ?: return
        if (tab.redoStack.isNotEmpty()) {
            val next = tab.redoStack.removeAt(tab.redoStack.size - 1)
            tab.undoStack.add(tab.content)
            tab.content = next
            tab.isModified = true
            _openTabs.value = _openTabs.value.toList()
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
            _statusMessage.value = "Saving ${tab.file.name} to GitHub..."
            try {
                val message = commitMessage?.ifBlank { "Update ${tab.file.name}" } ?: "Update ${tab.file.name}"
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
                    _statusMessage.value = "Push failed: HTTP ${response.code()}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Save error: ${e.localizedMessage}"
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
                tab.isModified = false
            } else {
                // Overwrite with local using new remote sha
                saveAndCommit(tab, "Resolve conflict: keep local version")
            }
            _openTabs.value = _openTabs.value.toList()
        }
        _conflictDialog.value = null
    }

    fun createFile(filePath: String, initialContent: String = "") {
        viewModelScope.launch {
            _statusMessage.value = "Creating $filePath on GitHub..."
            try {
                val encoded = Base64.encodeToString(initialContent.toByteArray(), Base64.NO_WRAP)
                val response = apiService.createOrUpdateFile(
                    owner = owner,
                    repo = repo,
                    path = filePath,
                    body = FileContentUpdate(
                        message = "Create $filePath",
                        content = encoded,
                        branch = _activeBranch.value
                    )
                )
                if (response.isSuccessful) {
                    _statusMessage.value = "File created: $filePath"
                    loadFileTreeRecursive()
                } else {
                    _statusMessage.value = "Create failed: HTTP ${response.code()}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error creating file: ${e.message}"
            }
        }
    }

    fun renameFile(oldNode: FileNode, newPath: String) {
        val currentTabFile = currentTab?.file
        viewModelScope.launch {
            _statusMessage.value = "Renaming ${oldNode.name} to $newPath..."
            try {
                // Fetch old file content
                val oldFileResp = apiService.getSingleFile(owner, repo, oldNode.path, _activeBranch.value)
                if (!oldFileResp.isSuccessful || oldFileResp.body() == null) {
                    _statusMessage.value = "Failed to read old file"
                    return@launch
                }
                val content = oldFileResp.body()!!.content ?: ""
                // 1. Create file at new path
                val createResp = apiService.createOrUpdateFile(
                    owner = owner,
                    repo = repo,
                    path = newPath,
                    body = FileContentUpdate(
                        message = "Rename ${oldNode.path} to $newPath",
                        content = content.replace("\n", ""),
                        branch = _activeBranch.value
                    )
                )
                if (createResp.isSuccessful) {
                    // 2. Delete old file
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
                _statusMessage.value = "Rename error: ${e.localizedMessage}"
            }
        }
    }

    fun deleteFile(fileNode: FileNode) {
        val sha = fileNode.sha ?: return
        viewModelScope.launch {
            _statusMessage.value = "Deleting ${fileNode.name} on GitHub..."
            try {
                val response = apiService.deleteFile(
                    owner = owner,
                    repo = repo,
                    path = fileNode.path,
                    body = FileDeleteRequest(
                        message = "Delete ${fileNode.name}",
                        sha = sha,
                        branch = _activeBranch.value
                    )
                )
                if (response.isSuccessful) {
                    // Close tab if open
                    val tabIdx = _openTabs.value.indexOfFirst { it.file.path == fileNode.path }
                    if (tabIdx >= 0) closeTab(tabIdx)
                    _statusMessage.value = "Deleted ${fileNode.name}"
                    loadFileTreeRecursive()
                } else {
                    _statusMessage.value = "Delete failed (HTTP ${response.code()})"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            }
        }
    }

    fun triggerBuild() {
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
                } else {
                    _buildStatus.value = BuildStatus.FAILURE
                    _buildLogs.value += "Dispatch failed: HTTP ${dispatch.code()} ${dispatch.message()}\n"
                }
            } catch (e: Exception) {
                _buildStatus.value = BuildStatus.FAILURE
                _buildLogs.value += "Error: ${e.localizedMessage}\n"
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
                                _buildLogs.value += "\n\n❌ BUILD FAILED (${latestRun.conclusion})"
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
            _statusMessage.value = "Fetching artifact for run #$runId..."
            try {
                val artifactsResp = apiService.getArtifacts(owner, repo, runId)
                if (artifactsResp.isSuccessful && !artifactsResp.body()?.artifacts.isNullOrEmpty()) {
                    val artifact = artifactsResp.body()!!.artifacts.find { it.name == "app-debug" }
                        ?: artifactsResp.body()!!.artifacts.first()

                    _statusMessage.value = "Downloading ${artifact.name} (${artifact.sizeInBytes / 1024} KB)..."
                    val result = ApkInstaller.downloadArtifactAndInstall(
                        context = context,
                        archiveUrl = artifact.archiveDownloadUrl,
                        authToken = authToken
                    )

                    if (result.isSuccess) {
                        _latestExtractedApk.value = result.getOrNull()
                        _statusMessage.value = "APK extracted and package installer opened! 📱"
                    } else {
                        _statusMessage.value = "Install failed: ${result.exceptionOrNull()?.message}"
                    }
                } else {
                    _statusMessage.value = "No build artifact found for run #$runId"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            }
        }
    }

    fun saveApkToDownloads(context: Context) {
        val apk = _latestExtractedApk.value ?: return
        viewModelScope.launch {
            val result = ApkInstaller.saveApkToDownloads(context, apk)
            _statusMessage.value = if (result.isSuccess) result.getOrNull() ?: "Saved to Downloads"
            else "Failed to export: ${result.exceptionOrNull()?.message}"
        }
    }

    fun searchAcrossRepo(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _statusMessage.value = "Searching '$query' across $repo..."
            try {
                val resp = apiService.searchCode("$query repo:$owner/$repo")
                if (resp.isSuccessful && resp.body() != null) {
                    _searchResults.value = resp.body()!!.items
                    _statusMessage.value = "Found ${_searchResults.value.size} results for '$query'"
                } else {
                    _statusMessage.value = "Search returned HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Search error: ${e.localizedMessage}"
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
                    val req = CreateRefRequest(ref = "refs/heads/$newBranchName", sha = currentSha)
                    val resp = apiService.createRef(owner, repo, req)
                    if (resp.isSuccessful) {
                        _statusMessage.value = "Branch '$newBranchName' created!"
                        loadBranches()
                        switchBranch(newBranchName)
                    } else {
                        _statusMessage.value = "Failed to create branch: HTTP ${resp.code()}"
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
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
                    _statusMessage.value = "PR #${resp.body()?.number} created!"
                    loadPullRequests()
                } else {
                    _statusMessage.value = "Failed to create PR: HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            }
        }
    }

    fun mergePullRequest(prNumber: Int) {
        viewModelScope.launch {
            _statusMessage.value = "Merging PR #$prNumber..."
            try {
                val resp = apiService.mergePullRequest(owner, repo, prNumber, MergePullRequest())
                if (resp.isSuccessful && resp.body()?.merged == true) {
                    _statusMessage.value = "PR #$prNumber merged successfully!"
                    loadPullRequests()
                } else {
                    _statusMessage.value = "Merge failed: ${resp.body()?.message ?: "HTTP ${resp.code()}"}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            }
        }
    }
}
