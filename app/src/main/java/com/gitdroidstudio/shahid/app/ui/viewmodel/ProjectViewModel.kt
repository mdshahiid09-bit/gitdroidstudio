package com.gitdroidstudio.shahid.app.ui.viewmodel

import android.content.Context
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
import java.util.Base64

data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sha: String?,
    val children: MutableList<FileNode> = mutableListOf(),
    var isExpanded: Boolean = false,
    var isLoading: Boolean = false
)

enum class BuildStatus { IDLE, RUNNING, SUCCESS, FAILURE }

class ProjectViewModel(
    val owner: String,
    val repo: String,
    val defaultBranch: String,
    private val apiService: GitHubApiService,
    private val authToken: String?
) : ViewModel() {

    private val _fileTree = MutableStateFlow<List<FileNode>>(emptyList())
    val fileTree: StateFlow<List<FileNode>> = _fileTree.asStateFlow()

    private val _currentFile = MutableStateFlow<GitHubFile?>(null)
    val currentFile: StateFlow<GitHubFile?> = _currentFile.asStateFlow()

    private val _editorContent = MutableStateFlow("")
    val editorContent: StateFlow<String> = _editorContent.asStateFlow()

    private val _isModified = MutableStateFlow(false)
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _buildStatus = MutableStateFlow(BuildStatus.IDLE)
    val buildStatus: StateFlow<BuildStatus> = _buildStatus.asStateFlow()

    private val _buildLogs = MutableStateFlow("")
    val buildLogs: StateFlow<String> = _buildLogs.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _branches = MutableStateFlow<List<Branch>>(emptyList())
    val branches: StateFlow<List<Branch>> = _branches.asStateFlow()

    private val _activeBranch = MutableStateFlow(defaultBranch)
    val activeBranch: StateFlow<String> = _activeBranch.asStateFlow()

    private var pollingJob: Job? = null

    init {
        loadFileTree("")
        loadBranches()
    }

    fun loadFileTree(dirPath: String) {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Fetching files..."
                val response = apiService.getContents(owner, repo, dirPath, _activeBranch.value)
                if (response.isSuccessful && response.body() != null) {
                    val nodes = response.body()!!.map { item ->
                        FileNode(
                            name = item.name,
                            path = item.path,
                            isDirectory = item.type == "dir",
                            sha = item.sha
                        )
                    }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

                    if (dirPath.isEmpty()) {
                        _fileTree.value = nodes
                    }
                    _statusMessage.value = "Synced with GitHub"
                } else {
                    _statusMessage.value = "Failed to load directory"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Sync error: ${e.message}"
            }
        }
    }

    fun expandFolder(node: FileNode) {
        if (!node.isDirectory) return
        node.isExpanded = !node.isExpanded
        if (node.isExpanded && node.children.isEmpty()) {
            viewModelScope.launch {
                node.isLoading = true
                try {
                    val response = apiService.getContents(owner, repo, node.path, _activeBranch.value)
                    if (response.isSuccessful && response.body() != null) {
                        node.children.clear()
                        node.children.addAll(
                            response.body()!!.map { item ->
                                FileNode(
                                    name = item.name,
                                    path = item.path,
                                    isDirectory = item.type == "dir",
                                    sha = item.sha
                                )
                            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        )
                    }
                } catch (_: Exception) {
                } finally {
                    node.isLoading = false
                    _fileTree.value = _fileTree.value.toList() // trigger recomposition
                }
            }
        } else {
            _fileTree.value = _fileTree.value.toList()
        }
    }

    fun openFile(node: FileNode) {
        if (node.isDirectory) return
        viewModelScope.launch {
            _statusMessage.value = "Opening ${node.name}..."
            try {
                val response = apiService.getSingleFile(owner, repo, node.path, _activeBranch.value)
                if (response.isSuccessful && response.body() != null) {
                    val file = response.body()!!
                    val decoded = if (file.content != null) {
                        try {
                            val cleanBase64 = file.content.replace("\n", "").replace("\r", "")
                            String(Base64.getDecoder().decode(cleanBase64))
                        } catch (_: Exception) {
                            file.content
                        }
                    } else ""

                    _currentFile.value = file
                    _editorContent.value = decoded
                    _isModified.value = false
                    _statusMessage.value = "Editing ${file.name}"
                } else {
                    _statusMessage.value = "Error reading file content"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error opening file: ${e.message}"
            }
        }
    }

    fun onContentChanged(newContent: String) {
        _editorContent.value = newContent
        _isModified.value = true
    }

    fun saveAndCommit(commitMessage: String? = null) {
        val file = _currentFile.value ?: return
        viewModelScope.launch {
            _statusMessage.value = "Pushing commit to GitHub..."
            try {
                val message = commitMessage?.ifBlank { "Update ${file.name}" } ?: "Update ${file.name}"
                val encodedContent = Base64.getEncoder().encodeToString(_editorContent.value.toByteArray())

                val updateReq = FileContentUpdate(
                    message = message,
                    content = encodedContent,
                    sha = file.sha,
                    branch = _activeBranch.value
                )

                val response = apiService.createOrUpdateFile(owner, repo, file.path, updateReq)
                if (response.isSuccessful) {
                    _isModified.value = false
                    _statusMessage.value = "Committed & Pushed successfully! 🚀"
                    // refresh file SHA
                    val refFile = apiService.getSingleFile(owner, repo, file.path, _activeBranch.value)
                    if (refFile.isSuccessful) {
                        _currentFile.value = refFile.body()
                    }
                } else {
                    _statusMessage.value = "Push failed: HTTP ${response.code()}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            }
        }
    }

    fun createFile(filePath: String, initialContent: String = "") {
        viewModelScope.launch {
            try {
                val encoded = Base64.getEncoder().encodeToString(initialContent.toByteArray())
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
                    loadFileTree("")
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error creating file: ${e.message}"
            }
        }
    }

    fun deleteFile(fileNode: FileNode) {
        val sha = fileNode.sha ?: return
        viewModelScope.launch {
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
                    if (_currentFile.value?.path == fileNode.path) {
                        _currentFile.value = null
                        _editorContent.value = ""
                    }
                    _statusMessage.value = "Deleted ${fileNode.name}"
                    loadFileTree("")
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error deleting: ${e.message}"
            }
        }
    }

    fun triggerBuild() {
        viewModelScope.launch {
            _buildStatus.value = BuildStatus.RUNNING
            _buildLogs.value = "🚀 Triggering GitHub Actions build workflow...\n"
            try {
                val response = apiService.dispatchWorkflow(
                    owner = owner,
                    repo = repo,
                    workflowId = "build.yml",
                    body = WorkflowDispatch(ref = _activeBranch.value)
                )

                if (response.isSuccessful) {
                    _buildLogs.value += "Workflow dispatched successfully. Waiting for runner to pick up job...\n"
                    delay(3000)
                    startPollingWorkflow()
                } else {
                    _buildStatus.value = BuildStatus.FAILURE
                    _buildLogs.value += "Failed to trigger build: HTTP ${response.code()} ${response.message()}\n"
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
            var runId: Long? = null
            for (attempt in 1..40) {
                if (!isActive) break
                try {
                    val runsResp = apiService.getWorkflowRuns(owner, repo)
                    if (runsResp.isSuccessful && !runsResp.body()?.workflowRuns.isNullOrEmpty()) {
                        val latestRun = runsResp.body()!!.workflowRuns.first()
                        runId = latestRun.id

                        _buildLogs.value = "Build Run #${latestRun.runNumber} [${latestRun.status.uppercase()}]\n" +
                                "Branch: ${latestRun.headBranch}\n"

                        val jobsResp = apiService.getWorkflowJobs(owner, repo, latestRun.id)
                        if (jobsResp.isSuccessful && !jobsResp.body()?.jobs.isNullOrEmpty()) {
                            val job = jobsResp.body()!!.jobs.first()
                            _buildLogs.value += "Job: ${job.name} -> Status: ${job.status}\n"

                            // Stream latest logs
                            val logsResp = apiService.getJobLogs(owner, repo, job.id)
                            if (logsResp.isSuccessful && logsResp.body() != null) {
                                val logText = logsResp.body()!!.string()
                                _buildLogs.value = logText.takeLast(15000) // keep last 15KB for performance
                            }
                        }

                        if (latestRun.status == "completed") {
                            if (latestRun.conclusion == "success") {
                                _buildStatus.value = BuildStatus.SUCCESS
                                _buildLogs.value += "\n\n🎉 BUILD FINISHED SUCCESSFULLY! Ready to install APK."
                            } else {
                                _buildStatus.value = BuildStatus.FAILURE
                                _buildLogs.value += "\n\n❌ BUILD FAILED (${latestRun.conclusion})"
                            }
                            break
                        }
                    }
                } catch (_: Exception) {}
                delay(4000)
            }
        }
    }

    fun downloadAndInstallApk(context: Context, directApkUrl: String) {
        viewModelScope.launch {
            _statusMessage.value = "Downloading APK..."
            val result = ApkInstaller.downloadAndInstallApk(
                context = context,
                apkUrl = directApkUrl,
                authToken = authToken,
                onProgress = { progress ->
                    _downloadProgress.value = progress
                }
            )

            if (result.isSuccess) {
                _statusMessage.value = "APK downloaded! Opening installer..."
            } else {
                _statusMessage.value = "Download failed: ${result.exceptionOrNull()?.message}"
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
        _currentFile.value = null
        _editorContent.value = ""
        loadFileTree("")
    }
}
