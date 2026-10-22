package io.github.nikoapps.dialer.call

import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.settings.WaitBeforeCalling
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallFlowTest {
    private val mum = Person(id = "mum", name = "Mum", number = "9800000001", place = 0)
    private val ram = Person(id = "ram", name = "Ram", number = "9800000002", place = 1)

    private class FakeSpeech : CallSpeech {
        val said = mutableListOf<String>()
        var stops = 0
        override fun sayCalling(person: Person) { said += person.name }
        override fun stop() { stops++ }
    }

    private class Setup(scope: TestScope, callStarts: Boolean = true) {
        val speech = FakeSpeech()
        val placed = mutableListOf<Person>()
        val flow = CallFlow(scope = scope, speech = speech, placeCall = { placed += it; callStarts })
    }

    @Test
    fun `call straight away speaks and calls at once`() = runTest {
        val s = Setup(this)
        s.flow.start(mum, WaitBeforeCalling.CallNow)
        assertEquals(listOf("Mum"), s.speech.said)
        assertEquals(listOf(mum), s.placed)
        assertEquals(CallState.Idle(), s.flow.state.value)
    }

    @Test
    fun `wait 3 seconds speaks, waits, then calls`() = runTest {
        val s = Setup(this)
        s.flow.start(mum, WaitBeforeCalling.Wait3s)
        assertEquals(listOf("Mum"), s.speech.said)
        assertEquals(CallState.Waiting(mum), s.flow.state.value)

        advanceTimeBy(CallFlow.WAIT_MILLIS - 1)
        runCurrent()
        assertTrue(s.placed.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(mum), s.placed)
        assertEquals(CallState.Idle(), s.flow.state.value)
    }

    @Test
    fun `stop during the wait cancels the call for sure and stops the speech`() = runTest {
        val s = Setup(this)
        s.flow.start(mum, WaitBeforeCalling.Wait3s)
        advanceTimeBy(CallFlow.WAIT_MILLIS - 1)
        runCurrent()
        s.flow.stop()
        advanceUntilIdle()
        assertTrue(s.placed.isEmpty())
        assertEquals(1, s.speech.stops)
        assertEquals(CallState.Idle(), s.flow.state.value)
    }

    @Test
    fun `stop then a new tap only calls the new person, once`() = runTest {
        val s = Setup(this)
        s.flow.start(mum, WaitBeforeCalling.Wait3s)
        advanceTimeBy(2_000)
        s.flow.stop()
        s.flow.start(ram, WaitBeforeCalling.Wait3s)
        advanceUntilIdle()
        assertEquals(listOf(ram), s.placed)
    }

    @Test
    fun `a second tap while a call is waiting is ignored`() = runTest {
        val s = Setup(this)
        s.flow.start(mum, WaitBeforeCalling.Wait3s)
        s.flow.start(ram, WaitBeforeCalling.Wait3s)
        advanceUntilIdle()
        assertEquals(listOf("Mum"), s.speech.said)
        assertEquals(listOf(mum), s.placed)
    }

    @Test
    fun `ask first waits for the Call button, however long it takes`() = runTest {
        val s = Setup(this)
        s.flow.start(mum, WaitBeforeCalling.AskFirst)
        assertEquals(CallState.Asking(mum), s.flow.state.value)
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(s.placed.isEmpty())

        s.flow.confirm()
        assertEquals(listOf(mum), s.placed)
        assertEquals(CallState.Idle(), s.flow.state.value)
    }

    @Test
    fun `ask first then stop never calls`() = runTest {
        val s = Setup(this)
        s.flow.start(mum, WaitBeforeCalling.AskFirst)
        s.flow.stop()
        s.flow.confirm()
        advanceUntilIdle()
        assertTrue(s.placed.isEmpty())
    }

    @Test
    fun `leaving the screen cancels a waiting call`() = runTest {
        val s = Setup(this)
        s.flow.start(mum, WaitBeforeCalling.Wait3s)
        s.flow.onLeft()
        advanceUntilIdle()
        assertTrue(s.placed.isEmpty())
        assertEquals(CallState.Idle(), s.flow.state.value)
    }

    @Test
    fun `leaving after the call went out doesn't cut anything off`() = runTest {
        val s = Setup(this)
        s.flow.start(mum, WaitBeforeCalling.CallNow)
        s.flow.onLeft()
        assertEquals(0, s.speech.stops)
    }

    @Test
    fun `a call that couldn't start is remembered for the hint line, until the next tap`() = runTest {
        val s = Setup(this, callStarts = false)
        s.flow.start(mum, WaitBeforeCalling.CallNow)
        assertEquals(CallState.Idle(lastCallFailed = true), s.flow.state.value)
        s.flow.start(mum, WaitBeforeCalling.Wait3s)
        assertEquals(CallState.Waiting(mum), s.flow.state.value)
    }
}
