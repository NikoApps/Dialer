package io.github.nikoapps.dialer.call

/** How a number gets called. Decided without Android, so it's tested on the JVM. */
sealed interface CallAction {
    /** A normal number: the call goes out straight away. Needs the call permission. */
    data class Call(val number: String) : CallAction

    /**
     * An emergency number: Android doesn't let other apps call these directly, so the phone's
     * own dialer opens with the number ready. Needs no permission.
     */
    data class Dial(val number: String) : CallAction

    /** Nothing to call, such as a blank number. */
    data object Nothing : CallAction
}

/** Decides how to call [number]. [isEmergency] is only asked when there is a number to call. */
fun callActionFor(number: String, isEmergency: (String) -> Boolean): CallAction {
    val trimmed = number.trim()
    if (trimmed.none(Char::isDigit)) return CallAction.Nothing
    return if (isEmergency(trimmed)) CallAction.Dial(trimmed) else CallAction.Call(trimmed)
}

/**
 * Common emergency numbers, used only when the phone can't answer, such as a phone without
 * a SIM service running. Opening the dialer for a number that isn't one does no harm.
 */
private val FallbackEmergencyNumbers = setOf("112", "911", "999", "000", "100", "101", "102", "108", "110", "119")

/** [systemAnswer] is the phone's own answer, or null when it couldn't give one. */
fun isEmergencyNumber(number: String, systemAnswer: Boolean?): Boolean =
    systemAnswer ?: (number.filter(Char::isDigit) in FallbackEmergencyNumbers)
