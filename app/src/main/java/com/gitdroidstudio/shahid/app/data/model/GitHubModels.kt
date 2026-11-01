package com.gitdroidstudio.shahid.app.data.model

import com.google.gson.annotations.SerializedName

data class GitHubUser(
    val login: String,
    val id: Long,
    @SerializedName("avatar_url") val avatarUrl: String?,
    val name: String?,
    val bio: String?,
    @SerializedName("public_repos") val publicRepos: Int = 0
)

data class Repository(
    val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String,
    val description: String?,
    val private: Boolean,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("default_branch") val defaultBranch: String = "main",
    @SerializedName("updated_at") val updatedAt: String?,
    val owner: GitHubUser?
)

data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    @SerializedName("auto_init") val autoInit: Boolean = true
)

data class GitHubFile(
    val name: String,
    val path: String,
    val sha: String?,
    val size: Long = 0,
    val type: String, // "file" or "dir"
    val content: String? = null,
    val encoding: String? = null,
    @SerializedName("download_url") val downloadUrl: String? = null
)

data class FileContentUpdate(
    val message: String,
    val content: String, // Base64 encoded
    val sha: String? = null,
    val branch: String = "main"
)

data class FileDeleteRequest(
    val message: String,
    val sha: String,
    val branch: String = "main"
)

data class GitTreeResponse(
    val sha: String,
    val url: String,
    val tree: List<GitTreeNode>,
    val truncated: Boolean = false
)

data class GitTreeNode(
    val path: String,
    val mode: String,
    val type: String, // "blob" or "tree"
    val sha: String,
    val size: Long? = null,
    val url: String? = null
)

data class ArtifactsResponse(
    @SerializedName("total_count") val totalCount: Int,
    val artifacts: List<ArtifactItem>
)

data class ArtifactItem(
    val id: Long,
    val name: String,
    @SerializedName("size_in_bytes") val sizeInBytes: Long,
    @SerializedName("archive_download_url") val archiveDownloadUrl: String,
    val expired: Boolean = false,
    @SerializedName("created_at") val createdAt: String?
)

data class WorkflowRunsResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("workflow_runs") val workflowRuns: List<WorkflowRun>
)

data class WorkflowRun(
    val id: Long,
    val name: String?,
    @SerializedName("head_branch") val headBranch: String?,
    val status: String, // queued, in_progress, completed
    val conclusion: String?, // success, failure, neutral, cancelled
    @SerializedName("html_url") val htmlUrl: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("run_number") val runNumber: Int
)

data class JobsResponse(
    @SerializedName("total_count") val totalCount: Int,
    val jobs: List<WorkflowJob>
)

data class WorkflowJob(
    val id: Long,
    @SerializedName("run_id") val runId: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("completed_at") val completedAt: String?,
    val steps: List<WorkflowStep>? = null
)

data class WorkflowStep(
    val name: String,
    val status: String,
    val conclusion: String?,
    val number: Int
)

data class Branch(
    val name: String,
    val commit: BranchCommit?
)

data class BranchCommit(
    val sha: String,
    val url: String?
)

data class CreateRefRequest(
    val ref: String,
    val sha: String
)

data class GitRef(
    val ref: String,
    val url: String
)

data class PullRequest(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String, // open, closed
    val user: GitHubUser?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("html_url") val htmlUrl: String?
)

data class CreatePullRequest(
    val title: String,
    val head: String,
    val base: String,
    val body: String? = null
)

data class MergePullRequest(
    @SerializedName("commit_title") val commitTitle: String? = null,
    @SerializedName("commit_message") val commitMessage: String? = null,
    @SerializedName("merge_method") val mergeMethod: String = "merge"
)

data class MergeResponse(
    val sha: String,
    val merged: Boolean,
    val message: String
)

data class CodeSearchResponse(
    @SerializedName("total_count") val totalCount: Int,
    val items: List<CodeSearchItem>
)

data class CodeSearchItem(
    val name: String,
    val path: String,
    val sha: String,
    @SerializedName("html_url") val htmlUrl: String
)

data class GitHubRelease(
    val id: Long,
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("published_at") val publishedAt: String?
)

data class WorkflowDispatch(
    val ref: String = "main"
)

data class AccountInfo(
    val username: String,
    val token: String,
    val avatarUrl: String?
)
