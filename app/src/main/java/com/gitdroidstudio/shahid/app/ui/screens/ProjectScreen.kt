package com.gitdroidstudio.shahid.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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
import com.gitdroidstudio.shahid.app.ui.viewmodel.BuildStatus
import com.gitdroidstudio.shahid.app.ui.viewmodel.FileNode
import com.gitdroidstudio.shahid.app.ui.viewmodel.ProjectViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    viewModel: ProjectViewModel,
    onNavigateBack: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val fileTree by viewModel.fileTree.collectAsState()
    val currentFile by viewModel.currentFile.collectAsState()
    val editorContent by viewModel.editorContent.collectAsState()
    val isModified by viewModel.isModified.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val buildStatus by viewModel.buildStatus.collectAsState()
    val buildLogs by viewModel.buildLogs.collectAsState()
    val activeBranch by viewModel.activeBranch.collectAsState()
    val branches by viewModel.branches.collectAsState()

    var showBuildSheet by remember { mutableStateOf(false) }
    var showCommitDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<FileNode?>(null) }
    var showBranchDialog by remember { mutableStateOf(false) }
    var showSearchReplaceBar by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    // Dropdown menus for Top Bar
    var activeMenu by remember { mutableStateOf<String?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Project Files",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { showNewFileDialog = true }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "New File")
                    }
                }
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(fileTree) { node ->
                        FileTreeNodeItem(
                            node = node,
                            depth = 0,
                            onNodeClick = { target ->
                                if (target.isDirectory) {
                                    viewModel.expandFolder(target)
                                } else {
                                    viewModel.openFile(target)
                                    coroutineScope.launch { drawerState.close() }
                                }
                            },
                            onDelete = { showDeleteConfirmDialog = it }
                        )
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
                                    text = "branch: $activeBranch",
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
                                        if (isModified) {
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

                    // Desktop-style Top Menu Bar (File, Edit, View, Build, Git, Settings)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("File", "Edit", "View", "Build", "Git", "Settings").forEach { menuName ->
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
                                                onClick = { activeMenu = null; showNewFileDialog = true }
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
                                                text = { Text("Find & Replace") },
                                                onClick = { activeMenu = null; showSearchReplaceBar = true }
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
                                        }
                                        "Git" -> {
                                            DropdownMenuItem(
                                                text = { Text("Save & Commit") },
                                                onClick = { activeMenu = null; showCommitDialog = true }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Switch Branch") },
                                                onClick = { activeMenu = null; showBranchDialog = true }
                                            )
                                        }
                                        "Settings" -> {
                                            DropdownMenuItem(
                                                text = { Text("Preferences") },
                                                onClick = { activeMenu = null; onNavigateSettings() }
                                            )
                                        }
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Find text...") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
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
                                            if (searchQuery.isNotEmpty()) {
                                                val replaced = editorContent.replace(searchQuery, replaceQuery)
                                                viewModel.onContentChanged(replaced)
                                                Toast.makeText(context, "Replaced occurrences", Toast.LENGTH_SHORT).show()
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
                                imageVector = if (isModified) Icons.Default.EditNote else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isModified) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isModified) "Unsaved changes" else statusMessage,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { showBuildSheet = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Build: ${buildStatus.name}",
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
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFF1E1E1E))
            ) {
                if (currentFile == null) {
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
                                text = "Select a file from the menu to edit",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Text("Open File Tree")
                            }
                        }
                    }
                } else {
                    // Code Editor with Line Numbers
                    CodeEditorView(
                        content = editorContent,
                        fileName = currentFile?.name ?: "",
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
                    .fillMaxHeight(0.75f)
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
                            text = "GitHub Actions Live Logs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (buildStatus == BuildStatus.RUNNING) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Build Log Output Terminal
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
                        text = buildLogs.ifBlank { "Initializing GitHub Actions build runner...\nPlease wait for logs to stream." },
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
                        Text("Trigger Rebuild")
                    }

                    if (buildStatus == BuildStatus.SUCCESS) {
                        Button(
                            onClick = {
                                val releaseApkUrl = "https://github.com/${viewModel.owner}/${viewModel.repo}/releases/latest/download/app-debug.apk"
                                viewModel.downloadAndInstallApk(context, releaseApkUrl)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("1-Tap Install APK")
                        }
                    }
                }
            }
        }
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
                    placeholder = { Text("Update ${currentFile?.name ?: "file"}") },
                    label = { Text("Commit Message") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCommitDialog = false
                        viewModel.saveAndCommit(commitMsg)
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
    if (showNewFileDialog) {
        var newFilePath by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create New File") },
            text = {
                OutlinedTextField(
                    value = newFilePath,
                    onValueChange = { newFilePath = it },
                    placeholder = { Text("app/src/main/java/Example.kt") },
                    label = { Text("File Path") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFilePath.isNotBlank()) {
                            viewModel.createFile(newFilePath)
                            showNewFileDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete ${target.name}?") },
            text = { Text("Are you sure you want to delete this file? This will immediately push a deletion commit to GitHub.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFile(target)
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
}

@Composable
fun FileTreeNodeItem(
    node: FileNode,
    depth: Int,
    onNodeClick: (FileNode) -> Unit,
    onDelete: (FileNode) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNodeClick(node) }
                .padding(start = (depth * 14).dp, top = 6.dp, bottom = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        if (node.isExpanded && node.children.isNotEmpty()) {
            node.children.forEach { child ->
                FileTreeNodeItem(
                    node = child,
                    depth = depth + 1,
                    onNodeClick = onNodeClick,
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
        // Line Numbers Gutter
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

        // Code Editor Text Area with Syntax Highlighting Visual Transformation
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

/**
 * VisualTransformation providing syntax coloring for Kotlin, Java, XML, JSON, Markdown
 */
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

            val keywordPattern = "\\b(${kotlinKeywords.joinToString("|")})\\b".toRegex()
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
