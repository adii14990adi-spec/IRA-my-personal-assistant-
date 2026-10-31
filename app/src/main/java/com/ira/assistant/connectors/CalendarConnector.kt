package com.ira.assistant.connectors

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The user selects a real writable calendar and confirms details before insertion. */
data class WritableCalendar(val id: Long, val name: String, val account: String)

class CalendarConnector(private val context: Context) {
    private fun checkPermissions() {
        check(context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            "Calendar permissions are needed to choose a calendar and save an event."
        }
    }

    fun writableCalendars(): List<WritableCalendar> {
        checkPermissions()
        val columns = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME)
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val result = mutableListOf<WritableCalendar>()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, columns, selection, args, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                result += WritableCalendar(cursor.getLong(0), cursor.getString(1) ?: "Calendar", cursor.getString(2) ?: "")
            }
        }
        return result
    }

    fun addEvent(calendarId: Long, title: String, start: LocalDateTime): Result<Unit> = runCatching {
        checkPermissions()
        require(title.isNotBlank() && title.length <= 120) { "Enter an event title (up to 120 characters)." }
        val zone = ZoneId.systemDefault()
        val startMillis = start.atZone(zone).toInstant().toEpochMilli()
        require(startMillis >= Instant.now().toEpochMilli() - 60_000) { "Choose a future date and time." }
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title.trim())
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, startMillis + 60 * 60_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        }
        check(context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null) {
            "The calendar did not save the event."
        }
    }
}
