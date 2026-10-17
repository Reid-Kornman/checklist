package com.example.checklist

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TaskAdapter
    private lateinit var taskManager: TaskManager
    private lateinit var routineSpinner: Spinner
    private lateinit var addTaskButton: Button
    private lateinit var emptyRoutineState: android.view.View
    private var selectedRoutineId: String? = null
    private var pendingRoutineTaskId: String? = null
    private val addRoutineLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(EditRoutineActivity.EXTRA_ROUTINE_ID)?.let { routineId ->
                selectedRoutineId = routineId
                pendingRoutineTaskId = routineId
                addTaskButton.post {
                    if (pendingRoutineTaskId == routineId &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    ) {
                        refreshRoutineSpinner()
                        loadTasks()
                        completeNewRoutineCreation()
                    }
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        taskManager = TaskManager(this)

        routineSpinner = findViewById(R.id.routineSpinner)
        emptyRoutineState = findViewById(R.id.emptyRoutineState)
        selectedRoutineId = intent.getStringExtra("routine_id")

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (!adapter.isReorderMode) return false
                return adapter.moveTask(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun isLongPressDragEnabled() = false
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
        adapter = TaskAdapter(
            taskManager,
            onEditClick = { taskId ->
                adapter.beginEditing(taskId)
            },
            onSaveClick = { taskId, name ->
                if (!adapter.saveTaskName(taskId, name)) {
                    Toast.makeText(this, R.string.task_name_required, Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteClick = { task ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.delete_task_title)
                    .setMessage(getString(R.string.delete_task_confirmation, task.name))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.delete_task) { _, _ -> adapter.deleteTask(task.id) }
                    .show()
            },
            onStartDrag = { viewHolder -> itemTouchHelper.startDrag(viewHolder) },
            onTaskListChanged = { updateEmptyRoutineState() }
        )
        recyclerView.adapter = adapter

        val addRoutineButton = findViewById<android.widget.ImageButton>(R.id.addRoutineButton)
        addRoutineButton.setOnClickListener {
            addRoutineLauncher.launch(Intent(this, EditRoutineActivity::class.java))
        }

        findViewById<Button>(R.id.deleteEmptyRoutineButton).setOnClickListener {
            confirmDeleteSelectedRoutine()
        }

        val reorderTasksButton = findViewById<Button>(R.id.reorderTasksButton)
        reorderTasksButton.setOnClickListener {
            if (adapter.isEditing) return@setOnClickListener
            if (adapter.isReorderMode) {
                saveTaskOrder()
                adapter.setReorderMode(false)
                updateReorderButton(reorderTasksButton, false)
            } else {
                adapter.setReorderMode(true)
                updateReorderButton(reorderTasksButton, true)
            }
        }

        addTaskButton = findViewById(R.id.addTaskButton)
        addTaskButton.setOnClickListener { addTaskForSelectedRoutine() }

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

    override fun onPostResume() {
        super.onPostResume()
        completeNewRoutineCreation()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val darkModeEnabled = getDarkModePreference()
        menu.findItem(R.id.action_dark_mode).apply {
            setIcon(if (darkModeEnabled) R.drawable.ic_light_mode else R.drawable.ic_dark_mode)
            setTitle(if (darkModeEnabled) R.string.dark_mode_switch_to_light else R.string.dark_mode_switch_to_dark)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_dark_mode -> {
                val darkModeEnabled = !getDarkModePreference()
                getSharedPreferences(ChecklistApplication.PREFERENCES_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(ChecklistApplication.DARK_MODE_KEY, darkModeEnabled)
                    .apply()
                AppCompatDelegate.setDefaultNightMode(
                    if (darkModeEnabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
                true
            }
            R.id.action_help -> {
                showHelpDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getDarkModePreference(): Boolean = getSharedPreferences(
        ChecklistApplication.PREFERENCES_NAME,
        MODE_PRIVATE
    ).getBoolean(ChecklistApplication.DARK_MODE_KEY, false)

    private fun refreshRoutineSpinner() {
        val routines = taskManager.getRoutines()
        val routineDisplayNames = routines.map {
            "${it.name} (${toAmPm(it.startTime)} - ${toAmPm(it.endTime)})"
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, routineDisplayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        routineSpinner.onItemSelectedListener = null
        routineSpinner.adapter = adapter

        if (routines.isNotEmpty()) {
            val selectedIndex = routines.indexOfFirst { it.id == selectedRoutineId }.takeIf { it >= 0 } ?: 0
            routineSpinner.setSelection(selectedIndex)
            selectedRoutineId = routines[selectedIndex].id
        } else {
            selectedRoutineId = null
        }

        routineSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val routineId = routines[position].id
                if (selectedRoutineId != routineId) {
                    selectedRoutineId = routineId
                    loadTasks()
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                selectedRoutineId = null
            }
        }
    }

    private fun completeNewRoutineCreation() {
        val pendingRoutineId = pendingRoutineTaskId ?: return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        pendingRoutineTaskId = null
        selectedRoutineId = pendingRoutineId
        refreshRoutineSpinner()
        loadTasks()
        addTaskForSelectedRoutine()
    }

    private fun addTaskForSelectedRoutine() {
        if (adapter.isEditing || adapter.isReorderMode) {
            Toast.makeText(this, R.string.finish_task_edit_first, Toast.LENGTH_SHORT).show()
            return
        }

        val currentRoutineId = selectedRoutineId ?: return
        val routine = taskManager.getRoutines().find { it.id == currentRoutineId } ?: return
        val tasksForRoutine = taskManager.getTasks().filter { it.routineId == currentRoutineId }
        val nextOrder = (tasksForRoutine.maxOfOrNull { it.order } ?: -1) + 1
        val task = Task(
            id = java.util.UUID.randomUUID().toString(),
            name = "",
            order = nextOrder,
            startTime = routine.startTime,
            endTime = routine.endTime,
            routineId = currentRoutineId
        )

        if (adapter.addTask(task)) {
            recyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun loadTasks() {
        val routineId = selectedRoutineId
        if (routineId == null) {
            adapter.updateTasks(emptyList())
            return
        }
        val tasksForRoutine = taskManager.getTasks().filter { it.routineId == routineId }.sortedBy { it.order }
        adapter.updateTasks(tasksForRoutine)
    }

    private fun updateEmptyRoutineState() {
        emptyRoutineState.visibility = if (selectedRoutineId != null && adapter.itemCount == 0) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    private fun confirmDeleteSelectedRoutine() {
        val routineId = selectedRoutineId ?: return
        val routine = taskManager.getRoutines().find { it.id == routineId } ?: return

        AlertDialog.Builder(this)
            .setTitle(R.string.delete_routine_title)
            .setMessage(getString(R.string.delete_routine_confirmation, routine.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_routine) { _, _ ->
                taskManager.deleteRoutine(routineId)
                selectedRoutineId = null
                refreshRoutineSpinner()
                loadTasks()
            }
            .show()
    }

    private fun saveTaskOrder() {
        val reorderedTasks = adapter.tasksInCurrentOrder().mapIndexed { index, task ->
            task.copy(order = index)
        }
        val updatedTasksById = reorderedTasks.associateBy { it.id }
        val allTasks = taskManager.getTasks().map { task -> updatedTasksById[task.id] ?: task }
        taskManager.saveTasks(allTasks)
    }

    private fun updateReorderButton(button: Button, isSaving: Boolean) {
        button.text = getString(if (isSaving) R.string.save_order else R.string.reorder_tasks)
        val icon = if (isSaving) R.drawable.ic_check else android.R.drawable.ic_menu_sort_by_size
        button.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
        button.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(
            getColor(if (isSaving) R.color.action_save else R.color.action_icon)
        )
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
    private val onEditClick: (taskId: String) -> Unit,
    private val onSaveClick: (taskId: String, name: String) -> Unit,
    private val onDeleteClick: (Task) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onTaskListChanged: () -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val tasks = mutableListOf<Task>()
    private var editingTaskId: String? = null
    private var editingTaskDraft = ""
    private var newTaskId: String? = null
    var isReorderMode = false
        private set
    val isEditing: Boolean
        get() = editingTaskId != null

    fun updateTasks(newTasks: List<Task>) {
        if (editingTaskId != null && newTasks.none { it.id == editingTaskId }) {
            editingTaskId = null
            editingTaskDraft = ""
        }
        if (newTaskId != null && newTasks.none { it.id == newTaskId }) newTaskId = null
        tasks.clear()
        tasks.addAll(newTasks)
        notifyDataSetChanged()
        onTaskListChanged()
    }

    fun updateTaskAt(position: Int, task: Task) {
        if (position in tasks.indices) {
            tasks[position] = task
            notifyItemChanged(position)
        }
    }

    fun setReorderMode(enabled: Boolean) {
        isReorderMode = enabled
        notifyDataSetChanged()
    }

    fun beginEditing(taskId: String) {
        if (isReorderMode || isEditing) return
        val task = tasks.find { it.id == taskId } ?: return
        editingTaskId = taskId
        editingTaskDraft = task.name
        notifyItemChanged(tasks.indexOf(task))
    }

    fun addTask(task: Task): Boolean {
        if (isReorderMode || isEditing) return false
        val position = tasks.size
        tasks.add(task)
        editingTaskId = task.id
        editingTaskDraft = ""
        newTaskId = task.id
        notifyItemInserted(position)
        onTaskListChanged()
        return true
    }

    fun updateEditingDraft(taskId: String, draft: String) {
        if (editingTaskId == taskId) editingTaskDraft = draft
    }

    fun saveTaskName(taskId: String, name: String): Boolean {
        val position = tasks.indexOfFirst { it.id == taskId }
        val updatedName = name.trim()
        if (position < 0 || editingTaskId != taskId || updatedName.isEmpty()) return false

        val updatedTask = tasks[position].copy(name = updatedName)
        taskManager.saveTask(updatedTask)
        tasks[position] = updatedTask
        editingTaskId = null
        editingTaskDraft = ""
        newTaskId = null
        notifyItemChanged(position)
        return true
    }

    fun cancelEditing(taskId: String) {
        if (editingTaskId != taskId) return
        val position = tasks.indexOfFirst { it.id == taskId }
        editingTaskId = null
        editingTaskDraft = ""
        if (newTaskId == taskId) {
            newTaskId = null
            if (position >= 0) {
                tasks.removeAt(position)
                notifyItemRemoved(position)
            }
        } else if (position >= 0) {
            notifyItemChanged(position)
        }
        onTaskListChanged()
    }

    fun deleteTask(taskId: String) {
        val position = tasks.indexOfFirst { it.id == taskId }
        taskManager.deleteTask(taskId)
        if (editingTaskId == taskId) {
            editingTaskId = null
            editingTaskDraft = ""
        }
        if (newTaskId == taskId) newTaskId = null
        if (position >= 0) {
            tasks.removeAt(position)
            notifyItemRemoved(position)
        }
        onTaskListChanged()
    }

    fun moveTask(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition !in tasks.indices || toPosition !in tasks.indices) return false
        val task = tasks.removeAt(fromPosition)
        tasks.add(toPosition, task)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun tasksInCurrentOrder(): List<Task> = tasks.toList()

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): TaskViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view, this, taskManager, onEditClick, onSaveClick, onDeleteClick, onStartDrag)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.bind(task, position, isReorderMode, editingTaskId == task.id, editingTaskDraft)
    }

    override fun getItemCount() = tasks.size

    class TaskViewHolder(
        itemView: android.view.View,
        private val adapter: TaskAdapter,
        private val taskManager: TaskManager,
        private val onEditClick: (taskId: String) -> Unit,
        private val onSaveClick: (taskId: String, name: String) -> Unit,
        private val onDeleteClick: (Task) -> Unit,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val taskName: android.widget.TextView = itemView.findViewById(R.id.taskName)
        private val taskNameEdit: android.widget.EditText = itemView.findViewById(R.id.taskNameEdit)
        private val editButton: android.widget.ImageButton = itemView.findViewById(R.id.editButton)
        private val dragHandle: android.widget.ImageButton = itemView.findViewById(R.id.dragHandle)
        private val completeToggle: android.widget.Switch = itemView.findViewById(R.id.completeToggle)
        private val editActions: android.view.View = itemView.findViewById(R.id.taskEditActions)
        private val saveButton: android.widget.ImageButton = itemView.findViewById(R.id.saveTaskButton)
        private val deleteButton: android.widget.ImageButton = itemView.findViewById(R.id.deleteTaskButton)
        private val cancelButton: android.widget.ImageButton = itemView.findViewById(R.id.cancelTaskEditButton)
        private val rootLayout: android.view.View = itemView
        private var draftWatcher: TextWatcher? = null

        fun bind(task: Task, position: Int, isReorderMode: Boolean, isEditing: Boolean, editDraft: String) {
            taskName.text = task.name
            taskName.visibility = if (isEditing) android.view.View.GONE else android.view.View.VISIBLE
            taskNameEdit.visibility = if (isEditing) android.view.View.VISIBLE else android.view.View.GONE
            editButton.visibility = if (isReorderMode || isEditing) android.view.View.GONE else android.view.View.VISIBLE
            dragHandle.visibility = if (isReorderMode) android.view.View.VISIBLE else android.view.View.GONE
            completeToggle.visibility = if (isEditing) android.view.View.GONE else android.view.View.VISIBLE
            editActions.visibility = if (isEditing) android.view.View.VISIBLE else android.view.View.GONE

            draftWatcher?.let(taskNameEdit::removeTextChangedListener)
            draftWatcher = null
            if (isEditing) {
                taskNameEdit.setText(editDraft)
                taskNameEdit.setSelection(taskNameEdit.text.length)
                draftWatcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        adapter.updateEditingDraft(task.id, s?.toString().orEmpty())
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                }
                taskNameEdit.addTextChangedListener(draftWatcher)
                taskNameEdit.post {
                    if (taskNameEdit.visibility == android.view.View.VISIBLE) {
                        taskNameEdit.requestFocus()
                        taskNameEdit.setSelection(taskNameEdit.text.length)
                        val inputMethodManager = itemView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                                as android.view.inputmethod.InputMethodManager
                        inputMethodManager.showSoftInput(taskNameEdit, 0)
                    }
                }
            } else {
                taskNameEdit.setText("")
            }

            dragHandle.setOnTouchListener(
                if (isReorderMode) android.view.View.OnTouchListener { _, event ->
                    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                        onStartDrag(this)
                    }
                    true
                } else {
                    null
                }
            )
            
            // Set alternating background colors
            val backgroundColor = itemView.context.getColor(
                if (position % 2 == 0) R.color.task_row_even else R.color.task_row_odd
            )
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
            saveButton.setOnClickListener {
                if (taskNameEdit.text.toString().isNotBlank()) hideKeyboard()
                onSaveClick(task.id, taskNameEdit.text.toString())
            }
            deleteButton.setOnClickListener {
                hideKeyboard()
                onDeleteClick(task)
            }
            cancelButton.setOnClickListener {
                hideKeyboard()
                adapter.cancelEditing(task.id)
            }
        }

        private fun hideKeyboard() {
            val inputMethodManager = itemView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(taskNameEdit.windowToken, 0)
            taskNameEdit.clearFocus()
        }
    }
}