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

    error?.let {
        LaunchedEffect(it) {
            // Error shown via Snackbar or inline; clear after display
        }
    }

    when (val state = authState) {
        is AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is AuthState.LoggedOut -> AuthScreen(vm)
        is AuthState.LoggedIn -> MainAppScreen(vm, state.user, state.profile)
    }
}

// ─── Auth ────────────────────────────────────────────────────────────────────

@Composable
fun AuthScreen(vm: AppViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Assignment, null, modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Project & Task Board", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(if (isSignUp) "Create your account" else "Sign in to manage your team",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))

        errorMsg?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(value = email, onValueChange = { email = it; errorMsg = null },
            label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it; errorMsg = null },
            label = { Text("Password") }, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                errorMsg = null
                if (isSignUp) vm.signUp(email, password) { errorMsg = it }
                else vm.signIn(email, password) { errorMsg = it }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (isSignUp) "Create Account" else "Sign In") }

        TextButton(onClick = { isSignUp = !isSignUp; errorMsg = null },
            modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (isSignUp) "Already have an account? Sign In" else "Need an account? Sign Up")
        }
    }
}

// ─── Main App ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(vm: AppViewModel, user: FirebaseUser, profile: UserProfile) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = if (profile.isAdmin) listOf("Tasks", "Projects", "Settings") else listOf("Tasks", "Projects")

    // Dialog state
    var commentState by remember { mutableStateOf<CommentDialogState?>(null) }
    var editTaskState by remember { mutableStateOf<EditTaskDialogState?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project & Task Board", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 4.dp)) {
                        Text(user.email ?: "", style = MaterialTheme.typography.labelSmall)
                        Text(profile.role, style = MaterialTheme.typography.labelSmall,
                            color = if (profile.isAdmin) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { vm.signOut() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Sign out")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            when (tab) {
                                "Tasks" -> Icon(Icons.Default.FormatListBulleted, tab)
                                "Projects" -> Icon(Icons.Default.Folder, tab)
                                else -> Icon(Icons.Default.Settings, tab)
                            }
                        },
                        label = { Text(tab) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (tabs.getOrNull(selectedTab)) {
                "Tasks" -> TasksPage(vm, user, profile,
                    onShowComments = { task, type, pId -> commentState = CommentDialogState(task, type, pId) },
                    onEditTask = { task, type, pId -> editTaskState = EditTaskDialogState(task, type, pId) })
                "Projects" -> ProjectsPage(vm, user, profile,
                    onShowComments = { task, type, pId -> commentState = CommentDialogState(task, type, pId) },
                    onEditTask = { task, type, pId -> editTaskState = EditTaskDialogState(task, type, pId) })
                "Settings" -> SettingsPage(vm)
                else -> Unit
            }
        }
    }

    commentState?.let { state ->
        CommentsDialog(task = state.task, taskType = state.taskType, projectId = state.projectId,
            currentUserEmail = user.email ?: "", vm = vm, onDismiss = { commentState = null })
    }

    editTaskState?.let { state ->
        EditTaskDialog(task = state.task, taskType = state.taskType, projectId = state.projectId,
            vm = vm, profile = profile, staff = if (vm.staff.collectAsState().value.isNotEmpty())
                vm.staff.collectAsState().value else emptyList(),
            currentUserEmail = user.email ?: "", onDismiss = { editTaskState = null })
    }
}

data class CommentDialogState(val task: Task, val taskType: String, val projectId: String?)
data class EditTaskDialogState(val task: Task, val taskType: String, val projectId: String?)

// ─── Tasks Page ───────────────────────────────────────────────────────────────

