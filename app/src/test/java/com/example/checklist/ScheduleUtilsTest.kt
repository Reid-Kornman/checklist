package com.example.checklist

import org.junit.Assert.assertEquals
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
}
