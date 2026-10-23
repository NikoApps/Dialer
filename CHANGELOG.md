# Changelog

Internal changelog for developers, generated from commit messages with git-cliff. The user-facing notes are in `fastlane/metadata/android/en-US/changelogs/`.

## 0.1.0 - 2026-10-23

### Added

- people, grid places and settings in DataStore
- fixed photo grid and calling flow
- voice language, speech speed and theme settings
- people editor rules for places, names and numbers
- helper settings, people editor, PIN gate, health checks and setup
- photo version on people, crop square maths and photo file names
- face photos from the camera, photo picker or contact
- silence calls from numbers not saved in the phone
- only ring for saved contacts in settings, setup and health checks
- use the Dialer green accent
- keep each person's contact link and recording on the person
- save typed people in the phone's contacts and copy SIM contacts
- record caller names and set them as contact ringtones
- repair recorded names and check them in health, hint and setup
- add a screen language setting like Clock's
- translate Dialer into Arabic, Bengali, Spanish, French, Hindi, Indonesian, Nepali, Portuguese and Urdu
- speak Calling {name} in every translated language

### Fixed

- ask for the PIN again after Android stops Dialer
- show the last silenced call in the phone's own time and date format
- keep the saved setup step in range when the steps change
- place one call per tap and ask about emergency numbers off the main thread
- ask for the PIN again after a helper leaves Dialer
- keep tile names and the hint line whole at 200% font size
- list people past the grid in settings and say who a smaller grid hides
- link a person to a contact by number only when it's clear which one
- leave a ringtone set to the phone's default alone
- delete a ringtone copy that can't be published
- keep a person's old photo and recording until their save is in
- never crash on a call because the settings can't be read
- keep a Stop tapped straight after Record
- open photos no bigger than the working size
- delete unsaved photos and recordings when leaving the people editor
- check typed numbers for emergency numbers off the main thread
- keep the title, hint and status bar readable at 200% font and in a chosen theme
- keep a new photo and recording when saving a person

### Build

- move commons to the PIN, health and setup move
- move commons to the app accent
- move Dialer's data to a new phone, never to cloud backup
- exclude everything from backup on Android 8 to 11 as well
- move commons to the title bar and PIN state fixes
- move commons to the merged accent, title bar and PIN state fixes

### CI

- add the release workflow from Clock

### Chores

- Niko Dialer skeleton with Commons as a submodule
- add a script that sets up test phones

### Tests

- grid places, call flow and emergency numbers

