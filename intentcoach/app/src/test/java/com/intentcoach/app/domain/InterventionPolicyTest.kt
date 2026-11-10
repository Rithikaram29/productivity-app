package com.intentcoach.app.domain

import com.intentcoach.app.data.IntentCategory
import com.intentcoach.app.data.SessionType
import org.junit.Assert.assertEquals
import org.junit.Test

class InterventionPolicyTest {

    private val now = 1_700_000_000_000L
    private val min = 60_000L

    private fun ctx(
        enabled: Boolean = true,
        auto: Boolean = true,
        monitored: Boolean = true,
        showing: Boolean = false,
        active: Boolean = false,
        last: Long? = null,
        cooldown: Long = 5 * min
    ) = PromptContext(enabled, auto, monitored, showing, active, last, cooldown, now)

    @Test
    fun `asks for a monitored app with nothing going on`() {
        assertEquals(PromptDecision.PROMPT, InterventionPolicy.decide(ctx()))
    }

    @Test
    fun `never asks when switched off or automatic check-ins are off`() {
        assertEquals(PromptDecision.SKIP_DISABLED, InterventionPolicy.decide(ctx(enabled = false)))
        assertEquals(PromptDecision.SKIP_DISABLED, InterventionPolicy.decide(ctx(auto = false)))
    }

    @Test
    fun `never asks for an app the user did not choose`() {
        assertEquals(PromptDecision.SKIP_NOT_MONITORED, InterventionPolicy.decide(ctx(monitored = false)))
    }

    @Test
    fun `no duplicate prompt while one is showing`() {
        assertEquals(PromptDecision.SKIP_PROMPT_SHOWING, InterventionPolicy.decide(ctx(showing = true)))
    }

    @Test
    fun `does not interrupt an active session`() {
        assertEquals(PromptDecision.SKIP_ACTIVE_SESSION, InterventionPolicy.decide(ctx(active = true)))
    }

    @Test
    fun `respects the cooldown, then asks again`() {
        assertEquals(PromptDecision.SKIP_COOLDOWN, InterventionPolicy.decide(ctx(last = now - 4 * min)))
        assertEquals(PromptDecision.PROMPT, InterventionPolicy.decide(ctx(last = now - 5 * min)))
    }

    @Test
    fun `zero cooldown means ask every time`() {
        assertEquals(PromptDecision.PROMPT, InterventionPolicy.decide(ctx(last = now - 1, cooldown = 0)))
    }

    @Test
    fun `a clock that went backwards does not block prompts forever`() {
        // lastActivityAt in the future (user changed the clock): not "within" the cooldown.
        assertEquals(PromptDecision.PROMPT, InterventionPolicy.decide(ctx(last = now + 10 * min)))
    }

    // --- "time's up" popup ---

    private fun timeUp(
        planned: Int? = 10,
        extensions: Int = 0,
        fgMin: Long? = 10,
        showing: Boolean = false,
        snoozedUntil: Long = 0,
        enabled: Boolean = true
    ) = TimeUpPolicy.shouldAsk(enabled, planned, extensions, fgMin?.let { it * min }, showing, snoozedUntil, now)

    @Test
    fun `asks once the planned time has been on screen`() {
        assertEquals(false, timeUp(fgMin = 9))
        assertEquals(true, timeUp(fgMin = 10))
    }

    @Test
    fun `each keep-going earns another block of the planned length`() {
        assertEquals(false, timeUp(extensions = 1, fgMin = 15))
        assertEquals(true, timeUp(extensions = 1, fgMin = 20))
        assertEquals(30 * min, TimeUpPolicy.thresholdMs(10, 2))
    }

    @Test
    fun `never asks for no-limit sessions or unmeasured time`() {
        assertEquals(false, timeUp(planned = null, fgMin = 500))
        assertEquals(false, timeUp(fgMin = null))
    }

    @Test
    fun `respects snooze, an open prompt, and the off switch`() {
        assertEquals(false, timeUp(snoozedUntil = now + 1))
        assertEquals(true, timeUp(snoozedUntil = now))
        assertEquals(false, timeUp(showing = true))
        assertEquals(false, timeUp(enabled = false))
    }

    @Test
    fun `classification uses only the user's own words`() {
        assertEquals(SessionType.INTENTIONAL, Classification.classify(IntentCategory.SOMETHING_SPECIFIC))
        assertEquals(SessionType.INTENTIONAL, Classification.classify(IntentCategory.TAKING_A_BREAK))
        assertEquals(SessionType.INTENTIONAL, Classification.classify(IntentCategory.SOMETHING_ELSE))
        assertEquals(SessionType.UNPLANNED, Classification.classify(IntentCategory.JUST_CHECKING))
        assertEquals(SessionType.UNPLANNED, Classification.classify(IntentCategory.BORED))
    }
}
