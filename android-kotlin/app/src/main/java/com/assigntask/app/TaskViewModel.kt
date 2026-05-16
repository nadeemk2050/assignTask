package com.assigntask.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import java.util.UUID

private const val WEB_APP_ID = "1:90081570769:web:1349e338f08c1643003af0"
private const val BASE = "/artifacts/$WEB_APP_ID/public/data"

sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(val user: FirebaseUser, val profile: UserProfile) : AuthState()
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = Firebase.auth
    private val db = Firebase.firestore

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _tasksForAll = MutableStateFlow<List<Task>>(emptyList())
    val tasksForAll: StateFlow<List<Task>> = _tasksForAll.asStateFlow()

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _staff = MutableStateFlow<List<Staff>>(emptyList())
    val staff: StateFlow<List<Staff>> = _staff.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selfRegistrationAllowed = MutableStateFlow<Boolean?>(null)
    val selfRegistrationAllowed: StateFlow<Boolean?> = _selfRegistrationAllowed.asStateFlow()

    private val _projectTasks = MutableStateFlow<Map<String, List<Task>>>(emptyMap())
    val projectTasks: StateFlow<Map<String, List<Task>>> = _projectTasks.asStateFlow()

    private val dataListeners = mutableListOf<ListenerRegistration>()
    private val projectTaskListeners = mutableMapOf<String, ListenerRegistration>()

    init {
        db.collection("$BASE/users")
            .whereEqualTo("role", "admin")
            .limit(1)
            .addSnapshotListener { snap, _ ->
                _selfRegistrationAllowed.value = snap?.isEmpty != false
            }

        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                clearDataListeners()
                _authState.value = AuthState.LoggedOut
            } else {
                setupForUser(user)
            }
        }
    }

    private fun setupForUser(user: FirebaseUser) {
        clearDataListeners()
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val profile = resolveUserProfile(user)
            if (profile == null) {
                _authState.value = AuthState.LoggedOut
                return@launch
            }
            _authState.value = AuthState.LoggedIn(user, profile)
            setupFirestoreListeners(user, profile)
        }
    }

    private suspend fun resolveUserProfile(user: FirebaseUser): UserProfile? {
        return try {
            val doc = db.collection("$BASE/users").document(user.uid).get().await()
            if (doc.exists()) {
                val active = doc.getBoolean("active") ?: true
                if (!active) {
                    _errorMessage.value = "This account has been disabled by the admin."
                    auth.signOut()
                    return null
                }
                UserProfile(
                    name = doc.getString("name") ?: "",
                    email = doc.getString("email") ?: user.email ?: "",
                    role = doc.getString("role") ?: "user",
                    active = active
                )
            } else {
                val profile = UserProfile(
                    name = user.email?.substringBefore('@') ?: "Admin",
                    email = user.email ?: "",
                    role = "admin",
                    active = true
                )
                db.collection("$BASE/users").document(user.uid).set(
                    mapOf(
                        "name" to profile.name,
                        "email" to profile.email,
                        "role" to profile.role,
                        "active" to profile.active,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                profile
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error loading profile: ${e.message}"
            auth.signOut()
            null
        }
    }

    private fun setupFirestoreListeners(user: FirebaseUser, profile: UserProfile) {
        // Tasks for all - everyone
        dataListeners += db.collection("$BASE/tasks_for_all")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                _tasksForAll.value = snap?.documents?.map { doc ->
                    docToTask(doc.id, doc.data ?: emptyMap())
                } ?: emptyList()
            }

        if (profile.isAdmin) {
            // Individual tasks - all staff
            dataListeners += db.collection("$BASE/tasks")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snap, _ ->
                    _tasks.value = snap?.documents?.map { doc ->
                        docToTask(doc.id, doc.data ?: emptyMap())
                    } ?: emptyList()
                }
            // Staff list
            dataListeners += db.collection("$BASE/staff")
                .orderBy("name")
                .addSnapshotListener { snap, _ ->
                    _staff.value = snap?.documents?.map { doc ->
                        Staff(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            email = doc.getString("email") ?: "",
                            uid = doc.getString("uid") ?: ""
                        )
                    } ?: emptyList()
                }
            // All projects
            dataListeners += db.collection("$BASE/projects")
                .addSnapshotListener { snap, _ ->
                    _projects.value = snap?.documents?.map { doc ->
                        @Suppress("UNCHECKED_CAST")
                        Project(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            members = (doc.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        )
                    } ?: emptyList()
                }
        } else {
            // Own individual tasks only
            dataListeners += db.collection("$BASE/tasks")
                .whereEqualTo("assigneeEmail", user.email)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snap, _ ->
                    _tasks.value = snap?.documents?.map { doc ->
                        docToTask(doc.id, doc.data ?: emptyMap())
                    } ?: emptyList()
                }
            // Projects where user is member
            dataListeners += db.collection("$BASE/projects")
                .whereArrayContains("members", user.email ?: "")
                .addSnapshotListener { snap, _ ->
                    @Suppress("UNCHECKED_CAST")
                    _projects.value = snap?.documents?.map { doc ->
                        Project(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            members = (doc.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        )
                    } ?: emptyList()
                }
        }
    }

    fun listenProjectTasks(projectId: String, userEmail: String?, isAdmin: Boolean) {
        if (projectTaskListeners.containsKey(projectId)) return
        val baseQuery = db.collection("$BASE/projects/$projectId/tasks")
        val query: Query = if (!isAdmin && userEmail != null) {
            baseQuery.whereEqualTo("assigneeEmail", userEmail)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        } else {
            baseQuery.orderBy("createdAt", Query.Direction.DESCENDING)
        }
        projectTaskListeners[projectId] = query.addSnapshotListener { snap, _ ->
            val current = _projectTasks.value.toMutableMap()
            current[projectId] = snap?.documents?.map { doc ->
                docToTask(doc.id, doc.data ?: emptyMap())
            } ?: emptyList()
            _projectTasks.value = current
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun docToTask(id: String, data: Map<String, Any>): Task = Task(
        id = id,
        description = data["description"] as? String ?: "",
        assigneeEmail = data["assigneeEmail"] as? String ?: "",
        status = data["status"] as? String ?: "To Do",
        dueDate = data["dueDate"] as? String,
        comments = (data["comments"] as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList(),
        createdBy = data["createdBy"] as? String ?: "",
        createdAt = data["createdAt"],
        completedBy = data["completedBy"] as? String,
        completedAt = data["completedAt"],
        repeat = data["repeat"] as? Map<String, Any>
    )

    fun signIn(email: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try { auth.signInWithEmailAndPassword(email, password).await() }
            catch (e: Exception) { onError(e.message ?: "Sign in failed") }
        }
    }

    fun signUp(email: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                auth.createUserWithEmailAndPassword(email.trim(), password).await()
            }
            catch (e: Exception) { onError(e.message ?: "Sign up failed") }
        }
    }

    fun createSubUser(name: String, email: String, password: String, adminEmail: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val normalizedEmail = email.trim().lowercase()
            val trimmedName = name.trim()
            if (trimmedName.isBlank() || normalizedEmail.isBlank()) {
                onResult("Name and email are required")
                return@launch
            }
            if (password.length < 6) {
                onResult("Password must be at least 6 characters")
                return@launch
            }

            var secondaryApp: FirebaseApp? = null
            try {
                val options = FirebaseApp.getInstance().options
                val secondaryOptions = FirebaseOptions.Builder()
                    .setApiKey(options.apiKey)
                    .setApplicationId(options.applicationId)
                    .setProjectId(options.projectId)
                    .setStorageBucket(options.storageBucket)
                    .setGcmSenderId(options.gcmSenderId)
                    .build()
                secondaryApp = FirebaseApp.initializeApp(
                    getApplication(),
                    secondaryOptions,
                    "subuser-${UUID.randomUUID()}"
                )
                val secondaryAuth = FirebaseAuth.getInstance(secondaryApp!!)
                val newUser = secondaryAuth.createUserWithEmailAndPassword(normalizedEmail, password).await().user
                    ?: throw IllegalStateException("Firebase did not return a new user")

                db.collection("$BASE/users").document(newUser.uid).set(
                    mapOf(
                        "name" to trimmedName,
                        "email" to normalizedEmail,
                        "role" to "user",
                        "active" to true,
                        "createdBy" to adminEmail,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                upsertStaffRecord(trimmedName, normalizedEmail, newUser.uid)
                secondaryAuth.signOut()
                secondaryApp?.delete()
                onResult(null)
            } catch (e: Exception) {
                secondaryApp?.delete()
                onResult(e.message ?: "Failed to create sub user")
            }
        }
    }

    fun updateCurrentProfile(name: String, onResult: (String?) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onResult("No active user")
            return
        }
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            onResult("Name is required")
            return
        }

        viewModelScope.launch {
            try {
                db.collection("$BASE/users").document(currentUser.uid)
                    .update(
                        mapOf(
                            "name" to trimmedName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()

                val state = _authState.value
                if (state is AuthState.LoggedIn) {
                    if (!state.profile.isAdmin) {
                        currentUser.email?.trim()?.lowercase()?.let { upsertStaffRecord(trimmedName, it, currentUser.uid) }
                    }
                    _authState.value = state.copy(profile = state.profile.copy(name = trimmedName))
                }
                onResult(null)
            } catch (e: Exception) {
                onResult(e.message ?: "Failed to update profile")
            }
        }
    }

    private suspend fun upsertStaffRecord(name: String, email: String, uid: String) {
        val existing = db.collection("$BASE/staff")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .await()

        val data = mapOf("name" to name, "email" to email, "uid" to uid)
        if (existing.documents.isEmpty()) {
            db.collection("$BASE/staff").add(data).await()
        } else {
            db.collection("$BASE/staff").document(existing.documents.first().id).update(data).await()
        }
    }

    fun signOut() {
        clearDataListeners()
        auth.signOut()
    }

    fun addIndividualTask(description: String, assigneeEmail: String, dueDate: String? = null,
                          repeatType: String = "none", repeatCount: Int? = null, creatorEmail: String) {
        if (description.isBlank()) return
        val data = buildTaskData(description, assigneeEmail, dueDate, repeatType, repeatCount, creatorEmail)
        db.collection("$BASE/tasks").add(data)
            .addOnFailureListener { _errorMessage.value = "Failed: ${it.message}" }
    }

    fun addTaskForAll(description: String, creatorEmail: String) {
        if (description.isBlank()) return
        db.collection("$BASE/tasks_for_all").add(
            mapOf("description" to description, "status" to "To Do",
                "comments" to emptyList<Any>(), "createdAt" to FieldValue.serverTimestamp(),
                "createdBy" to creatorEmail)
        ).addOnFailureListener { _errorMessage.value = "Failed: ${it.message}" }
    }

    fun addProjectTask(projectId: String, description: String, assigneeEmail: String,
                       dueDate: String? = null, repeatType: String = "none",
                       repeatCount: Int? = null, creatorEmail: String) {
        if (description.isBlank()) return
        val data = buildTaskData(description, assigneeEmail, dueDate, repeatType, repeatCount, creatorEmail)
        db.collection("$BASE/projects/$projectId/tasks").add(data)
            .addOnFailureListener { _errorMessage.value = "Failed: ${it.message}" }
    }

    private fun buildTaskData(description: String, assigneeEmail: String, dueDate: String?,
                               repeatType: String, repeatCount: Int?, creatorEmail: String): Map<String, Any> {
        val data = mutableMapOf<String, Any>(
            "description" to description, "assigneeEmail" to assigneeEmail,
            "status" to "To Do", "comments" to emptyList<Any>(),
            "createdAt" to FieldValue.serverTimestamp(), "createdBy" to creatorEmail
        )
        if (!dueDate.isNullOrEmpty()) data["dueDate"] = dueDate
        if (repeatType != "none") {
            val repeatMap = mutableMapOf<String, Any>("type" to repeatType)
            if (repeatCount != null) repeatMap["remaining"] = repeatCount
            data["repeat"] = repeatMap
        }
        return data
    }

    fun toggleTaskDone(task: Task, taskType: String, projectId: String?,
                       isDone: Boolean, currentUserEmail: String) {
        val collPath = if (taskType == "project" && projectId != null)
            "$BASE/projects/$projectId/tasks" else "$BASE/$taskType"
        val collRef = db.collection(collPath)

        if (isDone && task.repeatType != "none") {
            val remaining = task.repeatRemaining
            if (remaining == null || remaining > 1) {
                val newData = buildTaskData(task.description, task.assigneeEmail,
                    calculateNextDueDate(task.dueDate, task.repeatType),
                    task.repeatType,
                    if (remaining != null) remaining - 1 else null,
                    task.createdBy)
                collRef.add(newData)
            }
        }

        val update = mutableMapOf<String, Any>(
            "status" to if (isDone) "Done" else "To Do",
            "lastUpdatedBy" to currentUserEmail,
            "lastUpdatedAt" to FieldValue.serverTimestamp()
        )
        if (isDone) {
            update["completedBy"] = currentUserEmail
            update["completedAt"] = FieldValue.serverTimestamp()
        } else {
            update["completedBy"] = FieldValue.delete()
        }
        collRef.document(task.id).update(update)
    }

    fun deleteTask(taskId: String, taskType: String, projectId: String?) {
        val docRef = if (taskType == "project" && projectId != null)
            db.collection("$BASE/projects/$projectId/tasks").document(taskId)
        else db.collection("$BASE/$taskType").document(taskId)
        docRef.delete()
    }

    fun editTask(taskId: String, taskType: String, projectId: String?,
                 description: String, dueDate: String?, repeatType: String,
                 repeatCount: Int?, assigneeEmail: String?,
                 currentUserEmail: String, isAdmin: Boolean) {
        val docRef = if (taskType == "project" && projectId != null)
            db.collection("$BASE/projects/$projectId/tasks").document(taskId)
        else db.collection("$BASE/$taskType").document(taskId)

        val update = mutableMapOf<String, Any>(
            "description" to description,
            "lastUpdatedBy" to currentUserEmail,
            "lastUpdatedAt" to FieldValue.serverTimestamp()
        )
        if (!dueDate.isNullOrEmpty()) update["dueDate"] = dueDate!! else update["dueDate"] = FieldValue.delete()
        if (repeatType != "none") {
            val repeatMap = mutableMapOf<String, Any>("type" to repeatType)
            if (repeatCount != null) repeatMap["remaining"] = repeatCount
            update["repeat"] = repeatMap
        } else {
            update["repeat"] = FieldValue.delete()
        }
        if (isAdmin && assigneeEmail != null && taskType != "tasks_for_all") {
            update["assigneeEmail"] = assigneeEmail
        }
        docRef.update(update)
    }

    fun addComment(taskId: String, taskType: String, projectId: String?,
                   commentText: String, authorEmail: String) {
        val docRef = if (taskType == "project" && projectId != null)
            db.collection("$BASE/projects/$projectId/tasks").document(taskId)
        else db.collection("$BASE/$taskType").document(taskId)
        val comment = mapOf("text" to commentText, "authorEmail" to authorEmail,
            "createdAt" to Date())
        docRef.update("comments", FieldValue.arrayUnion(comment))
    }

    fun addStaff(name: String, email: String) {
        db.collection("$BASE/staff").add(mapOf("name" to name, "email" to email.lowercase()))
    }

    fun editStaff(id: String, name: String, email: String) {
        db.collection("$BASE/staff").document(id)
            .update(mapOf("name" to name, "email" to email.lowercase()))
    }

    fun removeStaff(id: String) {
        db.collection("$BASE/staff").document(id).delete()
    }

    fun createProject(name: String, members: List<String>) {
        db.collection("$BASE/projects").add(
            mapOf("name" to name, "members" to members, "createdAt" to FieldValue.serverTimestamp())
        )
    }

    fun editProject(id: String, name: String) {
        db.collection("$BASE/projects").document(id).update("name", name)
    }

    fun deleteProject(id: String) {
        db.collection("$BASE/projects").document(id).delete()
    }

    fun clearError() { _errorMessage.value = null }

    private fun calculateNextDueDate(currentDueDate: String?, repeatType: String): String? {
        if (currentDueDate.isNullOrEmpty()) return null
        return try {
            val cal = Calendar.getInstance()
            val parts = currentDueDate.split("T")
            val dateParts = parts[0].split("-")
            cal.set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())
            when (repeatType) {
                "daily" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                "weekly" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                "monthly" -> cal.add(Calendar.MONTH, 1)
                "annually" -> cal.add(Calendar.YEAR, 1)
            }
            val y = cal.get(Calendar.YEAR)
            val m = String.format("%02d", cal.get(Calendar.MONTH) + 1)
            val d = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))
            val time = if (parts.size > 1) parts[1] else "00:00"
            "$y-$m-${d}T$time"
        } catch (e: Exception) { null }
    }

    private fun clearDataListeners() {
        dataListeners.forEach { it.remove() }
        dataListeners.clear()
        projectTaskListeners.values.forEach { it.remove() }
        projectTaskListeners.clear()
        _tasks.value = emptyList()
        _tasksForAll.value = emptyList()
        _projects.value = emptyList()
        _staff.value = emptyList()
        _projectTasks.value = emptyMap()
    }

    override fun onCleared() {
        super.onCleared()
        clearDataListeners()
    }
}
