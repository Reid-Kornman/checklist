package com.example.checklist

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class EditRoutineActivity : AppCompatActivity() {

    private lateinit var taskManager: TaskManager
    private lateinit var nameEditText: EditText
    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var saveButton: Button

    private var routineId: String? = null
    private var startTime = "09:00"
    private var endTime = "18:00"

    private fun setModalTitle(title: String) {
        this.title = title
        supportActionBar?.title = title
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_routine)

        taskManager = TaskManager(this)

        nameEditText = findViewById(R.id.routineNameEditText)
        startTimeButton = findViewById(R.id.startTimeButton)
        endTimeButton = findViewById(R.id.endTimeButton)
        saveButton = findViewById(R.id.saveRoutineButton)
        val cancelButton = findViewById<Button>(R.id.cancelRoutineButton)
        val deleteButton = findViewById<ImageButton>(R.id.deleteRoutineButton)

        routineId = intent.getStringExtra("routine_id")

        if (routineId != null) {
            val routine = taskManager.getRoutines().find { it.id == routineId }
            if (routine != null) {
                nameEditText.setText(routine.name)
                startTime = routine.startTime
                endTime = routine.endTime
                updateTimeButtons()
                deleteButton.visibility = android.view.View.VISIBLE
            }
            setModalTitle("Edit Routine")
        } else {
            setModalTitle("Add Routine")
            updateTimeButtons()
        }

        startTimeButton.setOnClickListener { showPicker(true) }
        endTimeButton.setOnClickListener { showPicker(false) }

        saveButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Routine name is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val id = routineId ?: java.util.UUID.randomUUID().toString()
            val routine = Routine(id, name, startTime, endTime)
            taskManager.saveRoutine(routine)
            Toast.makeText(this, "Routine saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        cancelButton.setOnClickListener {
            finish()
        }

        deleteButton.setOnClickListener {
            val id = routineId ?: return@setOnClickListener
            taskManager.deleteRoutine(id)
            Toast.makeText(this, "Routine deleted", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showPicker(isStart: Boolean) {
        val time = if (isStart) startTime else endTime
        val parts = time.split(":")
        val hour = parts[0].toInt()
        val min = parts[1].toInt()

        TimePickerDialog(this, { _, h, m ->
            val selected = String.format("%02d:%02d", h, m)
            if (isStart) startTime = selected else endTime = selected
            updateTimeButtons()
        }, hour, min, false).show()
    }

    private fun updateTimeButtons() {
        startTimeButton.text = toAmPm(startTime)
        endTimeButton.text = toAmPm(endTime)
    }
}
