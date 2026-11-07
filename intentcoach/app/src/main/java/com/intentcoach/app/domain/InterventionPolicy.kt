package com.intentcoach.app.domain

import com.intentcoach.app.data.IntentCategory
import com.intentcoach.app.data.SessionType

/**
 * Whether a monitored app coming to the front should get a check-in.
 *
 * Pure: the service gathers the facts, this decides. Keeping the rules here (not in the
 * service) means duplicate prompts, cooldowns, and "don't interrupt an active session"
 * are all unit-tested without a phone.
 */
data class PromptContext(
    val enabled: Boolean,
    val autoIntervention: Boolean,
    val isMonitored: Boolean,
    val promptShowing: Boolean,
    val hasActiveSession: Boolean,
    /** When this app last had a session end, or a prompt answered/dismissed. */
    val lastActivityAt: Long?,
    val cooldownMs: Long,
    val now: Long
)

enum class PromptDecision {
    PROMPT,
    SKIP_DISABLED,
    SKIP_NOT_MONITORED,
    SKIP_PROMPT_SHOWING,
    SKIP_ACTIVE_SESSION,
    SKIP_COOLDOWN
}

object InterventionPolicy {
    fun decide(ctx: PromptContext): PromptDecision = when {
        !ctx.enabled || !ctx.autoIntervention -> PromptDecision.SKIP_DISABLED
        !ctx.isMonitored -> PromptDecision.SKIP_NOT_MONITORED
        // One prompt at a time: a second detection while the first is on screen is a duplicate.
        ctx.promptShowing -> PromptDecision.SKIP_PROMPT_SHOWING
        // Coming back to an app mid-session is part of that session, not a new decision.
        ctx.hasActiveSession -> PromptDecision.SKIP_ACTIVE_SESSION
        ctx.lastActivityAt != null && ctx.cooldownMs > 0 &&
            ctx.now - ctx.lastActivityAt in 0 until ctx.cooldownMs -> PromptDecision.SKIP_COOLDOWN
        else -> PromptDecision.PROMPT
    }
}

/**
 * Intent → classification. A user who names any purpose — including "Taking a break" —
 * is using the app intentionally. "Just checking" and "I'm bored" are their own honest
 * words for unplanned use. Nothing the user did not say is ever classified as unplanned.
 */
object Classification {
    fun classify(intent: IntentCategory): SessionType = when (intent) {
        IntentCategory.JUST_CHECKING, IntentCategory.BORED -> SessionType.UNPLANNED
        IntentCategory.SOMETHING_SPECIFIC,
        IntentCategory.TAKING_A_BREAK,
        IntentCategory.SOMETHING_ELSE -> SessionType.INTENTIONAL
    }
}
