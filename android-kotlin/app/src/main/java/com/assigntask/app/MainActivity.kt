package com.assigntask.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseUser
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val authState by vm.authState.collectAsState()
    val error by vm.errorMessage.collectAsState()

    when (val state = authState) {
        is AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is AuthState.LoggedOut -> AuthScreen(vm)
        is AuthState.LoggedIn -> MainAppScreen(vm, state.user, state.profile)
    }
}

@Composable
fun MainAppScreen(vm: AppViewModel, user: FirebaseUser, profile: UserProfile) {
    var currentPage by remember { mutableStateOf<String>("tasksForAll") }
    var drawerOpen by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerContent = {
            NavigationDrawerContent(
                currentPage = currentPage,
                onPageSelect = { page ->
                    currentPage = page
                    drawerOpen = false
                },
                profile = profile,
                vm = vm,
                user = user,
                onLogout = { vm.signOut() }
            )
        },
        drawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        drawerOpen = (drawerState.isOpen)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(pageTitle(currentPage)) },
                    navigationIcon = {
                        IconButton(onClick = { drawerOpen = !drawerOpen }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (currentPage) {
                    "tasksForAll" -> TasksForAllPage(vm, user)
                    "individualTasks" -> IndividualTasksPage(vm, user)
                    "projects" -> ProjectsPage(vm, user)
                    "profile" -> UserProfilePage(profile, vm, onBack = { currentPage = "tasksForAll" })
                }
            }
        }
    }
}

