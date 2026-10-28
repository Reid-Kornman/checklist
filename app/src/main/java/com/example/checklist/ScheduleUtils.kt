package com.example.checklist

import java.time.DayOfWeek

fun Task.isScheduledOn(dayOfWeek: DayOfWeek): Boolean = dayOfWeek.value in daysOfWeek

fun toAmPm(time24: String): String {
    val parts = time24.split(":")
    if (parts.size != 2) return time24
    val hour24 = parts[0].toIntOrNull() ?: return time24
    val minute = parts[1].toIntOrNull() ?: return time24
    val suffix = if (hour24 >= 12) "PM" else "AM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return if (minute == 0) "$hour12 $suffix" else String.format("%d:%02d %s", hour12, minute, suffix)
}
