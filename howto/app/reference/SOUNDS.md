# Scanium App Sounds

## Overview

Scanium provides subtle, non-disruptive UI sound cues for key user actions. Sounds are enabled by
default and can be toggled off in **Settings → Sounds → Enable sounds**.

Sounds are suppressed when:

- The toggle is OFF.
- Device ringer mode is **silent** or **vibrate**.
- Do Not Disturb is active (interruption filter not `ALL`).

## Sound Map

| Action                                                                        | Sound        |
|-------------------------------------------------------------------------------|--------------|
| Camera capture confirmed                                                      | `CAPTURE`    |
| Item added to list                                                            | `ITEM_ADDED` |
| Item selected/unselected                                                      | `SELECT`     |
| Item deleted (swipe)                                                          | `DELETE`     |
| User-triggered error (e.g., capture with no detections, export/share failure) | `ERROR`      |
| Assistant message sent                                                        | `SEND`       |
| Assistant reply received (when on screen & not typing)                        | `RECEIVED`   |
| Export/share initiated                                                        | `EXPORT`     |

## Anti-spam / Rate Limiting

Some sounds are rate-limited to prevent “machine-gun” audio during scanning or rapid taps.

- `ITEM_ADDED`: minimum 800 ms between plays.
- `SELECT`: minimum 150 ms between plays.
- `RECEIVED`: minimum 500 ms between plays.

## Adding New Cues

1. Add a new `AppSound` entry in `androidApp/src/main/java/com/scanium/app/audio/AppSound.kt`.
2. Map it to a resource in `AndroidSoundManager` (`SOUND_RESOURCES`).
3. (Optional) Add a rate limit in `DEFAULT_RATE_LIMITS`.
4. Wire the cue to a discrete, user-perceived action in the UI or ViewModel.
5. Ensure the action is not a continuous loop and won’t spam the user.

## Assets & Licensing

Sounds are generated at runtime using `ToneGenerator` (no bundled assets, no external licensing
required).
