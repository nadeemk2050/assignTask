package com.assigntask.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TaskViewModel : ViewModel() {
    private val tasksRef = Firebase.firestore.collection("tasks")

    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    private val _status = MutableStateFlow("Connected to Firebase project: assigntask-51813")
    val status: StateFlow<String> = _status.asStateFlow()

    init {
        listenForTasks()
    }

    private fun listenForTasks() {
        try {
            tasksRef.orderBy("createdAt", Query.Direction.DESCENDING)
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
            _status.value = "Firebase not configured yet. Add google-services.json to app/."
        }
    }

    fun addTask(title: String) {
        val normalized = title.trim()
        if (normalized.isEmpty()) return

        viewModelScope.launch {
            try {
                tasksRef.add(
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
        viewModelScope.launch {
            try {
                tasksRef.document(item.id).update("done", !item.done)
            } catch (ex: Exception) {
                _status.value = "Update failed: ${ex.message ?: "Unknown error"}"
            }
        }
    }
}
