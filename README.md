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
6. **Fake firmware reflash** — ~2.5 minutes in, the screen goes black and types
   `wiping /data ... ok`, then "restores" straight back with
   "RESTORE FAILED — ATTEMPT 2/3" and the siren resumes. Up to 3 attempts.
7. **"Compromised" interlude** — before the note, a 12-second gold-on-blue
   security-response screen: "This device was COMPROMISED. Case #0472-B."
   (deliberately NOT the old police-ransomware "you are wanted" framing).
8. **Wallpaper takeover** — home + lock wallpaper replaced with a rendered
   ransom image (original captured and restored on exit; live wallpapers come
   back as a static image, an Android API limitation).
9. **Voice threats** — robotic TextToSpeech lines on loop between alarm bursts.
10. **Overlay attack** — with "Draw over other apps" granted via the
    verification chain, a full-screen lock overlay persists above the home
    screen whenever the app is backgrounded. The overlay deliberately starts
    BELOW the status bar so the shade, power menu and system gestures are
    always reachable, carries its own phone-independent 10-minute watchdog,
    shows a persistent "SIM ACTIVE - tap to end it" notification, and has its
    own skull kill gesture.
11. **Clipboard poisoning** — clipboard silently overwritten with the fake BTC
    address every 5 s; reset on end.
12. **Camera disabled system-wide** once device admin is granted
    (`setCameraDisabled`) — re-enabled on every exit path.
13. **DND takeover** — with notification-policy access, the sim pins
    "alarms only" mode (siren pierces, everything else mutes); previous filter
    restored on exit.
14. **Payment portal** — fake BTC send screen with a procedurally drawn QR
    (no library), live "confirmations" counter and wallet expiry; back returns
    to the note.
15. **Fake incoming call** — realistic dialer UI (avatar, number, ripple around Answer), dual-tone ring with ring-pattern vibration; answering switches to an in-call screen with live timer, line hiss, mute/speaker controls and a scripted distorted-voice monologue with subtitles; the line cuts itself at the end
    escalation; decline/answer both return to the sim with taunts.
16. **Escalation ramp** — siren playback rate +3.8%/20 s (up to +34%),
    vibration gap tightens 1.5s -> 0.3s, border strobe 260 ms -> 120 ms,
    countdown shifts yellow -> orange -> red.

## Kill switch (presenter — memorize one of these)

Any of these instantly ends the sim and triggers a full unwind: siren stopped,
TTS stopped, vibration cancelled, CLIPBOARD reset, CAMERA re-enabled system-wide
(if device admin was granted), DND filter restored, WALLPAPER restored from the
captured snapshot, overlay service stopped, admin-removal screen opened if
active:

- Tap the small grey **skull (☠)** — it exists on EVERY surface: main screen,
  overlay, payment portal, fake call — **7 times within 4 s**.
- **Press and hold the big red "SYSTEM LOCKED / YOUR DEVICE IS LOCKED"
  headline for 6 seconds.**
- The sim force-releases itself after **10 minutes**, and the OVERLAY has its
  own independent 10-minute watchdog, so it cannot outlive the sim even if the
  activity is killed from recents.
- The overlay shows a persistent **"SIM ACTIVE — tap to end it" notification**
  with a one-tap end action at all times.
- The status bar strip is deliberately NOT covered by the overlay: the
  notification shade, power menu and system gestures always work; safe mode
  (if ever needed) also kills all overlays.
- Failsafe: `adb shell am force-stop com.china.fakeransom` (or just reboot —
  the boot takeover returns the sim WITH its kill switch, never without it).

## Permissions and why

| Permission | Purpose |
|---|---|
| `VIBRATE` | Shock-pattern vibration |
| `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS` | Lock-screen takeover at boot/after update |
| `REORDER_TASKS` | Pull the sim back over the home screen |
| `WAKE_LOCK` | Keep the screen on |
| `CAMERA` | Front-camera ghost preview behind the UI (no capture) |
| `SET_WALLPAPER` | Apply the ransom image over home + lock wallpaper during the sim; the originals are captured first and put back on end |
| `SYSTEM_ALERT_WINDOW` | Lock overlay from a foreground service (non-trapping: shade/power always reachable) |
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
