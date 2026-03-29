package com.example.checklist

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskManagerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var taskManager: TaskManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        taskManager = TaskManager(context)

        // Reset the shared data to avoid leaks from previous runs.
        taskManager.saveTasks(emptyList())
        taskManager.saveRoutines(emptyList())
    }

    @Test
    fun completedStatusPersistsPerRoutine() {
        val routineA = Routine("routine-a-id", "Routine A", "08:00", "12:00")
        val routineB = Routine("routine-b-id", "Routine B", "13:00", "17:00")
        taskManager.saveRoutine(routineA)
        taskManager.saveRoutine(routineB)

        val taskA1 = Task("task-a1", "Task A1", 1, "08:00", "09:00", routineA.id, completed = false)
        val taskA2 = Task("task-a2", "Task A2", 2, "09:00", "10:00", routineA.id, completed = true)
        val taskB1 = Task("task-b1", "Task B1", 1, "13:00", "14:00", routineB.id, completed = false)

        taskManager.saveTasks(listOf(taskA1, taskA2, taskB1))

        // Simulate user checking taskA1 in Routine A
        val updatedTaskA1 = taskA1.copy(completed = true)
        taskManager.saveTask(updatedTaskA1)

        // Reload from storage and verify persistence per routine
        val tasksLoaded = taskManager.getTasks()
        val aTasks = tasksLoaded.filter { it.routineId == routineA.id }.sortedBy { it.order }
        val bTasks = tasksLoaded.filter { it.routineId == routineB.id }.sortedBy { it.order }

        assertEquals(2, aTasks.size)
        assertEquals(1, bTasks.size)

        assertTrue(aTasks[0].completed)  // A1 is checked
        assertTrue(aTasks[1].completed)  // A2 was already checked
        assertFalse(bTasks[0].completed) // B1 remains unchecked
    }
}