@Composable
fun NavigationDrawerContent(
    currentPage: String,
    onPageSelect: (String) -> Unit,
    profile: UserProfile,
    vm: AppViewModel,
    user: FirebaseUser,
    onLogout: () -> Unit
) {
    ModalDrawerSheet {
        Spacer(Modifier.height(16.dp))
        Text("Project & Task Board", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        HorizontalDivider()
        
        NavigationDrawerItem(
            label = { Text("Tasks For All") },
            icon = { Icon(Icons.Default.ListAlt, null) },
            selected = currentPage == "tasksForAll",
            onClick = { onPageSelect("tasksForAll") }
        )
        
        NavigationDrawerItem(
            label = { Text("Individual Tasks") },
            icon = { Icon(Icons.Default.Assignment, null) },
            selected = currentPage == "individualTasks",
            onClick = { onPageSelect("individualTasks") }
        )
        
        NavigationDrawerItem(
            label = { Text("Projects") },
            icon = { Icon(Icons.Default.Folder, null) },
            selected = currentPage == "projects",
            onClick = { onPageSelect("projects") }
        )
        
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        
        NavigationDrawerItem(
            label = { Text("User Profile") },
            icon = { Icon(Icons.Default.Person, null) },
            selected = currentPage == "profile",
            onClick = { onPageSelect("profile") }
        )
        
        Spacer(Modifier.weight(1f))
        HorizontalDivider()
        
        NavigationDrawerItem(
            label = { Text("Sign Out") },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
            selected = false,
            onClick = onLogout
        )
        Spacer(Modifier.height(16.dp))
    }
}

fun pageTitle(page: String): String = when (page) {
    "tasksForAll" -> "Tasks For All"
    "individualTasks" -> "Individual Tasks"
    "projects" -> "Projects"
    "profile" -> "User Profile"
    else -> "Project & Task Board"
}

@Composable
fun TasksForAllPage(vm: AppViewModel, user: FirebaseUser) {
    val tasksForAll by vm.tasksForAll.collectAsState()
    val staff by vm.staff.collectAsState()
    val error by vm.errorMessage.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf<Task?>(null) }
    var showEditTask by remember { mutableStateOf<Pair<Task, String>?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tasks For All", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add Task", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (tasksForAll.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No tasks yet", color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val doneTasks = tasksForAll.filter { it.isDone }
                val pendingTasks = tasksForAll.filter { !it.isDone }

                items(pendingTasks, key = { it.id }) { task ->
                    TaskRow(task, "tasks_for_all", null, user, true, vm,
                        onShowComments = { showComments = it },
                        onEditTask = { t, type -> showEditTask = t to type })
                }

                if (doneTasks.isNotEmpty()) {
                    item {
                        CompletedToggle(doneTasks, "tasks_for_all", null, user, true, vm,
                            onShowComments = { showComments = it },
                            onEditTask = { t, type -> showEditTask = t to type })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTaskForAllDialog(vm, user) { showAddDialog = false }
    }

    showComments?.let { task ->
        CommentsDialog(task, "tasks_for_all", null, user.email ?: "", vm) { showComments = null }
    }

    showEditTask?.let { (task, taskType) ->
        EditTaskDialog(task, taskType, null, user, true, vm) { showEditTask = null }
    }
}

@Composable
fun AddTaskForAllDialog(vm: AppViewModel, user: FirebaseUser, onDismiss: () -> Unit) {
    var desc by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Task For All") },
        text = {
            Column {
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it; error = null },
                    label = { Text("Task Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (desc.isBlank()) {
                    error = "Description is required"
                } else {
                    vm.addTaskForAll(desc, user.email ?: "")
                    onDismiss()
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun IndividualTasksPage(vm: AppViewModel, user: FirebaseUser) {
    val tasks by vm.tasks.collectAsState()
    val staff by vm.staff.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf<Task?>(null) }
    var showEditTask by remember { mutableStateOf<Pair<Task, String>?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Individual Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add Task", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No individual tasks", color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val doneTasks = tasks.filter { it.isDone }
                val pendingTasks = tasks.filter { !it.isDone }

                items(pendingTasks, key = { it.id }) { task ->
                    TaskRow(task, "tasks", null, user, true, vm,
                        onShowComments = { showComments = it },
                        onEditTask = { t, type -> showEditTask = t to type })
                }

                if (doneTasks.isNotEmpty()) {
                    item {
                        CompletedToggle(doneTasks, "tasks", null, user, true, vm,
                            onShowComments = { showComments = it },
                            onEditTask = { t, type -> showEditTask = t to type })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddIndividualTaskDialog(vm, user) { showAddDialog = false }
    }

    showComments?.let { task ->
        CommentsDialog(task, "tasks", null, user.email ?: "", vm) { showComments = null }
    }

    showEditTask?.let { (task, taskType) ->
        EditTaskDialog(task, taskType, null, user, true, vm) { showEditTask = null }
    }
}

@Composable
fun AddIndividualTaskDialog(vm: AppViewModel, user: FirebaseUser, onDismiss: () -> Unit) {
    val staff by vm.staff.collectAsState()
    var desc by remember { mutableStateOf("") }
    var selectedStaff by remember { mutableStateOf<Staff?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Individual Task") },
        text = {
            Column {
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it; error = null },
                    label = { Text("Task Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(Modifier.height(8.dp))
                Text("Assign To", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                
                val allUsers = staff + Staff(name = user.email?.substringBefore('@') ?: "Me", email = user.email ?: "")
                allUsers.forEach { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedStaff = s }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedStaff == s, onClick = { selectedStaff = s })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(s.name, fontWeight = FontWeight.Medium)
                            Text(s.email, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        }
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    desc.isBlank() -> error = "Description is required"
                    selectedStaff == null -> error = "Select an assignee"
                    else -> {
                        vm.addIndividualTask(desc, selectedStaff!!.email, creatorEmail = user.email ?: "")
                        onDismiss()
                    }
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ProjectsPage(vm: AppViewModel, user: FirebaseUser) {
    val projects by vm.projects.collectAsState()
    val staff by vm.staff.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var expandedProjectId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("All Projects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add Project", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No projects yet", color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(project, vm, user, expandedProjectId, onExpandChange = { id ->
                        expandedProjectId = if (expandedProjectId == id) null else id
                    })
                }
            }
        }
    }

    if (showAddDialog) {
        AddProjectDialog(vm, staff, user) { showAddDialog = false }
    }
}

@Composable
fun ProjectCard(project: Project, vm: AppViewModel, user: FirebaseUser,
                expandedProjectId: String?, onExpandChange: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().clickable { onExpandChange(project.id) }.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(project.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { vm.deleteProject(project.id, "abcd") { } }) {
                    Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
            if (expandedProjectId == project.id) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Members:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                project.members.forEach { member ->
                    Text("• $member", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun AddProjectDialog(vm: AppViewModel, staff: List<Staff>, user: FirebaseUser, onDismiss: () -> Unit) {
    var projectName by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Project") },
        text = {
            Column {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it; error = null },
                    label = { Text("Project Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text("Select Members", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                
                val allUsers = staff + Staff(name = user.email?.substringBefore('@') ?: "Me", email = user.email ?: "")
                allUsers.forEach { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedMembers = if (selectedMembers.contains(s.email))
                                selectedMembers - s.email else selectedMembers + s.email
                        }.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = selectedMembers.contains(s.email), onCheckedChange = {
                            selectedMembers = if (selectedMembers.contains(s.email))
                                selectedMembers - s.email else selectedMembers + s.email
                        })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(s.name, fontWeight = FontWeight.Medium)
                            Text(s.email, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        }
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    projectName.isBlank() -> error = "Project name is required"
                    selectedMembers.isEmpty() -> error = "Select at least one member"
                    else -> {
                        vm.createProject(projectName, selectedMembers.toList())
                        onDismiss()
                    }
                }
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun UserProfilePage(profile: UserProfile, vm: AppViewModel, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Person, null, modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary)
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileInfoRow("Name", profile.displayName)
                HorizontalDivider()
                ProfileInfoRow("Email", profile.email)
                HorizontalDivider()
                ProfileInfoRow("Role", if (profile.isAdmin) "Admin" else "User")
                HorizontalDivider()
                ProfileInfoRow("Status", if (profile.active) "Active" else "Inactive")
            }
        }

        Spacer(Modifier.weight(1f))

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TaskRow(task: Task, taskType: String, projectId: String?, user: FirebaseUser,
            isAdmin: Boolean, vm: AppViewModel,
            onShowComments: (Task) -> Unit,
            onEditTask: (Task, String) -> Unit) {
    val isDone = task.isDone
    var showDeleteDialog by remember(task.id) { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Checkbox(checked = isDone, onCheckedChange = { checked ->
            vm.toggleTaskDone(task, taskType, projectId, checked, user.email ?: "")
        }, modifier = Modifier.padding(top = 0.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = task.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (isDone) MaterialTheme.colorScheme.onSurface.copy(0.45f)
                else MaterialTheme.colorScheme.onSurface
            )
            if (!task.dueDate.isNullOrEmpty()) {
                val overdue = isOverdue(task.dueDate) && !isDone
                Text(text = formatDueDate(task.dueDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            if (task.createdBy.isNotEmpty()) {
                Text("By ${task.createdBy.substringBefore('@')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
            }
            if (isDone && !task.completedBy.isNullOrEmpty()) {
                Text("Done by ${task.completedBy!!.substringBefore('@')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShowComments(task) }, modifier = Modifier.size(32.dp)) {
                BadgedBox(badge = {
                    if (task.comments.isNotEmpty()) Badge { Text("${task.comments.size}") }
                }) { Icon(Icons.Default.Comment, "Comments", modifier = Modifier.size(18.dp)) }
            }
            if (isAdmin || task.assigneeEmail == user.email) {
                IconButton(onClick = { onEditTask(task, taskType) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(18.dp))
                }
            }
            if (isAdmin) {
                IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteDialog) {
        TaskDeletePasswordDialog(task, taskType, projectId, vm) { showDeleteDialog = false }
    }
}

@Composable
fun CompletedToggle(doneTasks: List<Task>, taskType: String, projectId: String?,
                    user: FirebaseUser, isAdmin: Boolean, vm: AppViewModel,
                    onShowComments: (Task) -> Unit,
                    onEditTask: (Task, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text("${if (expanded) "Hide" else "Show"} ${doneTasks.size} Completed",
            style = MaterialTheme.typography.labelMedium)
    }
    if (expanded) {
        doneTasks.forEach { task ->
            TaskRow(task, taskType, projectId, user, isAdmin, vm, onShowComments, onEditTask)
        }
    }
}

@Composable
fun CommentsDialog(task: Task, taskType: String, projectId: String?,
                   currentUserEmail: String, vm: AppViewModel, onDismiss: () -> Unit) {
    var commentText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comments", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                Text(task.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (task.comments.isEmpty()) {
                    Text("No comments yet.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                        task.comments.forEach { comment ->
                            val author = (comment["authorEmail"] as? String)?.substringBefore('@') ?: "?"
                            val text = comment["text"] as? String ?: ""
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                .padding(8.dp)) {
                                Text(author, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(text, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = commentText, onValueChange = { commentText = it },
                        label = { Text("Add comment") }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (commentText.isNotBlank()) {
                            vm.addComment(task.id, taskType, projectId, commentText, currentUserEmail)
                            commentText = ""
                        }
                    }) { Text("Post") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun EditTaskDialog(task: Task, taskType: String, projectId: String?, user: FirebaseUser,
                   isAdmin: Boolean, vm: AppViewModel, onDismiss: () -> Unit) {
    var description by remember(task.id) { mutableStateOf(task.description) }
    var dueDate by remember(task.id) { mutableStateOf(task.dueDate ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task") },
        text = {
            Column {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; error = null },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it; error = null },
                    label = { Text("Due Date (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (description.isBlank()) {
                    error = "Description is required"
                } else {
                    vm.editTask(task.id, taskType, projectId, description, dueDate.ifBlank { null },
                        "none", null, null, user.email ?: "", isAdmin)
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TaskDeletePasswordDialog(task: Task, taskType: String, projectId: String?,
                             vm: AppViewModel, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Task") },
        text = {
            Column {
                Text("Enter admin password to delete this task.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Admin Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.deleteTask(task.id, taskType, projectId, password) { err ->
                    error = err
                    if (err == null) onDismiss()
                }
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Auth ────────────────────────────────────────────────────────────────────

@Composable
fun AuthScreen(vm: AppViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showAdminSignupConfirm by remember { mutableStateOf(false) }
    val selfRegistrationAllowed by vm.selfRegistrationAllowed.collectAsState()
    val globalError by vm.errorMessage.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Assignment, null, modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Project & Task Board", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            when {
                selfRegistrationAllowed == null -> "Checking workspace access..."
                isSignUp -> "You are creating a full new admin account"
                else -> "Sign in to manage your team"
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))

        globalError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(value = email, onValueChange = { email = it; errorMsg = null },
            label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it; errorMsg = null },
            label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation())
        if (errorMsg != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            if (isSignUp) {
                showAdminSignupConfirm = true
            } else {
                vm.signIn(email, password) { err -> errorMsg = err }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text(if (isSignUp) "Create Admin Account" else "Sign In")
        }

        TextButton(onClick = { isSignUp = !isSignUp; errorMsg = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (isSignUp) "Already have an account? Sign in" else "Don't have an account? Create one")
        }
    }

    if (showAdminSignupConfirm) {
        AlertDialog(
            onDismissRequest = { showAdminSignupConfirm = false },
            title = { Text("Create Admin Account") },
            text = { Text("You are about to create a new admin account. Only proceed if you are authorized to do so.") },
            confirmButton = {
                Button(onClick = {
                    vm.signUp(email, password) { err -> errorMsg = err }
                    showAdminSignupConfirm = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAdminSignupConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ─── Utility ──────────────────────────────────────────────────────────────────

fun isOverdue(dueDate: String?): Boolean {
    if (dueDate.isNullOrEmpty()) return false
    return try {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val due = formatter.parse(dueDate) ?: return false
        due.before(Date())
    } catch (e: Exception) {
        false
    }
}

fun formatDueDate(dueDate: String?): String {
    if (dueDate.isNullOrEmpty()) return ""
    return try {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val date = formatter.parse(dueDate) ?: return dueDate
        val display = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
        display.format(date)
    } catch (e: Exception) {
        dueDate
    }
}
