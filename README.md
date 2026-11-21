# FakeRansom Demo

FakeRansom is a local Android security-awareness simulation. It stages a ransomware-style terminal and warning screen without harming user data.

## Safety behavior

- No encryption, renaming, deletion, or modification of files.
- No network permission and no data upload.
- File and contact display names are read-only and limited to a small sample.
- Permissions are requested only after the user taps Start Simulation.
- The warning sound is synthesized locally. It stops when the app leaves the foreground or closes.
- The app has visible sound and exit controls.
- No boot receiver, forced foreground behavior, lock-screen takeover, or blocked Back button.

## Personal data access

If permission is granted, the demo displays up to 8 photo names, up to 8 download names on Android 12 and older, and up to 8 contact display names. It does not read file contents. On Android 13 and newer, download names are skipped.

Declining a permission does not prevent the simulation from running.

## Build

```bash
./gradlew assembleDebug
```

The GitHub Actions workflow should upload the debug APK as its configured artifact.
