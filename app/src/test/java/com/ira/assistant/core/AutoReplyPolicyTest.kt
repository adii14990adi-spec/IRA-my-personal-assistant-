package com.ira.assistant.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReplyPolicyTest {
    @Test fun onlyExplicitExactAllowlistMatches() {
        assertFalse(AutoReplyPolicy.matches(false, "Busy", "Mum", "Mum"))
        assertFalse(AutoReplyPolicy.matches(true, "  ", "Mum", "Mum"))
        assertFalse(AutoReplyPolicy.matches(true, "Busy", "Mum", "Mum's group"))
        assertFalse(AutoReplyPolicy.matches(true, "Busy", "Mum\nAditi", ""))
        assertTrue(AutoReplyPolicy.matches(true, "Busy", "Mum\nAditi", "  aditi  "))
    }

    @Test fun throttlesDuringCooldownAndIfClockMovesBackwards() {
        assertFalse(AutoReplyPolicy.throttled(0, 1000))
        assertTrue(AutoReplyPolicy.throttled(1000, 1001))
        assertTrue(AutoReplyPolicy.throttled(1000, 999))
        assertFalse(AutoReplyPolicy.throttled(1000, 1000 + AutoReplyPolicy.COOLDOWN_MILLIS))
    }
}
