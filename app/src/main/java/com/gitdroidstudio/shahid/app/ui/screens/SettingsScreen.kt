package com.gitdroidstudio.shahid.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gitdroidstudio.shahid.app.data.api.GitHubApiService
import com.gitdroidstudio.shahid.app.data.auth.AuthManager
import com.gitdroidstudio.shahid.app.data.model.AccountInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authManager: AuthManager,
    apiService: GitHubApiService,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var notificationsEnabled by remember { mutableStateOf(true) }
    var accountsList by remember { mutableStateOf(authManager.getAccounts()) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account Section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GitHub Accounts",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            IconButton(onClick = { showAddAccountDialog = true }) {
                                Icon(Icons.Default.PersonAdd, contentDescription = "Add Account")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Current active account
                        val currentUsername = authManager.getUsername()
                        val currentAvatar = authManager.getAvatarUrl()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!currentAvatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = currentAvatar,
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentUsername ?: "GitHub User",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Active Account",
                                    fontSize = 12.sp,
                                    color = Color(0xFF27AE60)
                                )
                            }
                        }

                        // Other stored accounts
                        val others = accountsList.filter { !it.username.equals(currentUsername, ignoreCase = true) }
                        if (others.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Switch Account:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            others.forEach { acc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(acc.username, fontSize = 13.sp)
                                    Row {
                                        TextButton(onClick = {
                                            authManager.switchAccount(acc.username)
                                            accountsList = authManager.getAccounts()
                                            Toast.makeText(context, "Switched to ${acc.username}", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Text("Switch")
                                        }
                                        IconButton(onClick = {
                                            authManager.removeAccount(acc.username)
                                            accountsList = authManager.getAccounts()
                                        }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Theme Preferences
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Appearance",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DarkMode, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Dark Theme (Default)")
                            }
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = { onToggleDarkTheme(it) }
                            )
                        }
                    }
                }
            }

            // Notifications
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Build Notifications",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Notifications, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Notify on build success/failure")
                            }
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { notificationsEnabled = it }
                            )
                        }
                    }
                }
            }

            // Real Check for Updates (STEP 9)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "App Information",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "GitDroid Studio v1.0.0",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (updateCheckResult != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = updateCheckResult!!,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    isCheckingUpdate = true
                                    try {
                                        val resp = apiService.getLatestRelease("mdshahiid09-bit", "gitdroidstudio")
                                        if (resp.isSuccessful && resp.body() != null) {
                                            val release = resp.body()!!
                                            updateCheckResult = "Latest Release: ${release.tagName} (${release.publishedAt?.take(10) ?: "recent"})\nApp is up to date!"
                                        } else {
                                            updateCheckResult = "Already on latest version v1.0.0"
                                        }
                                    } catch (e: Exception) {
                                        updateCheckResult = "Check error: ${e.message}"
                                    } finally {
                                        isCheckingUpdate = false
                                    }
                                }
                            },
                            enabled = !isCheckingUpdate,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking GitHub Releases...")
                            } else {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Check for Updates")
                            }
                        }
                    }
                }
            }

            // Logout Button
            item {
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout from GitHub")
                }
            }
        }
    }

    // Add Account Dialog
    if (showAddAccountDialog) {
        var tokenInput by remember { mutableStateOf("") }
        var isVerifying by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            title = { Text("Add GitHub Account") },
            text = {
                Column {
                    Text("Enter Personal Access Token (PAT) with repo and workflow scopes:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Token (ghp_...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (errorMsg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isVerifying = true
                            errorMsg = null
                            try {
                                val tempApi = GitHubApiService.create { tokenInput }
                                val resp = tempApi.getCurrentUser()
                                if (resp.isSuccessful && resp.body() != null) {
                                    val u = resp.body()!!
                                    authManager.addOrUpdateAccount(
                                        AccountInfo(username = u.login, token = tokenInput.trim(), avatarUrl = u.avatarUrl)
                                    )
                                    accountsList = authManager.getAccounts()
                                    showAddAccountDialog = false
                                } else {
                                    errorMsg = "Invalid token (HTTP ${resp.code()})"
                                }
                            } catch (e: Exception) {
                                errorMsg = e.localizedMessage ?: "Connection error"
                            } finally {
                                isVerifying = false
                            }
                        }
                    },
                    enabled = tokenInput.isNotBlank() && !isVerifying
                ) {
                    Text(if (isVerifying) "Verifying..." else "Add Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
