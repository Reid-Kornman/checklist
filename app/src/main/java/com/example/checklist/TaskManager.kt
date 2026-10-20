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
        return if (jsonString != null) {
            val jsonArray = JSONArray(jsonString)
            val tasks = mutableListOf<Task>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                tasks.add(Task(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    name = obj.getString("name"),
                    order = obj.getInt("order"),
                    startTime = obj.getString("startTime"),
                    endTime = obj.getString("endTime"),
                    routineId = obj.optString("routineId", ""),
                    completed = obj.optBoolean("completed", false),
                    emoji = obj.optString("emoji", "")
                ))
            }
            tasks
        } else {
            emptyList()
        }
    }

    fun saveTasks(tasks: List<Task>) {
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
            jsonArray.put(obj)
        }
        sharedPreferences.edit().putString(tasksKey, jsonArray.toString()).apply()
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
            val jsonArray = JSONArray(jsonString)
            val routines = mutableListOf<Routine>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                routines.add(Routine(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    name = obj.getString("name"),
                    startTime = obj.getString("startTime"),
                    endTime = obj.getString("endTime")
                ))
            }
            routines
        } else {
            emptyList()
        }
    }

    fun saveRoutines(routines: List<Routine>) {
        val jsonArray = JSONArray()
        for (routine in routines) {
            val obj = JSONObject()
            obj.put("id", routine.id)
            obj.put("name", routine.name)
            obj.put("startTime", routine.startTime)
            obj.put("endTime", routine.endTime)
            jsonArray.put(obj)
        }
        sharedPreferences.edit().putString(routinesKey, jsonArray.toString()).apply()
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