@Composable
fun TasksPage(vm: AppViewModel, user: FirebaseUser, profile: UserProfile,
              onShowComments: (Task, String, String?) -> Unit,
              onEditTask: (Task, String, String?) -> Unit) {
    val tasksForAll by vm.tasksForAll.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val staff by vm.staff.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)) {

        // ── Tasks For All section ──
        item {
            SectionHeader("Tasks For All")
        }
        if (profile.isAdmin) {
            item { AddTaskForAllForm(vm, user) }
        }
        items(tasksForAll.filter { !it.isDone }, key = { "tfa-todo-${it.id}" }) { task ->
            TaskRow(task, "tasks_for_all", null, user, profile.isAdmin, vm, onShowComments, onEditTask)
        }
        val doneTfA = tasksForAll.filter { it.isDone }
        if (doneTfA.isNotEmpty()) {
            item { CompletedToggle("tasks_for_all", doneTfA, null, user, profile.isAdmin, vm, onShowComments, onEditTask) }
        }

        // ── Individual Tasks section ──
        item { Spacer(Modifier.height(8.dp)); SectionHeader(if (profile.isAdmin) "Individual Tasks (All Staff)" else "My Individual Tasks") }

        if (profile.isAdmin) {
            // Admin: assign task form
            item { AdminAssignIndividualTaskForm(vm, staff, user) }
            // Grouped by staff
            val staffList = staff
            if (staffList.isEmpty()) {
                item { Text("No staff added yet. Add staff in Settings.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f), modifier = Modifier.padding(8.dp)) }
            }
            staffList.forEach { staffMember ->
                val staffTasks = tasks.filter { it.assigneeEmail == staffMember.email }
                item { Text(staffMember.name, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
                items(staffTasks.filter { !it.isDone }, key = { "itask-todo-${staffMember.email}-${it.id}" }) { task ->
                    TaskRow(task, "tasks", null, user, profile.isAdmin, vm, onShowComments, onEditTask)
                }
                val doneTasks = staffTasks.filter { it.isDone }
                if (doneTasks.isNotEmpty()) {
                    item { CompletedToggle("tasks", doneTasks, null, user, profile.isAdmin, vm, onShowComments, onEditTask) }
                }
                if (staffTasks.isEmpty()) {
                    item { Text("No tasks.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.padding(4.dp)) }
                }
            }
        } else {
            // User: own tasks
            item { UserAddIndividualTaskForm(vm, user) }
            items(tasks.filter { !it.isDone }, key = { "mytask-todo-${it.id}" }) { task ->
                TaskRow(task, "tasks", null, user, false, vm, onShowComments, onEditTask)
            }
            val myDone = tasks.filter { it.isDone }
            if (myDone.isNotEmpty()) {
                item { CompletedToggle("tasks", myDone, null, user, false, vm, onShowComments, onEditTask) }
            }
        }
    }
}

@Composable
fun AddTaskForAllForm(vm: AppViewModel, user: FirebaseUser) {
    var desc by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = desc, onValueChange = { desc = it },
                label = { Text("New shared task…") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                vm.addTaskForAll(desc, user.email ?: ""); desc = ""
            }) { Text("Add") }
        }
    }
}

@Composable
fun AdminAssignIndividualTaskForm(vm: AppViewModel, staff: List<Staff>, user: FirebaseUser) {
    if (staff.isEmpty()) return
    var desc by remember { mutableStateOf("") }
    var selectedStaff by remember(staff) { mutableStateOf(staff.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Assign Individual Task", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(value = desc, onValueChange = { desc = it },
                label = { Text("Task description") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(4.dp))
            Box {
                OutlinedTextField(value = selectedStaff?.name ?: "Select staff", onValueChange = {},
                    label = { Text("Assign to") }, modifier = Modifier.fillMaxWidth(),
                    readOnly = true, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null,
                        modifier = Modifier.clickable { expanded = true }) })
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    staff.forEach { s ->
                        DropdownMenuItem(text = { Text(s.name) }, onClick = { selectedStaff = s; expanded = false })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = {
                selectedStaff?.let { vm.addIndividualTask(desc, it.email, creatorEmail = user.email ?: "") }
                desc = ""
            }, modifier = Modifier.fillMaxWidth()) { Text("Assign Task") }
        }
    }
}

@Composable
fun UserAddIndividualTaskForm(vm: AppViewModel, user: FirebaseUser) {
    var desc by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = desc, onValueChange = { desc = it },
                label = { Text("Add a task for myself…") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                vm.addIndividualTask(desc, user.email ?: "", creatorEmail = user.email ?: ""); desc = ""
            }) { Text("Add") }
        }
    }
}

// ─── Projects Page ────────────────────────────────────────────────────────────

@Composable
fun ProjectsPage(vm: AppViewModel, user: FirebaseUser, profile: UserProfile,
                 onShowComments: (Task, String, String?) -> Unit,
                 onEditTask: (Task, String, String?) -> Unit) {
    val projects by vm.projects.collectAsState()
    val staff by vm.staff.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)) {

        if (profile.isAdmin) {
            item { SectionHeader(if (profile.isAdmin) "All Projects" else "My Assigned Projects") }
            item { CreateProjectForm(vm, staff) }
        } else {
            item { SectionHeader("My Assigned Projects") }
        }

        if (projects.isEmpty()) {
            item {
                Text(if (profile.isAdmin) "No projects created yet." else "You are not assigned to any projects.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    modifier = Modifier.padding(8.dp))
            }
        }

        items(projects, key = { it.id }) { project ->
            ProjectCard(project, vm, user, profile, staff, onShowComments, onEditTask)
        }
    }
}

