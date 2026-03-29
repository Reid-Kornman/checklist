package com.example.checklist

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TaskAdapter
    private lateinit var taskManager: TaskManager
    private lateinit var routineSpinner: Spinner
    private var selectedRoutineId: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        taskManager = TaskManager(this)

        routineSpinner = findViewById(R.id.routineSpinner)
        selectedRoutineId = intent.getStringExtra("routine_id")

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TaskAdapter(taskManager) { taskId ->
            val intent = Intent(this, EditTaskActivity::class.java)
            intent.putExtra("task_id", taskId)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        val manageRoutinesButton = findViewById<Button>(R.id.manageRoutinesButton)
        manageRoutinesButton.setOnClickListener {
            val intent = Intent(this, ManageRoutinesActivity::class.java)
            startActivity(intent)
        }

        val addTaskButton = findViewById<Button>(R.id.addTaskButton)
        addTaskButton.setOnClickListener {
            selectedRoutineId?.let { currentRoutineId ->
                val intent = Intent(this, EditTaskActivity::class.java)
                intent.putExtra("routine_id", currentRoutineId)
                startActivity(intent)
            }
        }

        val resetStatusButton = findViewById<Button>(R.id.resetStatusButton)
        resetStatusButton.setOnClickListener {
            val tasks = taskManager.getTasks().map { it.copy(completed = false) }
            taskManager.saveTasks(tasks)
            loadTasks()
        }

        // Initialize routines and tasks from assets if empty
        if (taskManager.getRoutines().isEmpty()) {
            val defaultRoutine = Routine(java.util.UUID.randomUUID().toString(), "Default", "09:00", "23:59")
            taskManager.saveRoutine(defaultRoutine)
        }

        refreshRoutineSpinner()
        loadTasks()
    }

    override fun onResume() {
        super.onResume()
        refreshRoutineSpinner()
        loadTasks()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_help -> {
                showHelpDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refreshRoutineSpinner() {
        val routines = taskManager.getRoutines()
        val routineDisplayNames = routines.map {
            "${it.name} (${toAmPm(it.startTime)} - ${toAmPm(it.endTime)})"
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, routineDisplayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        routineSpinner.adapter = adapter

        if (routines.isNotEmpty()) {
            val selectedIndex = routines.indexOfFirst { it.id == selectedRoutineId }.takeIf { it >= 0 } ?: 0
            routineSpinner.setSelection(selectedIndex)
            selectedRoutineId = routines[selectedIndex].id
        }

        routineSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedRoutineId = routines[position].id
                loadTasks()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                selectedRoutineId = null
            }
        }
    }

    private fun loadTasks() {
        val routineId = selectedRoutineId ?: return
        val tasksForRoutine = taskManager.getTasks().filter { it.routineId == routineId }.sortedBy { it.order }
        adapter.updateTasks(tasksForRoutine)
    }

    private fun showHelpDialog() {
        val appName = getString(R.string.app_name)
        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName ?: "N/A"
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toString()
        val versionInfo = "$versionName"
        val message = HtmlCompat.fromHtml(
            getString(
                R.string.help_dialog_html,
                appName,
                versionInfo,
                getString(R.string.support_email),
                getString(R.string.donation_url)
            ),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.help_dialog_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        dialog.show()
        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
    }
}

data class Task(
    val id: String,
    val name: String,
    val order: Int,
    val startTime: String,
    val endTime: String,
    val routineId: String,
    val completed: Boolean = false
)

data class Routine(
    val id: String,
    val name: String,
    val startTime: String,
    val endTime: String
)

class TaskAdapter(
    private val taskManager: TaskManager,
    private val onEditClick: (taskId: String) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val tasks = mutableListOf<Task>()

    fun updateTasks(newTasks: List<Task>) {
        tasks.clear()
        tasks.addAll(newTasks)
        notifyDataSetChanged()
    }

    fun updateTaskAt(position: Int, task: Task) {
        if (position in tasks.indices) {
            tasks[position] = task
            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): TaskViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view, taskManager, onEditClick)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position], position)
    }

    override fun getItemCount() = tasks.size

    class TaskViewHolder(
        itemView: android.view.View,
        private val taskManager: TaskManager,
        private val onEditClick: (taskId: String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val taskName: android.widget.TextView = itemView.findViewById(R.id.taskName)
        private val editButton: android.widget.ImageButton = itemView.findViewById(R.id.editButton)
        private val completeToggle: android.widget.Switch = itemView.findViewById(R.id.completeToggle)
        private val rootLayout: android.view.View = itemView

        fun bind(task: Task, position: Int) {
            taskName.text = task.name
            
            // Set alternating background colors
            val backgroundColor = if (position % 2 == 0) {
                android.graphics.Color.parseColor("#F5F5F5")
            } else {
                android.graphics.Color.parseColor("#FFFFFF")
            }
            rootLayout.setBackgroundColor(backgroundColor)
            
            if (task.completed) {
                taskName.paintFlags = taskName.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                taskName.paintFlags = taskName.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            // Prevent previous recycled listeners from firing when setting checked state
            completeToggle.setOnCheckedChangeListener(null)
            completeToggle.isChecked = task.completed
            completeToggle.setOnCheckedChangeListener { _, isChecked ->
                val updatedTask = task.copy(completed = isChecked)
                taskManager.saveTask(updatedTask)

                // Keep the adapter's cached list in sync until next load
                (bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION })?.let { listPosition ->
                    (itemView.parent as? RecyclerView)?.adapter?.let {
                        if (it is TaskAdapter) {
                            it.updateTaskAt(listPosition, updatedTask)
                        }
                    }
                }

                // Keep UI state aligned with stored completed state
                if (isChecked) {
                    taskName.paintFlags = taskName.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    taskName.paintFlags = taskName.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
            }
            editButton.setOnClickListener {
                onEditClick(task.id)
            }
        }
    }
}