package com.example.checklist

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import java.util.*

class EditTaskActivity : AppCompatActivity() {

    private lateinit var taskManager: TaskManager
    private lateinit var nameEditText: EditText
    private lateinit var orderEditText: EditText
    private var taskId: String? = null
    private var selectedRoutineId: String? = null

    private fun setModalTitle(title: String) {
        this.title = title
        supportActionBar?.title = title
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_task)

        taskManager = TaskManager(this)

        nameEditText = findViewById(R.id.taskNameEditText)
        orderEditText = findViewById(R.id.orderEditText)
        val saveButton = findViewById<AppCompatButton>(R.id.saveButton)
        val deleteButton = findViewById<ImageButton>(R.id.deleteButton)
        val cancelButton = findViewById<AppCompatButton>(R.id.cancelButton)

        taskId = intent.getStringExtra("task_id")
        selectedRoutineId = intent.getStringExtra("routine_id")

        if (taskId != null) {
            // Edit mode
            val task = taskManager.getTasks().find { it.id == taskId }
            if (task != null) {
                nameEditText.setText(task.name)
                selectedRoutineId = task.routineId
                orderEditText.setText(task.order.toString())
            }
            setModalTitle("Edit Task")
            deleteButton.visibility = android.view.View.VISIBLE
            deleteButton.setOnClickListener {
                taskManager.deleteTask(taskId!!)
                Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            // Add mode
            setModalTitle("Add Task")
            deleteButton.visibility = android.view.View.GONE

            selectedRoutineId?.let { rid ->
                val maxOrder = taskManager.getTasks().filter { it.routineId == rid }.maxOfOrNull { it.order } ?: 0
                orderEditText.setText((maxOrder + 1).toString())
            }
        }

        saveButton.setOnClickListener {
            if (validateInput()) {
                val routine = selectedRoutineId?.let { taskManager.getRoutines().find { it.id == selectedRoutineId } }
                val startTime = routine?.startTime ?: "09:00"
                val endTime = routine?.endTime ?: "17:00"
                val task = Task(
                    id = taskId ?: java.util.UUID.randomUUID().toString(),
                    name = nameEditText.text.toString(),
                    order = orderEditText.text.toString().toInt(),
                    startTime = startTime,
                    endTime = endTime,
                    routineId = selectedRoutineId ?: ""
                )
                taskManager.saveTask(task)
                Toast.makeText(this, "Task saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun validateInput(): Boolean {
        val name = nameEditText.text.toString().trim()
        val order = orderEditText.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "Task name cannot be empty", Toast.LENGTH_SHORT).show()
            return false
        }

        if (order.isEmpty()) {
            Toast.makeText(this, "Order must be a number", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }
}