@Composable
fun CreateProjectForm(vm: AppViewModel, staff: List<Staff>) {
    var projectName by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }
    var expanded by remember { mutableStateOf(true) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expanded = !expanded }) {
                Text("Create New Project", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = projectName, onValueChange = { projectName = it },
                    label = { Text("Project name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                if (staff.isEmpty()) {
                    Text("Add staff first in Settings.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                } else {
                    Text("Select members:", style = MaterialTheme.typography.labelMedium)
                    staff.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                            if (selectedMembers.contains(s.email)) selectedMembers.remove(s.email)
                            else selectedMembers.add(s.email)
                        }) {
                            Checkbox(checked = selectedMembers.contains(s.email), onCheckedChange = { checked ->
                                if (checked) selectedMembers.add(s.email) else selectedMembers.remove(s.email)
                            })
                            Text(s.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (projectName.isNotBlank() && selectedMembers.isNotEmpty()) {
                        vm.createProject(projectName, selectedMembers.toList())
                        projectName = ""; selectedMembers.clear()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Create Project") }
            }
        }
    }
}

@Composable
fun ProjectCard(project: Project, vm: AppViewModel, user: FirebaseUser, profile: UserProfile,
                staff: List<Staff>, onShowComments: (Task, String, String?) -> Unit,
                onEditTask: (Task, String, String?) -> Unit) {
    LaunchedEffect(project.id) {
        vm.listenProjectTasks(project.id, user.email, profile.isAdmin)
    }
    val allProjectTasks by vm.projectTasks.collectAsState()
    val projectTasks = allProjectTasks[project.id] ?: emptyList()
    var expanded by remember { mutableStateOf(true) }
    var editingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(project.name) }
    val pendingCount = projectTasks.count { !it.isDone }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).clickable { expanded = !expanded }) {
                    Text(project.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("$pendingCount pending", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
                if (profile.isAdmin) {
                    TextButton(onClick = { editingName = true }) { Text("Edit") }
                    TextButton(onClick = { vm.deleteProject(project.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Delete")
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }

            if (editingName) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = editedName, onValueChange = { editedName = it },
                        modifier = Modifier.weight(1f), singleLine = true)
                    TextButton(onClick = {
                        vm.editProject(project.id, editedName); editingName = false
                    }) { Text("Save") }
                    TextButton(onClick = { editingName = false; editedName = project.name }) { Text("Cancel") }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (profile.isAdmin) {
                    // Admin: assign per member
                    val members = staff.filter { project.members.contains(it.email) }
                    AdminProjectTaskForm(project.id, members, vm, user)
                    Spacer(Modifier.height(8.dp))
                    members.forEach { member ->
                        val memberTasks = projectTasks.filter { it.assigneeEmail == member.email }
                        Text(member.name, style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                        memberTasks.filter { !it.isDone }.forEach { task ->
                            TaskRow(task, "project", project.id, user, true, vm, onShowComments, onEditTask)
                        }
                        val done = memberTasks.filter { it.isDone }
                        if (done.isNotEmpty()) CompletedToggle("project", done, project.id, user, true, vm, onShowComments, onEditTask)
                        if (memberTasks.isEmpty()) {
                            Text("No tasks.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.padding(4.dp))
                        }
                    }
                } else {
                    // User: own tasks + add form
                    UserProjectTaskForm(project.id, vm, user)
                    Spacer(Modifier.height(4.dp))
                    projectTasks.filter { !it.isDone }.forEach { task ->
                        TaskRow(task, "project", project.id, user, false, vm, onShowComments, onEditTask)
                    }
                    val done = projectTasks.filter { it.isDone }
                    if (done.isNotEmpty()) CompletedToggle("project", done, project.id, user, false, vm, onShowComments, onEditTask)
                }
            }
        }
    }
}

@Composable
fun AdminProjectTaskForm(projectId: String, members: List<Staff>, vm: AppViewModel, user: FirebaseUser) {
    if (members.isEmpty()) return
    var desc by remember { mutableStateOf("") }
    var selectedMember by remember(members) { mutableStateOf(members.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text("Assign Project Task", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = desc, onValueChange = { desc = it },
                label = { Text("Task description") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(4.dp))
            Box {
                OutlinedTextField(value = selectedMember?.name ?: "", onValueChange = {},
                    readOnly = true, modifier = Modifier.width(140.dp),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.clickable { expanded = true }) })
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    members.forEach { m ->
                        DropdownMenuItem(text = { Text(m.name) }, onClick = { selectedMember = m; expanded = false })
                    }
                }
            }
        }
        Button(onClick = {
            selectedMember?.let { vm.addProjectTask(projectId, desc, it.email, creatorEmail = user.email ?: "") }
            desc = ""
        }, modifier = Modifier.fillMaxWidth()) { Text("Assign Task") }
    }
}

@Composable
fun UserProjectTaskForm(projectId: String, vm: AppViewModel, user: FirebaseUser) {
    var desc by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = desc, onValueChange = { desc = it },
            label = { Text("Add task for this project…") }, modifier = Modifier.weight(1f), singleLine = true)
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            vm.addProjectTask(projectId, desc, user.email ?: "", creatorEmail = user.email ?: ""); desc = ""
        }) { Text("Add") }
    }
}

