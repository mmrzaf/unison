# GitHub release checklist

This checklist is required before a public Unison release.

## Build and signing

- Use JDK 21 and Android SDK 36. The application bytecode target remains Java 17 for broad Android toolchain compatibility.
- Keep `minSdk = 30`, `targetSdk = 36`, and `compileSdk = 36` unless a deliberate platform migration is being made.
- Increase `versionCode` for every uploaded release and update `versionName` only when intentionally releasing a new version.
- Build `assembleRelease` for GitHub distribution.
- Verify the APK signature.
- Back up the release key and credentials in two secure locations.
- Never commit `keystore.properties`, signing keys, `local.properties`, or generated binaries.

## Automated gates

```bash
./scripts/check-static.sh
./scripts/check-core.sh
./gradlew --no-daemon testDebugUnitTest lintRelease assembleRelease
```

Do not publish if tests, Android Lint, signing verification, or the release build fail.

## Required physical-device gates

Test the signed release build—not only debug builds—on:

- Android 11 / API 30;
- Android 12 or 12L / API 31–32;
- Android 13 / API 33;
- at least one Android 14–16 device.

Run solo and multi-device rooms. Verify file-picker import, share-to-Unison, M3U import/export, playlists, same-Wi-Fi discovery, QR joining, local-only hotspot, transfer resume, screen-off behavior, reconnection, and two-hour playback.

Verify notification, lock-screen, wired-headset, Bluetooth-headset, and system media controls. Each control must affect the room, not only the local phone. Verify an ordinary third-party MediaController cannot connect with transport privileges and that repeat, shuffle, speed, and queue mutation are unavailable through the session.

## GitHub release page

- Publish the privacy policy at a stable public HTTPS URL.
- Explain that the app uses local network sockets and that Android's `INTERNET` permission does not mean an online service is used.
- Explain that the exported MediaSessionService is restricted in code to trusted Android media clients.
- Attach only the signed APK and `SHA256SUMS.txt` to the matching GitHub tag.
- Provide screenshots from the current UI and a support contact in the repository.

## Release smoke test

1. Install the signed APK on a clean Android 11 device.
2. Import one song and play it in a solo room.
3. Confirm media notification metadata and play/pause/seek/next/previous controls.
4. Join from a second phone and verify synchronized playback and transfer.
5. Force Wi-Fi loss and verify recovery or a clear user-facing failure.
6. End the room, clear temporary music, restart the app, and verify retained library and playlists.
7. Install the new signed APK over the previous production build and verify data migration/update succeeds.
