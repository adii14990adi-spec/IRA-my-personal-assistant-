package com.ira.assistant.connectors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class BatteryStatus(val percentage: Int, val charging: Boolean)

class SystemConnector(private val context: Context) {
    fun batteryStatus(): BatteryStatus? {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (percent !in 0..100) return null
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val state = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL
        return BatteryStatus(percent, charging)
    }
}
