# FakeRansom (Educational Simulation)

Demonstration app used in security-awareness workshops to show what a mobile
ransomware encounter **feels** like on an Android device. It stages the full
pressure experience — lock screen takeover, alarm siren, terminal-style
"encryption" log, ransom note with countdown — while performing **zero harm**:

- **No files are encrypted, modified, or deleted.** Real names may be listed in
  the terminal log, but nothing is written anywhere.
- **No network access.** The app has no INTERNET permission.
- The Bitcoin address, support e-mail, and `.demo` domain in the ransom note are
  placeholders. Nothing is collected or transmitted.

## What it demonstrates

Techniques real mobile ransomware uses, staged safely with standard UI APIs:

| Technique | How it is simulated |
|---|---|
| Device takeover at boot | `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` receiver posts a full-screen-intent notification that pulls the lock screen up (`showWhenLocked`, `turnScreenOn`) |
| Lock screen | Immersive sticky fullscreen, status bar and navigation hidden, back button blocked, `REORDER_TASKS` + `moveTaskToFront` pulls the app back over the home screen when the user gets out |
| Pressure | Looping siren played on the alarm audio channel (`AudioAttributes.USAGE_ALARM`), repeating waveform vibration, screen kept on |
| Encryption theatre | Terminal-style log enumerates real photos/downloads (via MediaStore, read-only) and contacts (read-only), showing them with a fake `.locked` suffix, fake session key, fake victim ID from `ANDROID_ID` |
| Extortion UI | Ransom note with countdown timer and on-chain payment instructions (all fake) |

## Escape hatch (presenter)

Tap the **yellow countdown clock 5 times within 3 seconds**. This exits the
simulation instantly and releases the siren and vibration. It is deliberately
ungreasy so it does not break the illusion live, but is documented here for the
presenter only.

If the app is ever stuck: `adb shell am force-stop com.china.fakeransom`.

## Permissions requested and why

| Permission | Purpose |
|---|---|
| `VIBRATE` | Alarm vibration |
| `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS` | Boot-time lock-screen takeover demo |
| `REORDER_TASKS` | Pull the demo back to the foreground when the user escapes to the home screen |
| `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` | List real photo/download **names** for the encryption log (read-only) |
| `READ_CONTACTS` | Show real contact names in the log (read-only) |
| `SYSTEM_ALERT_WINDOW` | Declared for completeness in lessons on overlay attacks; not actively used |

## Build

```
./gradlew assembleDebug
```

The GitHub Actions workflow uploads the APK as the `FakeRansom-APK` artifact.

**Never open this app on a device you do not own or are not authorised to demo
on** — to the victim unaware of the escape hatch, the experience is
indistinguishable from the real thing in the moment.
