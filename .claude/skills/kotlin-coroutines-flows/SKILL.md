---
name: kotlin-coroutines-flows
description: Kotlin coroutines and Flow patterns for Niko Android apps — structured concurrency, StateFlow for UI state, cancellation, dispatchers and coroutine testing. Use when writing or reviewing coroutine or Flow code, or debugging cancellation and concurrency.
---

<!-- Adapted from ECC (https://github.com/affaan-m/ECC), MIT licence, Copyright (c) 2026 Affaan Mustafa.
     Trimmed for Niko: Android only, no network, no dependency injection framework. -->

# Kotlin coroutines and Flow

## When to use

- Writing async code: speaking, ticking clocks, reading settings, scheduling
- Exposing UI state with StateFlow
- Handling cancellation and cleanup
- Testing coroutines and Flows

## Structured concurrency

Every coroutine belongs to a scope that ends with its owner. Never use `GlobalScope`.

```kotlin
// BAD
GlobalScope.launch { loadSettings() }

// GOOD: ends with the ViewModel
viewModelScope.launch { loadSettings() }

// GOOD: ends when the composable leaves the screen
LaunchedEffect(key) { loadSettings() }
```

Work that must outlive the screen (hourly chimes, shake listening) doesn't belong in a coroutine scope at all. It goes in AlarmManager, a BroadcastReceiver or a foreground service. See `../Hub/docs/06-engineering/05-background-reliability.md`.

Parallel work uses `coroutineScope` + `async`. Use `supervisorScope` when one child failing shouldn't cancel the others.

```kotlin
suspend fun loadScreen(): ScreenData = coroutineScope {
    val settings = async { settingsStore.read() }
    val voice = async { voices.pickFor(Locale.getDefault()) }
    ScreenData(settings.await(), voice.await())
}
```

## StateFlow for UI state

One immutable state object per screen, exposed as `StateFlow`, collected with `collectAsStateWithLifecycle()`.

```kotlin
class ClockViewModel(settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<ClockState> = settings.observe()
        .map { ClockState(quietHours = it.quietHours) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClockState())
}
```

`WhileSubscribed(5_000)` keeps the upstream alive for 5 seconds after the screen goes away, so a rotation doesn't restart it.

Combine sources with `combine(...)` then `stateIn`. Update state with `_state.update { it.copy(...) }`, never by mutating a list inside it.

## One-time events

Use a `SharedFlow` (or a `Channel`) for things that happen once, such as "speak now" or "open the battery settings". Don't put them in state, where they replay after rotation.

Niko doesn't use snackbars or toasts for anything important (`../Hub/docs/04-design-system/04-components.md`), so events drive speech or navigation, not messages that vanish.

## Dispatchers

```kotlin
withContext(Dispatchers.Default) { /* CPU work: phrase building, calendar conversion */ }
withContext(Dispatchers.IO) { /* disk: DataStore, audio files */ }
// viewModelScope and LaunchedEffect already run on Main
```

## Cancellation

Long loops check for cancellation with `ensureActive()`. Cleanup goes in `finally`, which runs even when cancelled.

```kotlin
viewModelScope.launch {
    try {
        speaker.speak(phrase)
    } finally {
        _state.update { it.copy(speaking = false) }
    }
}
```

Never catch `CancellationException` and swallow it. If you catch `Exception`, rethrow cancellation.

## Testing

Use `kotlinx-coroutines-test` with `runTest`. Inject a clock and fakes instead of using real time or the real TTS engine.

```kotlin
@Test
fun `quiet hours stop the chime`() = runTest {
    val viewModel = ClockViewModel(FakeSettings(quietHours = 22..7))
    advanceUntilIdle()
    assertTrue(viewModel.state.value.quietHours.contains(23))
}
```

Fakes are plain classes backed by `MutableStateFlow`:

```kotlin
class FakeSettings(quietHours: IntRange) : SettingsRepository {
    private val flow = MutableStateFlow(Settings(quietHours))
    override fun observe(): Flow<Settings> = flow
    fun set(settings: Settings) { flow.value = settings }
}
```

Add Turbine only if testing a sequence of emissions gets awkward without it.

## Avoid

- `GlobalScope`: leaks work with no cancellation
- Mutable collections inside `MutableStateFlow`
- Catching `CancellationException`
- `flowOn(Dispatchers.Main)` to change where collection runs; collection runs on the caller's dispatcher
- Building a `Flow` inside a composable without `remember`; it's rebuilt on every recomposition
- Using coroutines for work that must survive the app being closed
