# FakeRansom (Educational Simulation)

Full-intensity ransomware Encounter Simulator for security-awareness workshops.
Runs the complete psychological pressure arc a mobile ransomware victim feels —
takeover, lockdown, "encryption", extortion, fake reflash — while performing
**zero harm**:

- No files are encrypted, modified, or deleted, ever.
- No network access at all — the app has no INTERNET permission. "Uploads" in
  the terminal log are theatre lines.
- The Bitcoin address, support e-mail and `.demo` domain are placeholders.
- Camera is used read-only as a ghost preview behind the UI. No frame is
  captured, stored, or transmitted.

## The experience (what a participant feels)

1. **Silent persistence** — after any app update or reboot, a high-priority
   notification with a full-screen intent pulls the lock screen up as
   "System update required".
2. **Lockdown** — immersive fullscreen (no status bar, no navigation), back
   button mocked, screenshots and the recents thumbnail blacked out
   (`FLAG_SECURE`), screen kept on at max brightness, the app pulls itself back
   in front of the home screen ~0.7 s after the user escapes, and once device
   administration is granted (demanded by the ransom note ~8 s in) the screen
   locks with `lockNow()` and the activity reappears **above the keyguard**.
3. **Sensory pressure** — square-wave klaxon siren synthesized live and looped
   on the alarm channel, alarm + media volumes forced to max every 3 s,
   repeating shock-pattern vibration, red/blue police-style border strobe.
4. **Encryption theatre** — a green-on-black terminal enumerates the device's
   real photo names and Downloads (read-only via MediaStore/filesystem) plus
   real contact names, "encrypts" them with fake AES-256 + RSA-4096 labels,
   logs keylogger/camera/mic capture lines, then displays a victim ID derived
   from `ANDROID_ID` and a session key.
5. **The note** — 48-hour deletion countdown, payment instructions (all fake),
   "we have your face", tap-anywhere "RESTORE ACCESS" that leads into the
   device-admin dialog. Declining it: "ACCESS DENIED BY ADMINISTRATOR".
6. **Fake firmware reflash** — ~2 minutes in, the screen goes black and types
   `wiping /data ... ok`, then "restores" straight back with
   "RESTORE FAILED — ATTEMPT 2/3" and the siren resumes. Up to 3 attempts.

## Kill switch (presenter — memorize one of these)

Any of these instantly ends the sim, stops siren/vibration, releases the
camera, and if device admin is active opens the admin removal screen:

- Tap the small grey **skull (☠)** in the top-left corner **7 times within 4 s**.
- **Press and hold the big red "SYSTEM LOCKED / YOUR DEVICE IS LOCKED"
  headline for 6 seconds.**
- The sim force-releases itself after **10 minutes** no matter what.
- Failsafe: `adb shell am force-stop com.china.fakeransom`.

## Permissions and why

| Permission | Purpose |
|---|---|
| `VIBRATE` | Shock-pattern vibration |
| `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS` | Lock-screen takeover at boot/after update |
| `REORDER_TASKS` | Pull the sim back over the home screen |
| `WAKE_LOCK` | Keep the screen on |
| `CAMERA` | Front-camera ghost preview behind the UI (no capture) |
| `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` | List real photo/download **names** in the terminal (read-only) |
| `READ_CONTACTS` | Show real contact names in the terminal (read-only) |
| Device admin (`force-lock`) | `lockNow()` screen lock during the "RESTORE ACCESS" beat |

## Build

```
./gradlew assembleDebug
```

CI uploads the APK as the `FakeRansom-APK` artifact.

**Run it only on devices you own or are authorized to demo on**, and always
brief participants beforehand. To an unprepared person, mid-scene, this is
indistinguishable from the real thing — that is precisely the point, and
precisely why the kill switch exists.
