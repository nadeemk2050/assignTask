package com.assigntask.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private var tasksRef: CollectionReference? = null

    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    private val _status = MutableStateFlow("Starting app...")
    val status: StateFlow<String> = _status.asStateFlow()

    init {
        val apps = FirebaseApp.getApps(application)
        if (apps.isEmpty()) {
            _status.value = "Firebase not configured in APK. Add google-services.json and rebuild."
        } else {
            tasksRef = Firebase.firestore.collection("tasks")
            _status.value = "Connected to Firebase project: assigntask-51813"
            listenForTasks()
        }
    }

    private fun listenForTasks() {
        val ref = tasksRef ?: return

        try {
            ref.orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        _status.value = "Firebase error: ${error.message ?: "Unknown error"}"
                        return@addSnapshotListener
                    }

                    if (snapshot == null) {
                        _tasks.value = emptyList()
                        return@addSnapshotListener
                    }

                    _tasks.value = snapshot.documents.map { doc ->
                        TaskItem(
                            id = doc.id,
                            title = doc.getString("title") ?: "Untitled task",
                            done = doc.getBoolean("done") ?: false
                        )
                    }
                }
        } catch (ex: Exception) {
            _status.value = "Firebase setup error: ${ex.message ?: "Unknown error"}"
        }
    }

    fun addTask(title: String) {
        val normalized = title.trim()
        if (normalized.isEmpty()) return

        val ref = tasksRef
        if (ref == null) {
            _status.value = "Cannot add task: Firebase not configured."
            return
        }

        viewModelScope.launch {
            try {
                ref.add(
                    mapOf(
                        "title" to normalized,
                        "done" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            } catch (ex: Exception) {
                _status.value = "Add task failed: ${ex.message ?: "Unknown error"}"
            }
        }
    }

    fun toggleDone(item: TaskItem) {
        val ref = tasksRef
        if (ref == null) {
            _status.value = "Cannot update task: Firebase not configured."
            return
        }

        viewModelScope.launch {
            try {
                ref.document(item.id).update("done", !item.done)
            } catch (ex: Exception) {
                _status.value = "Update failed: ${ex.message ?: "Unknown error"}"
            }
        }
    }
}
