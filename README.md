# Niko Dialer

Tap a face to call. Niko Dialer is for people who can't easily read a screen: people who can't read, older people and people with low vision. Part of [Niko](https://nikoapps.github.io): simple, free Android apps with no ads and no tracking.

**Status:** early development. It can't make calls yet.

## What it will do

- A fixed grid of big face photos. Tap one, hear the name, and the call starts
- A family member records "Mum is calling", and it plays as that person's ringtone
- Optionally, calls from numbers not saved in the phone don't ring. They still show as missed calls
- A helper sets it up, with settings behind an optional PIN
- No internet permission, no ads, no tracking

## Building

Requires JDK 17 and the Android SDK. Shared code comes from [Niko Commons](https://github.com/NikoApps/Commons) as a git submodule, so clone with `--recursive`, or run `git submodule update --init` in an existing clone.

```sh
./gradlew assembleDebug
```

## Licence

GNU General Public License v3.0. See [LICENSE](LICENSE).
