package com.gitdroidstudio.shahid.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitdroidstudio.shahid.app.ui.viewmodel.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    viewModel: ProjectViewModel,
    onNavigateBack: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val fileTree by viewModel.fileTree.collectAsState()
    val openTabs by viewModel.openTabs.collectAsState()
    val activeTabIndex by viewModel.activeTabIndex.collectAsState()
    val currentTab = viewModel.currentTab

    val statusMessage by viewModel.statusMessage.collectAsState()
    val buildStatus by viewModel.buildStatus.collectAsState()
    val buildLogs by viewModel.buildLogs.collectAsState()
    val workflowSteps by viewModel.workflowSteps.collectAsState()
    val canInstallApk by viewModel.canInstallApk.collectAsState()
    val activeBranch by viewModel.activeBranch.collectAsState()
    val branches by viewModel.branches.collectAsState()
    val pullRequests by viewModel.pullRequests.collectAsState()
    val conflictDialog by viewModel.conflictDialog.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val showMissingWorkflowDialog by viewModel.showMissingWorkflowDialog.collectAsState()
    val workflowDispatchError by viewModel.workflowDispatchError.collectAsState()

    var showBuildSheet by remember { mutableStateOf(false) }
    var showCommitDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf<String?>(null) }
    var showNewFolderDialog by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf<FileNode?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<FileNode?>(null) }
    var showBranchDialog by remember { mutableStateOf(false) }
    var showNewBranchDialog by remember { mutableStateOf(false) }
    var showPRDialog by remember { mutableStateOf(false) }
    var showCreatePRDialog by remember { mutableStateOf(false) }
    var showSearchReplaceBar by remember { mutableStateOf(false) }
    var showRepoSearchModal by remember { mutableStateOf(false) }
    var showSnippetsModal by remember { mutableStateOf(false) }
    var showDiffDialog by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var repoSearchQuery by remember { mutableStateOf("") }
    var activeMenu by remember { mutableStateOf<String?>(null) }

    // Haptic feedback on build events
    LaunchedEffect(buildStatus) {
        if (buildStatus == BuildStatus.RUNNING || buildStatus == BuildStatus.SUCCESS || buildStatus == BuildStatus.FAILURE) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Project Files",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(fileTree) { node ->
                        FileTreeNodeItem(
                            node = node,
                            depth = 0,
                            onNodeClick = { target ->
                                if (target.isDirectory) {
                                    viewModel.toggleFolder(target)
                                } else {
                                    viewModel.openFile(target)
                                    coroutineScope.launch { drawerState.close() }
                                }
                            },
                            onNewFileInFolder = { folderPath -> showNewFileDialog = folderPath },
                            onNewFolderInFolder = { folderPath -> showNewFolderDialog = folderPath },
                            onRename = { showRenameDialog = it },
                            onDelete = { showDeleteConfirmDialog = it }
                        )
                    }

                    // Long press empty space at the bottom to create at root
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clickable { showNewFileDialog = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+ Tap or long press to create file at root",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = viewModel.repo,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1
                                )
                                Text(
                                    text = "branch: ${'$'}activeBranch",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Files")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showSearchReplaceBar = !showSearchReplaceBar }) {
                                Icon(Icons.Default.FindReplace, contentDescription = "Search & Replace")
                            }
                            IconButton(
                                onClick = {
                                    showBuildSheet = true
                                    viewModel.triggerBuild()
                                }
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Build APK",
                                    tint = Color(0xFF27AE60)
                                )
                            }
                            IconButton(onClick = { showCommitDialog = true }) {
                                BadgedBox(
                                    badge = {
                                        if (currentTab?.isModified == true) {
                                            Badge { Text("●") }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = "Save & Push")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Desktop-style Top Menu Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("File", "Edit", "View", "Build", "Git", "Tools", "Settings", "Help").forEach { menuName ->
                            Box {
                                TextButton(
                                    onClick = { activeMenu = if (activeMenu == menuName) null else menuName },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        menuName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                DropdownMenu(
                                    expanded = activeMenu == menuName,
                                    onDismissRequest = { activeMenu = null }
                                ) {
                                    when (menuName) {
                                        "File" -> {
                                            DropdownMenuItem(
                                                text = { Text("New File") },
                                                onClick = { activeMenu = null; showNewFileDialog = "" }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("New Folder") },
                                                onClick = { activeMenu = null; showNewFolderDialog = "" }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Open Repository") },
                                                onClick = { activeMenu = null; onNavigateBack() }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Close Project") },
                                                onClick = { activeMenu = null; onNavigateBack() }
                                            )
                                        }
                                        "Edit" -> {
                                            DropdownMenuItem(
                                                text = { Text("Undo") },
                                                onClick = { activeMenu = null; viewModel.undo() }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Redo") },
                                                onClick = { activeMenu = null; viewModel.redo() }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Go to line") },
                                                onClick = { activeMenu = null; showGoToLineDialog = true }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Find & Replace") },
                                                onClick = { activeMenu = null; showSearchReplaceBar = true }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Search Code in Repo") },
                                                onClick = { activeMenu = null; showRepoSearchModal = true }
                                            )
                                        }
                                        "View" -> {
                                            DropdownMenuItem(
                                                text = { Text("Toggle File Tree") },
                                                onClick = {
                                                    activeMenu = null
                                                    coroutineScope.launch {
                                                        if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Build Output Panel") },
                                                onClick = { activeMenu = null; showBuildSheet = true }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Appearance") },
                                                onClick = { activeMenu = null; onNavigateSettings() }
                                            )
                                        }
                                        "Build" -> {
                                            DropdownMenuItem(
                                                text = { Text("Run GitHub Build") },
                                                onClick = {
                                                    activeMenu = null
                                                    showBuildSheet = true
                                                    viewModel.triggerBuild()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(if (viewModel.isAndroidProject()) "Download APK" else "Download Artifact") },
                                                onClick = {
                                                    activeMenu = null
                                                    viewModel.saveApkToDownloads(context)
                                                }
                                            )
                                            if (viewModel.isAndroidProject()) {
                                                DropdownMenuItem(
                                                    text = { Text("Install APK") },
                                                    onClick = {
                                                        activeMenu = null
                                                        viewModel.downloadAndInstallArtifact(context)
                                                    }
                                                )
                                            }
                                        }
                                        "Git" -> {
                                            DropdownMenuItem(
                                                text = { Text("Diff / Compare Changes") },
                                                onClick = { activeMenu = null; showDiffDialog = true }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Save & Commit") },
                                                onClick = { activeMenu = null; showCommitDialog = true }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Switch Branch") },
                                                onClick = { activeMenu = null; showBranchDialog = true }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("New Branch") },
                                                onClick = { activeMenu = null; showNewBranchDialog = true }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Pull Requests") },
                                                onClick = { activeMenu = null; showPRDialog = true }
                                            )
                                        }
                                        "Tools" -> {
                                            DropdownMenuItem(
                                                text = { Text("Code Snippets") },
                                                onClick = { activeMenu = null; showSnippetsModal = true }
                                            )
                                        }
                                        "Settings" -> {
                                            DropdownMenuItem(
                                                text = { Text("Preferences") },
                                                onClick = { activeMenu = null; onNavigateSettings() }
                                            )
                                        }
                                        "Help" -> {
                                            DropdownMenuItem(
                                                text = { Text("Documentation") },
                                                onClick = {
                                                    activeMenu = null
                                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mdshahiid09-bit/gitdroidstudio#readme"))
                                                    context.startActivity(browserIntent)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Report a Bug") },
                                                onClick = {
                                                    activeMenu = null
                                                    val issueIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mdshahiid09-bit/gitdroidstudio/issues/new"))
                                                    context.startActivity(issueIntent)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Multi-file Tab Bar
                    if (openTabs.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF141414))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            itemsIndexed(openTabs) { index, tab ->
                                val isSelected = index == activeTabIndex
                                Surface(
                                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                                    color = if (isSelected) Color(0xFF1E1E1E) else Color(0xFF181818),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383838)) else null,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clickable { viewModel.selectTab(index) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (tab.isModified) {
                                            Text("● ", color = MaterialTheme.colorScheme.tertiary, fontSize = 10.sp)
                                        }
                                        Text(
                                            text = tab.file.name,
                                            fontSize = 12.sp,
                                            color = if (isSelected) Color.White else Color.Gray,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close Tab",
                                            tint = Color.Gray,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { viewModel.closeTab(index) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Search & Replace Bar
                    AnimatedVisibility(visible = showSearchReplaceBar) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                val matchCount = remember(currentTab?.content, searchQuery) {
                                    if (searchQuery.isNotEmpty() && currentTab != null) {
                                        var count = 0
                                        var idx = 0
                                        while (idx < currentTab.content.length) {
                                            val found = currentTab.content.indexOf(searchQuery, idx, ignoreCase = true)
                                            if (found >= 0) {
                                                count++
                                                idx = found + searchQuery.length
                                            } else break
                                        }
                                        count
                                    } else 0
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Find text...") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (searchQuery.isNotEmpty()) {
                                        Text(
                                            text = "${'$'}matchCount found",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    IconButton(onClick = { showSearchReplaceBar = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close")
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = replaceQuery,
                                        onValueChange = { replaceQuery = it },
                                        placeholder = { Text("Replace with...") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = {
                                            currentTab?.let { tab ->
                                                if (searchQuery.isNotEmpty()) {
                                                    val replaced = tab.content.replace(searchQuery, replaceQuery)
                                                    viewModel.onContentChanged(replaced)
                                                    Toast.makeText(context, "Replaced occurrences", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Replace All")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Column {
                    // Auto-complete suggestion chips row
                    if (suggestions.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF202020))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(suggestions) { suggestion ->
                                SuggestionChip(
                                    onClick = { viewModel.applySuggestion(suggestion) },
                                    label = { Text(suggestion, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    // Mobile Symbol Shortcut Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161616))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("{", "}", "(", ")", ";", "=", "\"", "'", "<", ">", "/", "\t").forEach { symbol ->
                            TextButton(
                                onClick = { viewModel.insertSymbol(symbol) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (symbol == "\t") "Tab" else symbol,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64B5F6),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Status Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (currentTab?.isModified == true) Icons.Default.EditNote else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (currentTab?.isModified == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (currentTab?.isModified == true) "Unsaved changes" else statusMessage,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { showBuildSheet = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "Build: ${'$'}{buildStatus.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (buildStatus) {
                                            BuildStatus.RUNNING -> MaterialTheme.colorScheme.primary
                                            BuildStatus.SUCCESS -> Color(0xFF27AE60)
                                            BuildStatus.FAILURE -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFF1E1E1E))
            ) {
                if (currentTab == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Select a file from the drawer to edit",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Text("Open Project Files")
                            }
                        }
                    }
                } else {
                    CodeEditorView(
                        content = currentTab.content,
                        fileName = currentTab.file.name,
                        onContentChange = { viewModel.onContentChanged(it) }
                    )
                }
            }
        }
    }

    // Build Logs Sliding Panel (ModalBottomSheet)
    if (showBuildSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBuildSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GitHub Actions Live Pipeline",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (buildStatus == BuildStatus.RUNNING) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }

                // Workflow Steps Status
                if (workflowSteps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(workflowSteps) { step ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text("${'$'}{step.name}: ${'$'}{step.conclusion ?: step.status}", fontSize = 10.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (step.conclusion == "success") Color(0xFF1B5E20) else Color(0xFF282828)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F0F0F))
                        .padding(12.dp)
                ) {
                    val logScroll = rememberScrollState()
                    LaunchedEffect(buildLogs) {
                        logScroll.scrollTo(logScroll.maxValue)
                    }
                    Text(
                        text = buildLogs.ifBlank { "Initializing GitHub Actions runner...\nWaiting for step execution..." },
                        color = Color(0xFF00FF66),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.verticalScroll(logScroll)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.triggerBuild() },
                        enabled = buildStatus != BuildStatus.RUNNING,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rebuild")
                    }

                    if (canInstallApk) {
                        if (viewModel.isAndroidProject()) {
                            Button(
                                onClick = { viewModel.downloadAndInstallArtifact(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install APK")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.saveApkToDownloads(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download Artifact")
                            }
                        }
                    }
                }
            }
        }
    }

    // Git Diff Viewer Dialog
    if (showDiffDialog) {
        val diffLines = remember(currentTab) { viewModel.getDiffForCurrentTab() }
        AlertDialog(
            onDismissRequest = { showDiffDialog = false },
            title = {
                Text(
                    text = "Git Diff: ${'$'}{currentTab?.file?.name ?: "No file"}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Comparing local buffer against GitHub HEAD commit.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (diffLines.isEmpty() || diffLines.all { it.type == DiffType.UNCHANGED }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No changes. File is identical to remote.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF141414))
                                .padding(6.dp)
                        ) {
                            items(diffLines) { line ->
                                val bgColor = when (line.type) {
                                    DiffType.ADDED -> Color(0xFF1B5E20).copy(alpha = 0.5f)
                                    DiffType.DELETED -> Color(0xFFB71C1C).copy(alpha = 0.5f)
                                    DiffType.UNCHANGED -> Color.Transparent
                                }
                                val textColor = when (line.type) {
                                    DiffType.ADDED -> Color(0xFF81C784)
                                    DiffType.DELETED -> Color(0xFFE57373)
                                    DiffType.UNCHANGED -> Color(0xFFB0BEC5)
                                }
                                val prefix = when (line.type) {
                                    DiffType.ADDED -> "+ "
                                    DiffType.DELETED -> "- "
                                    DiffType.UNCHANGED -> "  "
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bgColor)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = prefix + line.text,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDiffDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Go to Line Dialog
    if (showGoToLineDialog) {
        var lineInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGoToLineDialog = false },
            title = { Text("Go to Line") },
            text = {
                OutlinedTextField(
                    value = lineInput,
                    onValueChange = { lineInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Line Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = lineInput.toIntOrNull()
                        if (num != null) {
                            Toast.makeText(context, "Navigating to line ${'$'}num", Toast.LENGTH_SHORT).show()
                            showGoToLineDialog = false
                        }
                    }
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoToLineDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Missing Workflow Dialog
    if (showMissingWorkflowDialog) {
        var buildCmd by remember { mutableStateOf("./gradlew assembleDebug --stacktrace") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissMissingWorkflowDialog() },
            title = { Text("Add Build Workflow?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Workflow '.github/workflows/build.yml' was not found in this repository. Would you like GitDroid Studio to generate a GitHub Actions workflow to build your project?",
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = buildCmd,
                        onValueChange = { buildCmd = it },
                        label = { Text("Build Command") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.createWorkflowAndBuild(buildCmd) }) {
                    Text("Generate & Build")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissMissingWorkflowDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Workflow Dispatch Error Dialog
    workflowDispatchError?.let { errText ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissWorkflowDispatchError() },
            title = { Text("Build Dispatch Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text(errText, fontSize = 13.sp) },
            confirmButton = {
                Button(onClick = { viewModel.dismissWorkflowDispatchError() }) {
                    Text("Dismiss")
                }
            }
        )
    }

    // 409 Conflict Dialog
    conflictDialog?.let { conflict ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Version Conflict (409)") },
            text = { Text("This file was modified on GitHub by another commit. Reload the remote version or overwrite with your local version?") },
            confirmButton = {
                Button(onClick = { viewModel.resolveConflict(keepLocal = true) }) {
                    Text("Keep My Version")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.resolveConflict(keepLocal = false) }) {
                    Text("Reload Remote")
                }
            }
        )
    }

    // Commit Message Dialog
    if (showCommitDialog) {
        var commitMsg by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            title = { Text("Commit & Push Changes") },
            text = {
                OutlinedTextField(
                    value = commitMsg,
                    onValueChange = { commitMsg = it },
                    placeholder = { Text("Update ${'$'}{currentTab?.file?.name ?: "file"}") },
                    label = { Text("Commit Message") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCommitDialog = false
                        viewModel.saveCurrentFile(commitMsg)
                    }
                ) {
                    Text("Push to GitHub")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // New File Dialog
    showNewFileDialog?.let { basePath ->
        var fileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = null },
            title = { Text("Create New File") },
            text = {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    placeholder = { Text(if (basePath.isEmpty()) "app/src/main/Example.kt" else "${'$'}basePath/NewFile.kt") },
                    label = { Text("File Path") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fileName.isNotBlank()) {
                            val fullPath = if (basePath.isEmpty()) fileName else "${'$'}basePath/${'$'}fileName"
                            viewModel.createFile(fullPath)
                            showNewFileDialog = null
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // New Folder Dialog
    showNewFolderDialog?.let { basePath ->
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = null },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it.filter { c -> c.isLetterOrDigit() || c == '/' || c == '_' || c == '-' } },
                    placeholder = { Text("my_package") },
                    label = { Text("Folder Name / Path") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            val fullPath = if (basePath.isEmpty()) folderName else "${'$'}basePath/${'$'}folderName"
                            viewModel.createFolder(fullPath)
                            showNewFolderDialog = null
                        }
                    }
                ) {
                    Text("Create Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Dialog
    showRenameDialog?.let { node ->
        var newPath by remember { mutableStateOf(node.path) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename ${'$'}{node.name}") },
            text = {
                OutlinedTextField(
                    value = newPath,
                    onValueChange = { newPath = it },
                    label = { Text("New Path") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameFile(node, newPath)
                        showRenameDialog = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete ${'$'}{target.name}?") },
            text = {
                Text(
                    if (target.isDirectory) "Are you sure you want to delete this folder and ALL files inside it? This will push deletion commits to GitHub."
                    else "Are you sure you want to delete this file? This will immediately push a deletion commit to GitHub."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (target.isDirectory) {
                            viewModel.deleteFolder(target)
                        } else {
                            viewModel.deleteFile(target)
                        }
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Branch Switcher Dialog
    if (showBranchDialog) {
        AlertDialog(
            onDismissRequest = { showBranchDialog = false },
            title = { Text("Switch Branch") },
            text = {
                LazyColumn {
                    items(branches) { branch ->
                        ListItem(
                            headlineContent = { Text(branch.name) },
                            leadingContent = {
                                RadioButton(
                                    selected = branch.name == activeBranch,
                                    onClick = {
                                        viewModel.switchBranch(branch.name)
                                        showBranchDialog = false
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.switchBranch(branch.name)
                                showBranchDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBranchDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // New Branch Dialog
    if (showNewBranchDialog) {
        var branchName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewBranchDialog = false },
            title = { Text("Create New Branch") },
            text = {
                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it.filter { char -> char.isLetterOrDigit() || char == '-' || char == '_' } },
                    label = { Text("Branch Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (branchName.isNotBlank()) {
                            viewModel.createBranch(branchName)
                            showNewBranchDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewBranchDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Pull Requests Dialog
    if (showPRDialog) {
        AlertDialog(
            onDismissRequest = { showPRDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pull Requests")
                    IconButton(onClick = { showCreatePRDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New PR")
                    }
                }
            },
            text = {
                if (pullRequests.isEmpty()) {
                    Text("No open pull requests.", color = Color.Gray)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(pullRequests) { pr ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("#${'$'}{pr.number}: ${'$'}{pr.title}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("By: ${'$'}{pr.user?.login ?: "user"}", fontSize = 11.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = { viewModel.mergePullRequest(pr.number) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6F42C1)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Merge PR #${'$'}{pr.number}")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPRDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Code Search in Repo Modal
    if (showRepoSearchModal) {
        AlertDialog(
            onDismissRequest = { showRepoSearchModal = false },
            title = { Text("Search Code in ${'$'}repo") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = repoSearchQuery,
                            onValueChange = { repoSearchQuery = it },
                            placeholder = { Text("Search code...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = { viewModel.searchAcrossRepo(repoSearchQuery) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(searchResults) { item ->
                            ListItem(
                                headlineContent = { Text(item.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(item.path, fontSize = 11.sp, color = Color.Gray) }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRepoSearchModal = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Snippets Modal
    if (showSnippetsModal) {
        val snippets = listOf(
            "fun main() {\n    println(\"Hello\")\n}" to "main function",
            "@Composable\nfun MyScreen() {\n    Text(\"Hello\")\n}" to "Composable Screen",
            "val client = OkHttpClient()\nval request = Request.Builder().url(url).build()" to "OkHttp GET",
            "data class User(\n    val id: Long,\n    val name: String\n)" to "Data Class"
        )
        AlertDialog(
            onDismissRequest = { showSnippetsModal = false },
            title = { Text("Code Snippets") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snippets) { (code, label) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.insertSymbol("\n${'$'}code\n")
                                    showSnippetsModal = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(code, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.LightGray)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSnippetsModal = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTreeNodeItem(
    node: FileNode,
    depth: Int,
    onNodeClick: (FileNode) -> Unit,
    onNewFileInFolder: (String) -> Unit,
    onNewFolderInFolder: (String) -> Unit,
    onRename: (FileNode) -> Unit,
    onDelete: (FileNode) -> Unit
) {
    var expandedContextMenu by remember { mutableStateOf(false) }

    Column {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onNodeClick(node) },
                        onLongClick = { expandedContextMenu = true }
                    )
                    .padding(start = (depth * 14).dp, top = 6.dp, bottom = 6.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (node.isDirectory) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(if (node.isExpanded) 90f else 0f),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Icon(
                    imageVector = if (node.isDirectory) {
                        if (node.isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder
                    } else {
                        when {
                            node.name.endsWith(".kt") -> Icons.Default.Code
                            node.name.endsWith(".xml") -> Icons.Default.Description
                            node.name.endsWith(".gradle.kts") -> Icons.Default.Build
                            else -> Icons.Default.InsertDriveFile
                        }
                    },
                    contentDescription = null,
                    tint = if (node.isDirectory) MaterialTheme.colorScheme.primary else Color.LightGray,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = node.name,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (!node.isDirectory) {
                    IconButton(
                        onClick = { onDelete(node) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = expandedContextMenu,
                onDismissRequest = { expandedContextMenu = false }
            ) {
                if (node.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("New File Here") },
                        onClick = {
                            expandedContextMenu = false
                            onNewFileInFolder(node.path)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("New Folder Here") },
                        onClick = {
                            expandedContextMenu = false
                            onNewFolderInFolder(node.path)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        expandedContextMenu = false
                        onRename(node)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        expandedContextMenu = false
                        onDelete(node)
                    }
                )
            }
        }

        if (node.isExpanded && node.children.isNotEmpty()) {
            node.children.forEach { child ->
                FileTreeNodeItem(
                    node = child,
                    depth = depth + 1,
                    onNodeClick = onNodeClick,
                    onNewFileInFolder = onNewFileInFolder,
                    onNewFolderInFolder = onNewFolderInFolder,
                    onRename = onRename,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
fun CodeEditorView(
    content: String,
    fileName: String,
    onContentChange: (String) -> Unit
) {
    val lineCount = remember(content) {
        val lines = content.count { it == '\n' } + 1
        lines.coerceAtLeast(1)
    }

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(verticalScroll)
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFF161616))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End
        ) {
            for (i in 1..lineCount) {
                Text(
                    text = "$i",
                    color = Color.DarkGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(horizontalScroll)
                .padding(8.dp)
        ) {
            BasicTextField(
                value = content,
                onValueChange = onContentChange,
                textStyle = TextStyle(
                    color = Color(0xFFD4D4D4),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                cursorBrush = SolidColor(Color.White),
                visualTransformation = remember(fileName) { CodeSyntaxVisualTransformation(fileName) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

class CodeSyntaxVisualTransformation(private val fileName: String) : VisualTransformation {

    private val kotlinKeywords = setOf(
        "package", "import", "class", "interface", "fun", "val", "var", "if", "else",
        "return", "for", "while", "when", "data", "object", "sealed", "private", "public",
        "protected", "override", "suspend", "companion", "true", "false", "null"
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = buildAnnotatedString {
            append(raw)

            val keywordPattern = "\\b(${'$'}{kotlinKeywords.joinToString("|")})\\b".toRegex()
            for (match in keywordPattern.findAll(raw)) {
                addStyle(
                    SpanStyle(color = Color(0xFFC586C0), fontWeight = FontWeight.SemiBold),
                    match.range.first,
                    match.range.last + 1
                )
            }

            val stringPattern = "\"[^\"]*\"".toRegex()
            for (match in stringPattern.findAll(raw)) {
                addStyle(
                    SpanStyle(color = Color(0xFFCE9178)),
                    match.range.first,
                    match.range.last + 1
                )
            }

            val commentPattern = "//.*".toRegex()
            for (match in commentPattern.findAll(raw)) {
                addStyle(
                    SpanStyle(color = Color(0xFF6A9955)),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }

        return TransformedText(builder, OffsetMapping.Identity)
    }
}
