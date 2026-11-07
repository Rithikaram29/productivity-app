package com.intentcoach.app.data

/**
 * The vocabulary of IntentCoach. Room stores each enum by its `name`, so renaming a
 * constant is a schema change — add new ones, never rename old ones.
 */

/** Step 1 of a check-in: "What did you pick up your phone to do?" */
enum class IntentCategory(val label: String) {
    SOMETHING_SPECIFIC("Something specific"),
    JUST_CHECKING("Just checking"),
    BORED("I'm bored"),
    TAKING_A_BREAK("Taking a break"),
    SOMETHING_ELSE("Something else")
}

/**
 * How a session is classified. UNKNOWN is a real answer, not a missing one: it means
 * "we don't know why", and it is never folded into UNPLANNED.
 */
enum class SessionType { INTENTIONAL, UNPLANNED, UNKNOWN }

enum class SessionStatus {
    /** The user chose Continue and is (as far as we know) still in the app. */
    ACTIVE,

    /** The app left the foreground and the session was closed normally. */
    COMPLETED,

    /** The session was cut short by a reboot / shutdown rather than by the user leaving. */
    INTERRUPTED,

    /** The prompt was closed without an answer. Not a session of intentional use. */
    DISMISSED,

    /** The user chose Go back. */
    TURNED_BACK
}

/** Step 3 of a check-in. */
enum class Decision { CONTINUE, GO_BACK }

/** "Did you accomplish what you intended?" — always optional. */
enum class Reflection(val label: String) {
    YES_EXACTLY("Yes, exactly what I came for"),
    PARTIALLY("Partially"),
    NOT_REALLY("Not really"),
    CHANGED_MIND("I changed my mind, and that's okay")
}

/**
 * Where a session's duration came from. The UI must show UNKNOWN as "not measured",
 * never as zero — IntentCoach never fabricates usage numbers.
 */
enum class DurationSource {
    /** Reconstructed from Android's usage events. */
    MEASURED,

    /** We could not measure it (no usage access, events missing or expired). */
    UNKNOWN,

    /** There was no usage to measure (turned back, dismissed). */
    NOT_APPLICABLE
}

/** Whether the check-in was triggered by detection or started by hand. */
enum class CheckInSource { AUTO, MANUAL }

/** Suggested grouping for the app picker. */
enum class AppCategory(val label: String) {
    SOCIAL("Social media"),
    ENTERTAINMENT("Entertainment"),
    COMMUNICATION("Communication"),
    BROWSING("Browsing"),
    OTHER("Other")
}

enum class FocusCategory(val label: String) {
    WORK("Work"),
    STUDY("Study"),
    READING("Reading"),
    PERSONAL_PROJECTS("Personal projects"),
    CUSTOM("Custom")
}
