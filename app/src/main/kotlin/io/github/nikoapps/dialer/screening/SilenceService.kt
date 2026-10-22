package io.github.nikoapps.dialer.screening

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import io.github.nikoapps.dialer.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * Silences calls from numbers that aren't saved in the phone, while "Only ring for saved
 * contacts" is on. Android binds this only while Dialer holds the call screening role, and only
 * for numbers not in the phone's contacts, so Dialer never sees family numbers and never reads
 * contacts here.
 *
 * Calls are silenced, never rejected: they still ring through to the call log and show as
 * missed calls, so a clinic's call isn't lost.
 */
class SilenceService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val direction = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> CallDirection.Unknown
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING -> CallDirection.Incoming
            callDetails.callDirection == Call.Details.DIRECTION_OUTGOING -> CallDirection.Outgoing
            else -> CallDirection.Unknown
        }

        // Android waits only a few seconds for an answer while the phone is already ringing.
        // Reading one setting from DataStore takes milliseconds, so this blocks briefly instead
        // of answering from another thread, which keeps the service bound until the answer and
        // the log write are done. If the read is slow for any reason, the call rings as normal.
        val onlySavedContacts = runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(READ_TIMEOUT_MS) { SettingsRepository(this@SilenceService).current().onlySavedContacts }
        } ?: false

        val screening = screeningFor(Build.VERSION.SDK_INT, onlySavedContacts, direction)
        respondToCall(callDetails, responseFor(screening))

        if (screening == Screening.Silence) {
            // After answering, so a slow write never delays the decision. Only the time is kept.
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(WRITE_TIMEOUT_MS) { SilenceLog(this@SilenceService).record(Instant.now()) }
            }
        }
    }

    private fun responseFor(screening: Screening): CallResponse {
        val builder = CallResponse.Builder()
            // Never block, reject or hide a call: it must stay in the call log and as a missed call.
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
        if (screening == Screening.Silence && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setSilenceCall(true)
        }
        return builder.build()
    }

    private companion object {
        const val READ_TIMEOUT_MS = 1_500L
        const val WRITE_TIMEOUT_MS = 1_000L
    }
}
