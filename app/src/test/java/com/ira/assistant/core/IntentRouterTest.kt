package com.ira.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentRouterTest {
    private val router = IntentRouter(listOf(
        IntentDefinition("ACTION_ALARM", listOf("alarm", "uthana")),
        IntentDefinition("ACTION_BATTERY", listOf("battery")),
        IntentDefinition("ACTION_WHATSAPP", listOf("whatsapp")),
        IntentDefinition("ACTION_CALENDAR", listOf("calendar", "add event")),
        IntentDefinition("ACTION_REMINDER", listOf("remind me")),
        IntentDefinition("ACTION_CALLER_ID", listOf("who is calling"))
    ))

    @Test fun directActionsBypassChat() {
        assertEquals(IraIntent.Battery, router.route("Hey IRA, battery status?"))
        assertEquals(IraIntent.WhatsApp, router.route("Read WhatsApp"))
        assertEquals(IraIntent.Caller, router.route("who is calling?"))
        assertEquals(IraIntent.Chat, router.route("How was your day?"))
        assertEquals(IraIntent.Chat, router.route("batteryless devices"))
    }

    @Test fun reminderWinsOverBattery() {
        assertEquals(IraIntent.Calendar("charge my battery"), router.route("remind me to charge my battery"))
    }

    @Test fun alarmUsesSafeTimeParsing() {
        assertEquals(IraIntent.Alarm(6, 30), router.route("set alarm 6:30 am"))
        assertEquals(IraIntent.Alarm(18, 30), router.route("alarm 6:30 pm"))
        assertEquals(IraIntent.Alarm(0, 0), router.route("alarm 12 am"))
        assertEquals(IraIntent.Alarm(12, 0), router.route("alarm 12 pm"))
        assertEquals(IraIntent.Alarm(19, 5), router.route("alarm 19:05"))
        assertEquals(IraIntent.Alarm(null, null), router.route("alarm 99:99"))
        assertEquals(IraIntent.Alarm(null, null), router.route("alarm at 7 baje"))
        assertEquals(IraIntent.DatedAlarm, router.route("kal subah 6:30 baje ka alarm"))
        assertEquals(IraIntent.DatedAlarm, router.route("alarm 3/11 at 6:30"))
        assertEquals(IraIntent.DatedAlarm, router.route("सोमवार को alarm 6:30"))
        assertEquals(IraIntent.Alarm(null, null), router.route("alarm at 6:3"))
        assertEquals(IraIntent.Alarm(23, 59), router.route("alarm 23:59"))
    }
}
