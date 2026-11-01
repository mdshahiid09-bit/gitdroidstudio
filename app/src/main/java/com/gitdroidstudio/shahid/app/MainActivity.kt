package com.gitdroidstudio.shahid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gitdroidstudio.shahid.app.data.api.GitHubApiService
import com.gitdroidstudio.shahid.app.data.auth.AuthManager
import com.gitdroidstudio.shahid.app.ui.screens.*
import com.gitdroidstudio.shahid.app.ui.theme.GitDroidStudioTheme
import com.gitdroidstudio.shahid.app.ui.viewmodel.AuthViewModel
import com.gitdroidstudio.shahid.app.ui.viewmodel.HomeViewModel
import com.gitdroidstudio.shahid.app.ui.viewmodel.ProjectViewModel

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(applicationContext)

        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }

            GitDroidStudioTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "splash"
                    ) {
                        composable("splash") {
                            SplashScreen(
                                isLoggedIn = authManager.isLoggedIn(),
                                onNavigateNext = { isLoggedIn ->
                                    if (isLoggedIn) {
                                        navController.navigate("home") {
                                            popUpTo("splash") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("login") {
                                            popUpTo("splash") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable("login") {
                            val authViewModel = remember {
                                AuthViewModel(
                                    authManager = authManager,
                                    apiServiceFactory = { token -> GitHubApiService.create { token } }
                                )
                            }
                            LoginScreen(
                                viewModel = authViewModel,
                                onLoginSuccess = {
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("home") {
                            val homeViewModel = remember {
                                val api = GitHubApiService.create { authManager.getToken() }
                                HomeViewModel(api)
                            }

                            HomeScreen(
                                viewModel = homeViewModel,
                                currentUserAvatar = authManager.getAvatarUrl(),
                                currentUsername = authManager.getUsername(),
                                onOpenRepo = { owner, repo, branch ->
                                    navController.navigate("project/$owner/$repo/$branch")
                                },
                                onOpenSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }

                        composable(
                            route = "project/{owner}/{repo}/{branch}",
                            arguments = listOf(
                                navArgument("owner") { type = NavType.StringType },
                                navArgument("repo") { type = NavType.StringType },
                                navArgument("branch") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val owner = backStackEntry.arguments?.getString("owner") ?: ""
                            val repo = backStackEntry.arguments?.getString("repo") ?: ""
                            val branch = backStackEntry.arguments?.getString("branch") ?: "main"

                            val projectViewModel = remember(owner, repo, branch) {
                                val api = GitHubApiService.create { authManager.getToken() }
                                ProjectViewModel(
                                    owner = owner,
                                    repo = repo,
                                    defaultBranch = branch,
                                    apiService = api,
                                    authToken = authManager.getToken()
                                )
                            }

                            ProjectScreen(
                                viewModel = projectViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateSettings = { navController.navigate("settings") }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                authManager = authManager,
                                isDarkTheme = isDarkTheme,
                                onToggleDarkTheme = { isDarkTheme = it },
                                onNavigateBack = { navController.popBackStack() },
                                onLogout = {
                                    authManager.logout()
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
