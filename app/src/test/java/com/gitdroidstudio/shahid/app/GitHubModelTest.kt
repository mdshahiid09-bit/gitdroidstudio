package com.gitdroidstudio.shahid.app

import com.gitdroidstudio.shahid.app.data.model.GitHubUser
import com.gitdroidstudio.shahid.app.data.model.Repository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GitHubModelTest {

    @Test
    fun testRepositoryModelCreation() {
        val user = GitHubUser(
            login = "mdshahiid09-bit",
            id = 12345L,
            avatarUrl = "https://avatars.githubusercontent.com/u/307735745?v=4",
            name = "Shahid",
            bio = "Android Developer",
            publicRepos = 10
        )

        val repo = Repository(
            id = 999L,
            name = "gitdroidstudio",
            fullName = "mdshahiid09-bit/gitdroidstudio",
            description = "Native Android IDE",
            private = false,
            htmlUrl = "https://github.com/mdshahiid09-bit/gitdroidstudio",
            defaultBranch = "main",
            updatedAt = "2026-11-01T12:00:00Z",
            owner = user
        )

        assertEquals("gitdroidstudio", repo.name)
        assertEquals("mdshahiid09-bit", repo.owner?.login)
        assertEquals("main", repo.defaultBranch)
        assertNotNull(repo.htmlUrl)
    }
}
