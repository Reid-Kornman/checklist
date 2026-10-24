package com.example.checklist

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
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
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.ZoneId
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val weekdayRefreshHandler = Handler(Looper.getMainLooper())
    private val weekdayRefreshRunnable = object : Runnable {
        override fun run() {
            if (refreshWeekdayTitleIfNeeded()) loadTasks()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && hasWindowFocus()) {
                weekdayRefreshHandler.postDelayed(this, WEEKDAY_TITLE_REFRESH_INTERVAL)
            }
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TaskAdapter
    private lateinit var taskManager: TaskManager
    private lateinit var routineSpinner: Spinner
    private lateinit var addTaskButton: Button
    private lateinit var editTasksButton: Button
    private lateinit var emptyRoutineState: android.view.View
    private var selectedRoutineId: String? = null
    private var pendingRoutineTaskId: String? = null
    private val exportTasksLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val output = contentResolver.openOutputStream(uri) ?: error("Unable to open file")
            output.use { it.write(taskManager.exportBackupJson().toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, R.string.tasks_exported, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.tasks_export_failed, Toast.LENGTH_LONG).show()
        }
    }
    private val importTasksLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val input = contentResolver.openInputStream(uri) ?: error("Unable to open file")
            val json = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val backup = taskManager.parseBackupJson(json)
            AlertDialog.Builder(this)
                .setTitle(R.string.restore_tasks_title)
                .setMessage(
                    getString(
                        R.string.restore_tasks_confirmation,
                        backup.tasks.size,
                        backup.routines.size
                    )
                )
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.restore_tasks) { _, _ ->
                    taskManager.replaceWithBackup(backup)
                    refreshRoutineSpinner()
                    loadTasks()
                    Toast.makeText(
                        this,
                        getString(R.string.tasks_restored, backup.tasks.size, backup.routines.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.tasks_import_failed, Toast.LENGTH_LONG).show()
        }
    }
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
                if (!adapter.isEditMode) return false
                return adapter.moveTask(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun isLongPressDragEnabled() = false
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
        adapter = TaskAdapter(
            taskManager,
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

        editTasksButton = findViewById(R.id.editTasksButton)
        updateEditTasksButton(false)
        editTasksButton.setOnClickListener {
            if (adapter.isEditMode) {
                if (!adapter.saveChanges()) {
                    Toast.makeText(this, R.string.task_name_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(editTasksButton.windowToken, 0)
                routineSpinner.isEnabled = true
                updateEditTasksButton(false)
            } else {
                adapter.beginEditing()
                routineSpinner.isEnabled = false
                updateEditTasksButton(true)
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
        scheduleWeekdayTitleRefresh()
        refreshRoutineSpinner()
        loadTasks()
    }

    override fun onPause() {
        weekdayRefreshHandler.removeCallbacks(weekdayRefreshRunnable)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            scheduleWeekdayTitleRefresh()
        } else {
            weekdayRefreshHandler.removeCallbacks(weekdayRefreshRunnable)
        }
    }

    private fun scheduleWeekdayTitleRefresh() {
        weekdayRefreshHandler.removeCallbacks(weekdayRefreshRunnable)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && hasWindowFocus()) {
            if (refreshWeekdayTitleIfNeeded()) loadTasks()
            weekdayRefreshHandler.postDelayed(weekdayRefreshRunnable, WEEKDAY_TITLE_REFRESH_INTERVAL)
        }
    }

    private fun refreshWeekdayTitleIfNeeded(force: Boolean = false): Boolean {
        if (!hasWindowFocus() || getSystemService(PowerManager::class.java)?.isInteractive != true) return false

        val preferences = getSharedPreferences(ChecklistApplication.PREFERENCES_NAME, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastRefresh = preferences.getLong(WEEKDAY_TITLE_REFRESH_KEY, 0L)
        val today = LocalDate.now()
        val lastRefreshDate = lastRefresh.takeIf { it != 0L }
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        var tasksWereReset = false
        val weekday = if (force || lastRefresh == 0L || now - lastRefresh > WEEKDAY_TITLE_REFRESH_INTERVAL) {
            if (lastRefreshDate != null && lastRefreshDate != today) {
                val tasks = taskManager.getTasks()
                if (tasks.any { it.completed }) {
                    taskManager.saveTasks(tasks.map { it.copy(completed = false) })
                    tasksWereReset = true
                }
            }
            today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).also {
                preferences.edit()
                    .putLong(WEEKDAY_TITLE_REFRESH_KEY, now)
                    .putString(WEEKDAY_TITLE_VALUE_KEY, it)
                    .apply()
            }
        } else {
            preferences.getString(WEEKDAY_TITLE_VALUE_KEY, null)
                ?: today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        }
        title = "Checklist - $weekday"
        return tasksWereReset
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

    companion object {
        private const val WEEKDAY_TITLE_REFRESH_INTERVAL = 60 * 60 * 1000L
        private const val WEEKDAY_TITLE_REFRESH_KEY = "weekday_title_last_refresh"
        private const val WEEKDAY_TITLE_VALUE_KEY = "weekday_title_value"
    }

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
                    refreshWeekdayTitleIfNeeded(force = true)
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
        if (!adapter.isEditMode) {
            adapter.beginEditing()
            routineSpinner.isEnabled = false
            updateEditTasksButton(true)
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

    private fun updateEditTasksButton(isSaving: Boolean) {
        editTasksButton.text = getString(if (isSaving) R.string.save_tasks else R.string.edit_tasks)
        val iconSize = (24 * resources.displayMetrics.density).toInt()
        val iconResource = if (isSaving) R.drawable.ic_check else android.R.drawable.ic_menu_edit
        val icon = androidx.core.content.ContextCompat.getDrawable(this, iconResource)
            ?.mutate()
            ?.apply { setBounds(0, 0, iconSize, iconSize) }
        editTasksButton.setCompoundDrawablesRelative(icon, null, null, null)
        val tint = if (isSaving) R.color.action_save else R.color.action_edit
        editTasksButton.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(getColor(tint))
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
            .setNeutralButton(R.string.task_backup) { _, _ -> showTaskBackupOptions() }
            .setPositiveButton(android.R.string.ok, null)
            .create()

        dialog.show()
        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun showTaskBackupOptions() {
        AlertDialog.Builder(this)
            .setTitle(R.string.task_backup)
            .setItems(R.array.task_backup_options) { _, selection ->
                if (adapter.isEditMode) {
                    Toast.makeText(this, R.string.save_task_edits_before_backup, Toast.LENGTH_SHORT).show()
                } else if (selection == 0) {
                    exportTasksLauncher.launch("checklist-backup.json")
                } else {
                    importTasksLauncher.launch(arrayOf("application/json"))
                }
            }
            .show()
    }
}

data class Task(
    val id: String,
    val name: String,
    val order: Int,
    val startTime: String,
    val endTime: String,
    val routineId: String,
    val completed: Boolean = false,
    val emoji: String = ""
)

data class Routine(
    val id: String,
    val name: String,
    val startTime: String,
    val endTime: String
)

class TaskAdapter(
    private val taskManager: TaskManager,
    private val onDeleteClick: (Task) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onTaskListChanged: () -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val tasks = mutableListOf<Task>()
    private val editedNames = mutableMapOf<String, String>()
    var isEditMode = false
        private set

    fun updateTasks(newTasks: List<Task>) {
        editedNames.keys.retainAll(newTasks.mapTo(mutableSetOf()) { it.id })
        tasks.clear()
        tasks.addAll(newTasks)
        if (isEditMode) {
            tasks.forEach { task -> editedNames.putIfAbsent(task.id, task.name) }
        }
        notifyDataSetChanged()
        onTaskListChanged()
    }

    fun updateTaskAt(position: Int, task: Task) {
        if (position in tasks.indices) {
            tasks[position] = task
            notifyItemChanged(position)
        }
    }

    fun updateTaskEmoji(taskId: String, emoji: String) {
        val position = tasks.indexOfFirst { it.id == taskId }
        if (position < 0) return
        tasks[position] = tasks[position].copy(emoji = emoji)
        notifyItemChanged(position)
    }

    fun beginEditing() {
        if (isEditMode) return
        isEditMode = true
        tasks.forEach { editedNames[it.id] = it.name }
        notifyDataSetChanged()
    }

    fun addTask(task: Task): Boolean {
        if (!isEditMode) return false
        val position = tasks.size
        tasks.add(task)
        editedNames[task.id] = ""
        notifyItemInserted(position)
        onTaskListChanged()
        return true
    }

    fun updateEditingDraft(taskId: String, draft: String) {
        if (isEditMode && taskId in editedNames) editedNames[taskId] = draft
    }

    fun saveChanges(): Boolean {
        val updatedTasks = tasks.mapIndexed { index, task ->
            val name = editedNames[task.id]?.trim().orEmpty()
            if (name.isEmpty()) return false
            task.copy(name = name, order = index)
        }
        val updatedTasksById = updatedTasks.associateBy { it.id }
        val allTasks = taskManager.getTasks().map { task -> updatedTasksById[task.id] ?: task }
        taskManager.saveTasks(allTasks + updatedTasks.filter { updated -> allTasks.none { it.id == updated.id } })
        tasks.clear()
        tasks.addAll(updatedTasks)
        editedNames.clear()
        isEditMode = false
        notifyDataSetChanged()
        return true
    }

    fun deleteTask(taskId: String) {
        val position = tasks.indexOfFirst { it.id == taskId }
        taskManager.deleteTask(taskId)
        editedNames.remove(taskId)
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
        return TaskViewHolder(view, this, taskManager, onDeleteClick, onStartDrag)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.bind(task, position, isEditMode, editedNames[task.id].orEmpty())
    }

    override fun getItemCount() = tasks.size

    class TaskViewHolder(
        itemView: android.view.View,
        private val adapter: TaskAdapter,
        private val taskManager: TaskManager,
        private val onDeleteClick: (Task) -> Unit,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val taskName: android.widget.TextView = itemView.findViewById(R.id.taskName)
        private val taskNameEdit: android.widget.EditText = itemView.findViewById(R.id.taskNameEdit)
        private val taskBullet: android.widget.TextView = itemView.findViewById(R.id.taskBullet)
        private val taskEmoji: android.widget.TextView = itemView.findViewById(R.id.taskEmoji)
        private val dragHandle: android.widget.ImageButton = itemView.findViewById(R.id.dragHandle)
        private val emojiPickerButton: android.widget.TextView = itemView.findViewById(R.id.emojiPickerButton)
        private val completeToggle: android.widget.Switch = itemView.findViewById(R.id.completeToggle)
        private val deleteButton: android.widget.ImageButton = itemView.findViewById(R.id.deleteTaskButton)
        private val rootLayout: android.view.View = itemView
        private var draftWatcher: TextWatcher? = null

        fun bind(task: Task, position: Int, isEditMode: Boolean, editDraft: String) {
            taskName.text = task.name
            taskName.visibility = if (isEditMode) android.view.View.GONE else android.view.View.VISIBLE
            taskNameEdit.visibility = if (isEditMode) android.view.View.VISIBLE else android.view.View.GONE
            taskBullet.visibility = if (!isEditMode && task.emoji.isEmpty()) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            taskEmoji.text = task.emoji
            taskEmoji.visibility = if (!isEditMode && task.emoji.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            dragHandle.visibility = if (isEditMode) android.view.View.VISIBLE else android.view.View.GONE
            emojiPickerButton.text = task.emoji.ifEmpty { "+" }
            emojiPickerButton.contentDescription = itemView.context.getString(R.string.choose_task_emoji)
            emojiPickerButton.visibility = if (isEditMode) android.view.View.VISIBLE else android.view.View.GONE
            deleteButton.visibility = if (isEditMode) android.view.View.VISIBLE else android.view.View.GONE
            completeToggle.visibility = if (isEditMode) android.view.View.GONE else android.view.View.VISIBLE

            draftWatcher?.let(taskNameEdit::removeTextChangedListener)
            draftWatcher = null
            if (isEditMode) {
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
            } else {
                taskNameEdit.setText("")
            }

            dragHandle.setOnTouchListener(
                if (isEditMode) android.view.View.OnTouchListener { _, event ->
                    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                        onStartDrag(this)
                    }
                    true
                } else {
                    null
                }
            )
            emojiPickerButton.setOnClickListener { showEmojiPicker(emojiPickerButton, task.id) }
            
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
            deleteButton.setOnClickListener {
                onDeleteClick(task)
            }
        }

        private fun showEmojiPicker(anchor: android.view.View, taskId: String) {
            val context = itemView.context
            val categories = listOf(
                R.string.emoji_category_home to listOf(
                    "🧹", "🧺", "🧼", "🛏️", "🪥", "🚿", "🛁", "🧽",
                    "🧴", "🪟", "🧻", "🧯", "🪴", "🧷", "🧹", "🧹",
                    "🧦", "👕", "👗", "🧥", "🪣", "🧹", "🧺", "🪠",
                    "🛋️", "🪑", "🛁", "🪞", "🧸", "🕯️", "🪜", "🧰"
                ),
                R.string.emoji_category_food to listOf(
                    "🍽️", "🥣", "🍎", "🍌", "🥑", "🥪", "🥗", "🍳",
                    "🥛", "☕", "🍵", "🧃", "🍞", "🍕", "🍲", "🍪",
                    "🍊", "🍓", "🫐", "🍇", "🍉", "🥕", "🥦", "🌽",
                    "🍚", "🍜", "🌮", "🥘", "🧁", "🍫", "🥤", "🍼"
                ),
                R.string.emoji_category_health to listOf(
                    "💧", "🧘", "🏃", "💊", "🩺", "🩹", "🚴", "🏋️",
                    "🧠", "😴", "🛌", "🧘‍♀️", "🧘‍♂️", "🦷", "🧴", "❤️",
                    "🧑‍⚕️", "🩻", "🌡️", "🧪", "🩼", "🦵", "💪", "🫀",
                    "🚶", "🤸", "🏃‍♀️", "🏃‍♂️", "🥗", "🍎", "🧘", "🛀"
                ),
                R.string.emoji_category_work to listOf(
                    "📚", "✍️", "💻", "📞", "📝", "📖", "📓", "📅",
                    "📧", "🖥️", "⌨️", "🖨️", "📊", "🎓", "🗂️", "📌",
                    "📒", "📔", "📕", "📗", "📘", "📙", "🗒️", "📇",
                    "🖊️", "🖋️", "✏️", "📐", "📏", "🧮", "📈", "📉",
                    "🗓️", "🧑‍💻", "🗃️", "📂", "📎", "🖇️", "🔍", "💼"
                ),
                R.string.emoji_category_errands to listOf(
                    "🛒", "💰", "📦", "🏦", "📮", "🧾", "🛍️", "🔑",
                    "🧺", "🏪", "🚚", "🧹", "🛠️", "📬", "💳", "🗑️",
                    "🏧", "🪙", "💵", "💸", "🧮", "🪪", "🛃", "🛂",
                    "🧰", "🔧", "🔨", "🪛", "🪜", "📫", "🧹", "🧼",
                    "🏣", "🏤", "🛒", "🛍️", "🧾", "📋", "🔋", "🪫"
                ),
                R.string.emoji_category_travel to listOf(
                    "🚗", "🚲", "🚌", "🚆", "✈️", "🚕", "⛽", "🗺️",
                    "🧳", "🚦", "🚶", "🏠", "🚇", "🛴", "🚢", "🛣️",
                    "🚙", "🚐", "🚎", "🚃", "🚄", "🚅", "🚉", "🚏",
                    "🚁", "🛩️", "🛫", "🛬", "🛥️", "⛴️", "🚤", "🛶",
                    "🛞", "🅿️", "🧭", "🏕️", "🏨", "🛎️", "🎫", "🛂"
                ),
                R.string.emoji_category_nature to listOf(
                    "🐕", "🐈", "🌱", "🌿", "🌷", "🌻", "🌳", "🌼",
                    "🐾", "🐈‍⬛", "🐦", "🐠", "☀️", "🌧️", "🌈", "🌎",
                    "🐶", "🐱", "🐰", "🐹", "🐢", "🐟", "🐬", "🦋",
                    "🌵", "🌴", "🍀", "🌺", "🌹", "🍁", "🍂", "🌲",
                    "🌤️", "⛅", "🌩️", "❄️", "🌊", "🌙", "⭐", "🌍"
                ),
                R.string.emoji_category_activities to listOf(
                    "🎵", "⭐", "🎨", "🎸", "🎮", "⚽", "🏀", "🏊",
                    "🎬", "📷", "🧩", "🎤", "🎹", "🏕️", "🎾", "🪁",
                    "🎧", "🎼", "🥁", "🎻", "🎷", "🎺", "🪇", "🎭",
                    "🎟️", "🎲", "♟️", "🃏", "🪄", "🛼", "🏸", "🏓",
                    "🏐", "🏈", "⚾", "🥎", "🥊", "🧗", "🏄", "🎣"
                ),
                R.string.emoji_category_symbols to listOf(
                    "❤️", "✅", "❌", "❗", "❓", "💡", "🔔", "⏰",
                    "📍", "🔒", "♻️", "➕", "➖", "💤", "✨", "🔥",
                    "💯", "⭐", "🌟", "💫", "⚠️", "🚫", "⛔", "🔴",
                    "🟠", "🟡", "🟢", "🔵", "🟣", "⚫", "⚪", "🟤",
                    "🔓", "🔑", "🔕", "📣", "🏁", "🎯", "➰", "💬"
                )
            )
            val categoryIcons = listOf("🏠", "🍽️", "💊", "📚", "🛒", "🚗", "🌿", "🎨", "✅")
            val density = context.resources.displayMetrics.density
            val cellSize = (48 * density).toInt()
            val padding = (4 * density).toInt()
            val popupWidth = taskNameEdit.width.takeIf { it > 0 } ?: anchor.width
            val columns = ((popupWidth - padding * 2) / cellSize).coerceAtLeast(1)
            val grid = GridLayout(context).apply {
                columnCount = columns
                setPadding(padding, padding, padding, padding)
            }
            val categoryTabs = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val gridScroll = ScrollView(context).apply {
                isFillViewport = true
                addView(grid)
            }
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    categoryTabs,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (48 * density).toInt()
                    )
                )
                addView(
                    android.view.View(context).apply {
                        setBackgroundColor(context.getColor(R.color.emoji_picker_divider))
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * density).toInt()
                    ).apply {
                        topMargin = (4 * density).toInt()
                        bottomMargin = (4 * density).toInt()
                    }
                )
                addView(
                    gridScroll,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                )
            }
            val popup = PopupWindow(
                content,
                popupWidth,
                (260 * density).toInt(),
                true
            ).apply {
                setBackgroundDrawable(ColorDrawable(context.getColor(R.color.task_row_odd)))
                elevation = 4 * density
                isOutsideTouchable = true
            }

            fun addEmojiOption(index: Int, emoji: String) {
                val option = TextView(context).apply {
                    text = emoji.ifEmpty { "×" }
                    textSize = 24f
                    gravity = Gravity.CENTER
                    contentDescription = if (emoji.isEmpty()) {
                        context.getString(R.string.clear_task_emoji)
                    } else {
                        context.getString(R.string.task_emoji_option, emoji)
                    }
                    setBackgroundResource(android.R.drawable.list_selector_background)
                    setOnClickListener {
                        adapter.updateTaskEmoji(taskId, emoji)
                        popup.dismiss()
                    }
                }
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(index / columns),
                    GridLayout.spec(index % columns)
                ).apply {
                    width = cellSize
                    height = cellSize
                }
                grid.addView(option, params)
            }

            fun showCategory(categoryIndex: Int) {
                categoryTabs.removeAllViews()
                categories.forEachIndexed { index, category ->
                    val categoryName = context.getString(category.first)
                    val tab = TextView(context).apply {
                        text = categoryIcons[index]
                        textSize = 20f
                        gravity = Gravity.CENTER
                        contentDescription = categoryName
                        tooltipText = categoryName
                        setBackgroundResource(android.R.drawable.list_selector_background)
                        isSelected = index == categoryIndex
                        alpha = if (isSelected) 1f else 0.65f
                        if (isSelected) {
                            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                        }
                        setOnClickListener { showCategory(index) }
                    }
                    categoryTabs.addView(
                        tab,
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1f
                        )
                    )
                }

                grid.removeAllViews()
                val emojis = listOf("") + categories[categoryIndex].second.distinct()
                emojis.forEachIndexed(::addEmojiOption)
                gridScroll.scrollTo(0, 0)
            }

            showCategory(0)
            popup.showAsDropDown(anchor, anchor.width, -anchor.height)
        }
    }
}