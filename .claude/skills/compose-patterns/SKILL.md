---
name: compose-patterns
description: Jetpack Compose patterns for Niko Android apps — screen state, stateless composables, Modifier order, recomposition performance and theming within the Niko design system. Use when building or reviewing Compose UI, state or navigation.
---

<!-- Adapted from ECC compose-multiplatform-patterns (https://github.com/affaan-m/ECC), MIT licence,
     Copyright (c) 2026 Affaan Mustafa. Trimmed for Niko: Android only, no KMP, no Koin,
     no dynamic colour, no snackbars or bottom sheets. -->

# Compose patterns

The Niko design system wins over anything here. Read `../Hub/docs/04-design-system/` before building a screen: one screen one job, fixed positions, no scrolling on the main screen, 64dp touch targets (96dp for primary actions), AAA contrast.

## Screen state

One immutable data class per screen, held in a ViewModel as `StateFlow`.

```kotlin
data class ClockState(
    val time: LocalTime = LocalTime.MIN,
    val voiceMissing: Boolean = false,
    val lastSpokeAt: LocalTime? = null,
)

class ClockViewModel(...) : ViewModel() {
    private val _state = MutableStateFlow(ClockState())
    val state: StateFlow<ClockState> = _state.asStateFlow()

    fun onSpeak() { /* ... */ }
}
```

## Stateful wrapper, stateless content

The screen composable collects state and passes it down. The content composable takes plain values and lambdas, so it previews and tests without a ViewModel.

```kotlin
@Composable
fun ClockScreen(viewModel: ClockViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ClockContent(state = state, onSpeak = viewModel::onSpeak)
}

@Composable
private fun ClockContent(state: ClockState, onSpeak: () -> Unit) { /* ... */ }
```

Construct ViewModels with `viewModel { ClockViewModel(...) }` or a small factory. No dependency injection framework.

When a screen has many callbacks, pass one `onEvent: (Event) -> Unit` with a sealed interface instead of a long list of lambdas.

## Navigation

Niko apps have very few screens: the main screen, the helper PIN, and helper settings. Pass lambdas such as `onOpenSettings` down instead of passing a `NavController` into composables. Add Navigation Compose only when there are enough screens to need it.

Confirmations use the `ConfirmSheet` pattern from the design system: a plain question and two big buttons with verbs. No bottom sheets for core tasks, no dialogs that need reading to escape.

## Building composables

Shared building blocks (BigButton, BigDisplay, SettingRow, PinPad) live in `commons/ui`. Build them once, following `../Hub/docs/04-design-system/04-components.md`, and reuse them. Use slot parameters (`content: @Composable () -> Unit`) where the caller supplies content.

Every composable that takes a `modifier` applies it to its outermost element and defaults it to `Modifier`.

### Modifier order

Order changes the result. Usual sequence:

```kotlin
Modifier
    .padding(16.dp)                   // 1. layout: size, padding
    .clip(RoundedCornerShape(8.dp))   // 2. shape
    .background(color)                // 3. drawing
    .clickable(onClickLabel = label) { } // 4. interaction
```

Padding before `clickable` shrinks the touch area. Check that the touch target is still 64dp or more.

## Accessibility in Compose

- Every control has a spoken label. Text buttons get it for free; anything else uses `Modifier.semantics { contentDescription = ... }`.
- Combine related text into one TalkBack stop with `Modifier.semantics(mergeDescendants = true) {}`.
- Mark the big time as a heading so TalkBack users can jump to it.
- Use `sp` for text so it follows the phone's font size, and check at 200%.
- Never rely on colour alone.

## Performance

- Mark UI models `@Immutable` when all their properties are immutable, so Compose can skip recomposing them.
- Use `remember` for anything computed from inputs, and `derivedStateOf` when a value changes less often than what it reads.
- Give list items stable keys: `items(list, key = { it.id })`.
- Don't create new objects or lambdas in composable parameters when they can be remembered.

## Theming

Use `NikoTheme` from `commons/theme`. It has no dynamic colour on purpose: wallpaper-based palettes can break the 7:1 contrast rule. Never call `dynamicLightColorScheme` or `dynamicDarkColorScheme`, and never hard-code colours in screens; read them from `MaterialTheme.colorScheme`.

## Avoid

- `mutableStateOf` in ViewModels; use `MutableStateFlow` and `collectAsStateWithLifecycle`
- Passing `NavController` deep into composables
- Heavy work inside composables; move it to the ViewModel or `remember`
- `LaunchedEffect(Unit)` as a stand-in for ViewModel initialisation
- Toasts and snackbars for anything important, icon-only buttons, swipe-only or long-press-only actions
- Anything that moves, appears or disappears on the main screen based on state
