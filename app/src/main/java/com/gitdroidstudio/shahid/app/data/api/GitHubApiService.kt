package com.gitdroidstudio.shahid.app.data.api

import com.gitdroidstudio.shahid.app.data.model.*
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface GitHubApiService {

    @GET("user")
    async suspend fun getCurrentUser(): Response<GitHubUser>

    @GET("user/repos")
    async suspend fun getUserRepositories(
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100,
        @Query("type") type: String = "all"
    ): Response<List<Repository>>

    @POST("user/repos")
    async suspend fun createRepository(
        @Body request: CreateRepoRequest
    ): Response<Repository>

    @GET("repos/{owner}/{repo}/contents/{path}")
    async suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String = "",
        @Query("ref") ref: String? = null
    ): Response<List<GitHubFile>>

    @GET("repos/{owner}/{repo}/contents/{path}")
    async suspend fun getSingleFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): Response<GitHubFile>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    async suspend fun createOrUpdateFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: FileContentUpdate
    ): Response<ResponseBody>

    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    async suspend fun deleteFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: FileDeleteRequest
    ): Response<ResponseBody>

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    async suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String = "build.yml",
        @Body body: WorkflowDispatch
    ): Response<ResponseBody>

    @GET("repos/{owner}/{repo}/actions/runs")
    async suspend fun getWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 10
    ): Response<WorkflowRunsResponse>

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    async suspend fun getWorkflowJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<JobsResponse>

    @GET("repos/{owner}/{repo}/actions/jobs/{job_id}/logs")
    @Streaming
    async suspend fun getJobLogs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("job_id") jobId: Long
    ): Response<ResponseBody>

    @GET("repos/{owner}/{repo}/branches")
    async suspend fun getBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<List<Branch>>

    @GET("repos/{owner}/{repo}/pulls")
    async suspend fun getPullRequests(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open"
    ): Response<List<PullRequest>>

    @POST("repos/{owner}/{repo}/pulls")
    async suspend fun createPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreatePullRequest
    ): Response<PullRequest>

    companion object {
        private const val BASE_URL = "https://api.github.com/"

        fun create(tokenProvider: () -> String?): GitHubApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val requestBuilder = chain.request().newBuilder()
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")

                    tokenProvider()?.let { token ->
                        if (token.isNotBlank()) {
                            val authHeader = if (token.startsWith("Bearer ") || token.startsWith("token ")) {
                                token
                            } else {
                                "token $token"
                            }
                            requestBuilder.header("Authorization", authHeader)
                        }
                    }

                    chain.proceed(requestBuilder.build())
                }
                .addInterceptor(logging)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GitHubApiService::class.java)
        }
    }
}
