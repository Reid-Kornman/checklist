package com.example.checklist

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleUtilsTest {
    @Test
    fun toAmPm_convertsMidnightAndNoonCorrectly() {
        assertEquals("12 AM", toAmPm("00:00"))
        assertEquals("12 PM", toAmPm("12:00"))
    }

    @Test
    fun toAmPm_formatsAfternoonAndMinutesCorrectly() {
        assertEquals("3:05 PM", toAmPm("15:05"))
        assertEquals("9:30 AM", toAmPm("09:30"))
    }

    @Test
    fun isScheduledOn_matchesOnlySelectedWeekdays() {
        val task = task(daysOfWeek = setOf(DayOfWeek.MONDAY.value, DayOfWeek.WEDNESDAY.value))

        assertTrue(task.isScheduledOn(DayOfWeek.MONDAY))
        assertTrue(task.isScheduledOn(DayOfWeek.WEDNESDAY))
        assertFalse(task.isScheduledOn(DayOfWeek.TUESDAY))
        assertFalse(task.isScheduledOn(DayOfWeek.SUNDAY))
    }

    @Test
    fun isScheduledOn_includesEveryDayForDefaultSchedule() {
        val task = task(daysOfWeek = ALL_DAYS_OF_WEEK)

        DayOfWeek.values().forEach { day ->
            assertTrue(task.isScheduledOn(day))
        }
    }

    private fun task(daysOfWeek: Set<Int>) = Task(
        id = "task-id",
        name = "Task",
        order = 0,
        startTime = "09:00",
        endTime = "10:00",
        routineId = "routine-id",
        daysOfWeek = daysOfWeek
    )
}
