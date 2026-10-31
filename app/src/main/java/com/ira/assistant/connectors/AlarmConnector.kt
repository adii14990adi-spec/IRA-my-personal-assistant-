package com.ira.assistant.connectors

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

class AlarmConnector(private val context: Context) {
    /** Opens the clock for confirmation; Android does not report whether the user saved it. */
    fun open(hour: Int, minute: Int): Result<Unit> = runCatching {
        require(hour in 0..23 && minute in 0..59) { "Enter a valid time." }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "IRA alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (failure: ActivityNotFoundException) {
            throw IllegalStateException("No compatible clock app was found.", failure)
        } catch (failure: SecurityException) {
            throw IllegalStateException("The clock app did not allow this alarm.", failure)
        }
    }
}
