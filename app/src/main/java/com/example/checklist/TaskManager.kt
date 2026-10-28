package com.example.checklist

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class TaskManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("tasks_db", Context.MODE_PRIVATE)
    private val tasksKey = "tasks"
    private val routinesKey = "routines"

    fun getTasks(): List<Task> {
        val jsonString = sharedPreferences.getString(tasksKey, null)
        return jsonString?.let(::parseTasksJson) ?: emptyList()
    }

    fun exportBackupJson(): String = JSONObject()
        .put("formatVersion", 1)
        .put("tasks", taskListToJson(getTasks()))
        .put("routines", routineListToJson(getRoutines()))
        .toString()

    fun parseTasksJson(json: String): List<Task> {
        return parseTasksArray(JSONArray(json))
    }

    fun parseBackupJson(json: String): TaskBackup {
        val backup = JSONObject(json)
        require(backup.getInt("formatVersion") == 1) { "Unsupported backup version" }
        return TaskBackup(
            tasks = parseTasksArray(backup.getJSONArray("tasks")),
            routines = parseRoutinesArray(backup.getJSONArray("routines"))
        )
    }

    fun replaceWithBackup(backup: TaskBackup) {
        sharedPreferences.edit()
            .putString(tasksKey, taskListToJson(backup.tasks).toString())
            .putString(routinesKey, routineListToJson(backup.routines).toString())
            .apply()
    }

    private fun parseTasksArray(jsonArray: JSONArray): List<Task> {
        val tasks = mutableListOf<Task>()
        val taskIds = mutableSetOf<String>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val task = Task(
                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                name = obj.getString("name"),
                order = obj.getInt("order"),
                startTime = obj.getString("startTime"),
                endTime = obj.getString("endTime"),
                routineId = obj.optString("routineId", ""),
                completed = obj.optBoolean("completed", false),
                emoji = obj.optString("emoji", ""),
                daysOfWeek = obj.optJSONArray("days")?.let { days ->
                    (0 until days.length()).map { days.getInt(it) }.filter { it in 1..7 }.toSet()
                }?.takeIf { it.isNotEmpty() } ?: ALL_DAYS_OF_WEEK
            )
            require(task.id.isNotBlank()) { "Task IDs cannot be empty" }
            require(taskIds.add(task.id)) { "Task IDs must be unique" }
            tasks.add(task)
        }
        return tasks
    }

    fun saveTasks(tasks: List<Task>) {
        sharedPreferences.edit().putString(tasksKey, taskListToJson(tasks).toString()).apply()
    }

    private fun taskListToJson(tasks: List<Task>): JSONArray {
        val jsonArray = JSONArray()
        for (task in tasks) {
            val obj = JSONObject()
            obj.put("id", task.id)
            obj.put("name", task.name)
            obj.put("order", task.order)
            obj.put("startTime", task.startTime)
            obj.put("endTime", task.endTime)
            obj.put("routineId", task.routineId)
            obj.put("completed", task.completed)
            obj.put("emoji", task.emoji)
            obj.put("days", JSONArray(task.daysOfWeek.sorted()))
            jsonArray.put(obj)
        }
        return jsonArray
    }

    fun saveTask(task: Task) {
        val tasks = getTasks().toMutableList()
        val existingIndex = tasks.indexOfFirst { it.id == task.id }
        if (existingIndex >= 0) {
            tasks[existingIndex] = task
        } else {
            tasks.add(task)
        }
        saveTasks(tasks)
    }

    fun deleteTask(taskId: String) {
        val tasks = getTasks().toMutableList()
        tasks.removeAll { it.id == taskId }
        saveTasks(tasks)
    }

    fun deleteAllAndInitFromAssets(context: Context, assetsFilePath: String) {
        sharedPreferences.edit().remove(tasksKey).apply()
        val inputStream = context.assets.open(assetsFilePath)
        val json = inputStream.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(json)
        val tasks = mutableListOf<Task>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            tasks.add(Task(
                id = java.util.UUID.randomUUID().toString(),
                name = obj.getString("name"),
                order = obj.getInt("order"),
                startTime = obj.getString("startTime"),
                endTime = obj.getString("endTime"),
                routineId = "",
                completed = false
            ))
        }
        saveTasks(tasks)
    }

    fun getRoutines(): List<Routine> {
        val jsonString = sharedPreferences.getString(routinesKey, null)
        return if (jsonString != null) {
            parseRoutinesArray(JSONArray(jsonString))
        } else {
            emptyList()
        }
    }

    fun saveRoutines(routines: List<Routine>) {
        sharedPreferences.edit().putString(routinesKey, routineListToJson(routines).toString()).apply()
    }

    private fun parseRoutinesArray(jsonArray: JSONArray): List<Routine> {
        val routines = mutableListOf<Routine>()
        val routineIds = mutableSetOf<String>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val routine = Routine(
                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                name = obj.getString("name"),
                startTime = obj.getString("startTime"),
                endTime = obj.getString("endTime")
            )
            require(routine.id.isNotBlank()) { "Routine IDs cannot be empty" }
            require(routineIds.add(routine.id)) { "Routine IDs must be unique" }
            routines.add(routine)
        }
        return routines
    }

    private fun routineListToJson(routines: List<Routine>): JSONArray {
        val jsonArray = JSONArray()
        for (routine in routines) {
            val obj = JSONObject()
            obj.put("id", routine.id)
            obj.put("name", routine.name)
            obj.put("startTime", routine.startTime)
            obj.put("endTime", routine.endTime)
            jsonArray.put(obj)
        }
        return jsonArray
    }

    fun saveRoutine(routine: Routine) {
        val routines = getRoutines().toMutableList()
        val existingIndex = routines.indexOfFirst { it.id == routine.id }
        if (existingIndex >= 0) {
            routines[existingIndex] = routine
        } else {
            routines.add(routine)
        }
        saveRoutines(routines)
    }

    fun deleteRoutine(routineId: String) {
        val routines = getRoutines().toMutableList()
        routines.removeAll { it.id == routineId }
        saveRoutines(routines)
        // Also remove tasks from removed routine
        val tasks = getTasks().toMutableList()
        tasks.removeAll { it.routineId == routineId }
        saveTasks(tasks)
    }
}

data class TaskBackup(val tasks: List<Task>, val routines: List<Routine>)