// ─── Settings Page ────────────────────────────────────────────────────────────

@Composable
fun SettingsPage(vm: AppViewModel) {
    val staff by vm.staff.collectAsState()
    var newName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var editingStaff by remember { mutableStateOf<Staff?>(null) }
    var editName by remember { mutableStateOf("") }
    var editEmail by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionHeader("Staff Management") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Add New Staff", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = newName, onValueChange = { newName = it },
                        label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = newEmail, onValueChange = { newEmail = it },
                        label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (newName.isNotBlank() && newEmail.isNotBlank()) {
                            vm.addStaff(newName, newEmail); newName = ""; newEmail = ""
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Add Staff") }
                }
            }
        }
        items(staff, key = { it.id }) { s ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (editingStaff?.id == s.id) {
                        OutlinedTextField(value = editName, onValueChange = { editName = it },
                            label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(value = editEmail, onValueChange = { editEmail = it },
                            label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Button(onClick = { vm.editStaff(s.id, editName, editEmail); editingStaff = null },
                                modifier = Modifier.weight(1f)) { Text("Save") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { editingStaff = null }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(s.email, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            }
                            TextButton(onClick = { editingStaff = s; editName = s.name; editEmail = s.email }) { Text("Edit") }
                            TextButton(onClick = { vm.removeStaff(s.id) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove") }
                        }
                    }
                }
            }
        }
        if (staff.isEmpty()) {
            item {
                Text("No staff added yet.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f), modifier = Modifier.padding(8.dp))
            }
        }
    }
}

// ─── Shared Components ────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp))
    HorizontalDivider()
    Spacer(Modifier.height(4.dp))
}

@Composable
fun TaskRow(task: Task, taskType: String, projectId: String?, user: FirebaseUser,
            isAdmin: Boolean, vm: AppViewModel,
            onShowComments: (Task, String, String?) -> Unit,
            onEditTask: (Task, String, String?) -> Unit) {
    val isDone = task.isDone
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
        // Action buttons
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShowComments(task, taskType, projectId) }, modifier = Modifier.size(32.dp)) {
                BadgedBox(badge = {
                    if (task.comments.isNotEmpty()) Badge { Text("${task.comments.size}") }
                }) { Icon(Icons.Default.Comment, "Comments", modifier = Modifier.size(18.dp)) }
            }
            if (isAdmin || task.assigneeEmail == user.email) {
                IconButton(onClick = { onEditTask(task, taskType, projectId) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(18.dp))
                }
            }
            if (isAdmin) {
                IconButton(onClick = { vm.deleteTask(task.id, taskType, projectId) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun CompletedToggle(taskType: String, doneTasks: List<Task>, projectId: String?,
                    user: FirebaseUser, isAdmin: Boolean, vm: AppViewModel,
                    onShowComments: (Task, String, String?) -> Unit,
                    onEditTask: (Task, String, String?) -> Unit) {
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

// ─── Dialogs ──────────────────────────────────────────────────────────────────

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
fun EditTaskDialog(task: Task, taskType: String, projectId: String?,
                   vm: AppViewModel, profile: UserProfile, staff: List<Staff>,
                   currentUserEmail: String, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf(task.description) }
    var assigneeEmail by remember { mutableStateOf(task.assigneeEmail) }
    var staffDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task") },
        text = {
            Column {
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                if (profile.isAdmin && taskType != "tasks_for_all" && staff.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Assignee", style = MaterialTheme.typography.labelMedium)
                    val selectedStaffName = staff.find { it.email == assigneeEmail }?.name ?: assigneeEmail
                    Box {
                        OutlinedTextField(value = selectedStaffName, onValueChange = {},
                            readOnly = true, modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null,
                                modifier = Modifier.clickable { staffDropdownExpanded = true }) })
                        DropdownMenu(expanded = staffDropdownExpanded, onDismissRequest = { staffDropdownExpanded = false }) {
                            staff.forEach { s ->
                                DropdownMenuItem(text = { Text(s.name) }, onClick = {
                                    assigneeEmail = s.email; staffDropdownExpanded = false
                                })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.editTask(task.id, taskType, projectId, description, task.dueDate,
                    task.repeatType, task.repeatRemaining,
                    if (profile.isAdmin) assigneeEmail else null,
                    currentUserEmail, profile.isAdmin)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

fun formatDueDate(dueDate: String?): String {
    if (dueDate.isNullOrEmpty()) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val date = sdf.parse(dueDate) ?: return dueDate
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
    } catch (e: Exception) { dueDate }
}

fun isOverdue(dueDate: String?): Boolean {
    if (dueDate.isNullOrEmpty()) return false
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val date = sdf.parse(dueDate) ?: return false
        date.before(Date())
    } catch (e: Exception) { false }
}
