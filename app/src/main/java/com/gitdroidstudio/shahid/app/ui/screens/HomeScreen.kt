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
import com.gitdroidstudio.shahid.app.data.model.Repository
import com.gitdroidstudio.shahid.app.ui.dialogs.CreateProjectDialog
import com.gitdroidstudio.shahid.app.ui.viewmodel.HomeUiState
import com.gitdroidstudio.shahid.app.ui.viewmodel.HomeViewModel
import com.gitdroidstudio.shahid.app.ui.viewmodel.RepoSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    currentUserAvatar: String?,
    currentUsername: String?,
    onOpenRepo: (owner: String, repo: String, branch: String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val pinnedRepos by viewModel.pinnedRepos.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showOpenRepoDialog by remember { mutableStateOf(false) }

    // Repo action dialogs
    var repoToRename by remember { mutableStateOf<Repository?>(null) }
    var repoToChangeVisibility by remember { mutableStateOf<Repository?>(null) }
    var repoToDelete by remember { mutableStateOf<Repository?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GitDroid Studio",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        if (!currentUserAvatar.isNullOrBlank()) {
                            AsyncImage(
                                model = currentUserAvatar,
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Project") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Android Studio Welcome Header Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Welcome, ${'$'}{currentUsername ?: "Developer"} 👋",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Build & edit Android projects directly backed by GitHub cloud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showCreateDialog = true },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Project")
                        }
                        OutlinedButton(
                            onClick = { showOpenRepoDialog = true },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Repository")
                        }
                    }
                }
            }

            // Search Bar
            val currentQuery = (uiState as? HomeUiState.Success)?.searchQuery ?: ""
            OutlinedTextField(
                value = currentQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search your GitHub repositories...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (currentQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Section Header with Sort Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Repositories",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )

                TextButton(
                    onClick = { viewModel.toggleSort() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (sortOption == RepoSort.UPDATED) "Sort: Updated" else "Sort: Name",
                        fontSize = 12.sp
                    )
                }
            }

            // Content List
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is HomeUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = state.message, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.loadRepositories() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is HomeUiState.Success -> {
                    if (state.filteredRepos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FolderOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (state.searchQuery.isEmpty()) "No repositories found for this GitHub account." else "No matching repositories.",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(onClick = { showCreateDialog = true }) {
                                    Text("Create First Project")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.filteredRepos, key = { it.id }) { repo ->
                                val isPinned = pinnedRepos.contains(repo.fullName)
                                RepositoryItem(
                                    repo = repo,
                                    isPinned = isPinned,
                                    onClick = {
                                        onOpenRepo(
                                            repo.owner?.login ?: "",
                                            repo.name,
                                            repo.defaultBranch
                                        )
                                    },
                                    onTogglePin = { viewModel.togglePin(repo.fullName) },
                                    onRename = { repoToRename = repo },
                                    onChangeVisibility = { repoToChangeVisibility = repo },
                                    onDelete = { repoToDelete = repo }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc, isPrivate, template ->
                showCreateDialog = false
                viewModel.createProject(
                    name = name,
                    description = desc,
                    isPrivate = isPrivate,
                    template = template,
                    onSuccess = { newRepo ->
                        onOpenRepo(
                            newRepo.owner?.login ?: "",
                            newRepo.name,
                            newRepo.defaultBranch
                        )
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    // Real Open Repository Picker Dialog
    if (showOpenRepoDialog) {
        var ownerInput by remember { mutableStateOf(currentUsername ?: "") }
        var repoInput by remember { mutableStateOf("") }
        var isOpening by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showOpenRepoDialog = false },
            title = { Text("Open Repository", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = ownerInput,
                        onValueChange = { ownerInput = it },
                        label = { Text("Owner (Username or Org)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        label = { Text("Repository Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isOpening = true
                        viewModel.openSpecificRepo(
                            owner = ownerInput,
                            repoName = repoInput,
                            onSuccess = { targetRepo ->
                                isOpening = false
                                showOpenRepoDialog = false
                                onOpenRepo(
                                    targetRepo.owner?.login ?: ownerInput.trim(),
                                    targetRepo.name,
                                    targetRepo.defaultBranch
                                )
                            },
                            onError = { err ->
                                isOpening = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = ownerInput.isNotBlank() && repoInput.isNotBlank() && !isOpening
                ) {
                    Text(if (isOpening) "Opening..." else "Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOpenRepoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Repository Dialog (STEP 4)
    repoToRename?.let { targetRepo ->
        var newRepoName by remember { mutableStateOf(targetRepo.name) }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { repoToRename = null },
            title = { Text("Rename Repository", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a new name for repository ${'$'}{targetRepo.name}:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = newRepoName,
                        onValueChange = { newRepoName = it.filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' } },
                        label = { Text("New Repository Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmitting = true
                        val ownerLogin = targetRepo.owner?.login ?: currentUsername ?: ""
                        viewModel.renameRepo(
                            owner = ownerLogin,
                            oldName = targetRepo.name,
                            newName = newRepoName,
                            onSuccess = {
                                isSubmitting = false
                                repoToRename = null
                                Toast.makeText(context, "Repository renamed to ${'$'}newRepoName", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                isSubmitting = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = newRepoName.isNotBlank() && newRepoName != targetRepo.name && !isSubmitting
                ) {
                    Text(if (isSubmitting) "Renaming..." else "Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { repoToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Change Visibility Dialog (STEP 4)
    repoToChangeVisibility?.let { targetRepo ->
        var isSubmitting by remember { mutableStateOf(false) }
        val targetIsPrivate = !targetRepo.private

        AlertDialog(
            onDismissRequest = { repoToChangeVisibility = null },
            title = {
                Text(
                    if (targetIsPrivate) "Make Repository Private?" else "Make Repository Public?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (targetIsPrivate) {
                        "Only you and authorized collaborators will be able to view this repository on GitHub."
                    } else {
                        "⚠️ WARNING: Making this repository PUBLIC will allow ANYONE on GitHub to view, clone, and fork your code."
                    },
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmitting = true
                        val ownerLogin = targetRepo.owner?.login ?: currentUsername ?: ""
                        viewModel.changeVisibility(
                            owner = ownerLogin,
                            repo = targetRepo.name,
                            isPrivate = targetIsPrivate,
                            onSuccess = {
                                isSubmitting = false
                                repoToChangeVisibility = null
                                Toast.makeText(
                                    context,
                                    "Repository is now ${'$'}{if (targetIsPrivate) "Private" else "Public"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onError = { err ->
                                isSubmitting = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = !isSubmitting,
                    colors = if (!targetIsPrivate) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                             else ButtonDefaults.buttonColors()
                ) {
                    Text(if (isSubmitting) "Updating..." else if (targetIsPrivate) "Make Private" else "Make Public")
                }
            },
            dismissButton = {
                TextButton(onClick = { repoToChangeVisibility = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Exact-Name Confirmation Delete Dialog (STEP 4)
    repoToDelete?.let { targetRepo ->
        var confirmationName by remember { mutableStateOf("") }
        var isDeleting by remember { mutableStateOf(false) }
        val isNameMatched = confirmationName.trim() == targetRepo.name

        AlertDialog(
            onDismissRequest = { repoToDelete = null },
            title = {
                Text("Delete Repository?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This action CANNOT be undone. This will permanently delete the repository '${'$'}{targetRepo.fullName}', including all commits, branches, issues, and workflows.",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "To confirm, please type the exact repository name '${'$'}{targetRepo.name}' below:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedTextField(
                        value = confirmationName,
                        onValueChange = { confirmationName = it },
                        placeholder = { Text(targetRepo.name) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        val ownerLogin = targetRepo.owner?.login ?: currentUsername ?: ""
                        viewModel.deleteRepo(
                            owner = ownerLogin,
                            repo = targetRepo.name,
                            onSuccess = {
                                isDeleting = false
                                repoToDelete = null
                                Toast.makeText(context, "Repository ${'$'}{targetRepo.name} deleted.", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                isDeleting = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = isNameMatched && !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isDeleting) "Deleting..." else "Delete Repository")
                }
            },
            dismissButton = {
                TextButton(onClick = { repoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RepositoryItem(
    repo: Repository,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onChangeVisibility: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (repo.private) Icons.Default.Lock else Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                if (repo.private) "Private" else "Public",
                                fontSize = 10.sp
                            )
                        },
                        modifier = Modifier.height(22.dp)
                    )

                    if (isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (!repo.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = repo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                val relativeTime = HomeViewModel.formatRelativeTime(repo.updatedAt)
                Text(
                    text = "Branch: ${'$'}{repo.defaultBranch} • Updated: ${'$'}relativeTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Repo actions",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Project") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isPinned) "Unpin from Top" else "Pin to Top") },
                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (repo.private) "Make Public" else "Make Private") },
                        leadingIcon = { Icon(if (repo.private) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onChangeVisibility()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete Repository", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
