# Niko Dialer

A photo dialer for people who can't easily read a screen: tap a face to call. Part of Niko: simple, free, offline Android apps with no ads and no tracking.

## Where the context lives

The private Hub repo at `../Hub` (relative to this repo) is the source of truth. Read the relevant doc before changing behaviour, and don't repeat its reasoning here.

- Dialer spec: `../Hub/docs/02-product/apps/02-dialer/01-spec.md`
- Scope decision: `../Hub/docs/07-decisions/0026-dialer-scope.md`
- Design system (colour, type, layout, components, speech): `../Hub/docs/04-design-system/`
- Words on screen and spoken phrases: `../Hub/docs/03-brand/04-voice-and-tone.md`
- Stack and text-to-speech: `../Hub/docs/06-engineering/01-platform-and-stack.md`
- Permissions: `../Hub/docs/06-engineering/04-privacy-and-permissions.md`
- Release, signing, versioning: `../Hub/docs/06-engineering/03-release-and-distribution.md`
- Decisions: `../Hub/docs/07-decisions/`. Anything that constrains future work gets a new ADR there.

If code and the Hub disagree, flag it instead of guessing which is right.

## Stack

- Kotlin, Jetpack Compose, Material 3 as the base layer with Niko's own components on top. No XML views.
- One activity. ViewModel + StateFlow for screen state. DataStore for settings and people. No dependency injection framework.
- Dependencies: AndroidX, Kotlin libraries and small GPLv3-compatible open source libraries only, pinned in `gradle/libs.versions.toml`. Never Google Play Services, Firebase, Play libraries, analytics or crash reporting. CI fails on them.
- Shared code (theme, speech, components, PIN pad) lives in the public `Commons` repo, included as a git submodule at `commons/` and built as the `:commons` module (ADR 0032). Change it in Commons by pull request, then move the submodule pointer here in its own commit. Keep Dialer-specific logic out of it. Clock uses the same Commons, so a Commons change must keep Clock building.

Project skills in `.claude/skills/`: `compose-patterns` and `kotlin-coroutines-flows`.

## Hard rules

- No new permissions without asking. Never the internet permission. The spec lists the permissions Dialer may use; add each only with the feature that needs it.
- Calls are silenced, never rejected or hidden from the call log.
- Every control has a spoken label. Touch targets at least 64dp, face tiles and primary actions at least 96dp. Text in `sp`, usable at 200% font size. Contrast 7:1 in light and dark.
- The main screen never scrolls, and faces never move unless the helper moves them.
- Speech gets words, never digits. Each language has a hand-written phrase template.
- No toasts or snackbars for anything important, no icon-only buttons, no swipe-only or long-press-only actions.
- All user-facing text goes in `strings.xml`. Plain everyday words, no exclamation marks, never blame the user.
- Builds must stay reproducible: no timestamps, git hashes or dates in the build, no `+` dependency versions.

## Commands

```sh
./gradlew assembleDebug assembleRelease testDebugUnitTest lint
uvx git-cliff -o CHANGELOG.md   # regenerate the internal changelog
scripts/release.sh 0.2.0        # bump version, changelog, commit and tag
```

## Git and releases

- Conventional commits (`feat:`, `fix:`, `docs:`, `chore:`, `build:`, `ci:`, `test:`, `refactor:`). They generate `CHANGELOG.md`.
- Work on a branch and merge by pull request with rebase. Work is tracked in GitHub Issues.
- No AI attribution in commits, pull requests or changelogs.
- Version lives in `gradle.properties` (`VERSION_NAME`, `VERSION_CODE`). Tags are the bare version name, no `v`.
- User-facing release notes are written by hand in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: plain words, 500 bytes max.
- The repo is private until the founder decides to open it (ADR 0018). Never change its visibility without that instruction.
- Releasing: write `changelogs/<next versionCode>.txt`, run `scripts/release.sh <version>` on `main`, then push `main` and the tag. The Release workflow checks the build is reproducible, signs it and publishes the GitHub release with one APK, `niko-dialer.apk` (ADR 0029).
- The release key lives outside the repo. Never print, copy or commit it or its password.
- The package ID `io.github.nikoapps.dialer` must never change once released.
- Never commit secrets, signing keys or private Hub content.
