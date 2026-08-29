# Physical-device qualification

Use at least three phones from at least two manufacturers covering Android 11 (API 30), Android 13
(API 33), and Android 16 (API 36). Test router Wi-Fi and LocalOnlyHotspot. Repeat the playback cases
with built-in speakers and Bluetooth output.

## Pass criteria

At all times, every connected and ready listener eventually reports the latest canonical playback
revision, queue revision, queue item and desired play/pause state. A detected mismatch must repair
without leaving and rejoining the room.

## Required scenarios

1. Create a room, join two listeners, add at least twenty tracks and play for one hour.
2. Rapidly alternate Play and Pause from different phones for two minutes.
3. Rapidly use Next/Previous among READY items while another listener is preparing media; tap an unavailable item and verify it prepares without changing current playback, then play it only after READY.
4. Reorder and clear the queue while imports and transfer preparation are active.
5. Turn one screen off for five minutes, then wake it and verify automatic convergence.
6. Background and foreground every phone while playback continues.
7. Disable Wi-Fi on one listener for thirty seconds, restore it and verify full state repair.
8. Disconnect the coordinator or hosted network and verify bounded recovery. If it cannot be recovered, verify that playback stops and the room ends cleanly without electing a replacement or leaving zombie room UI.
9. Kill and restart a participant process, verify the listener disappears after grace if it does not reconnect, then rejoin the active room.
10. Repeat song changes with Bluetooth connected and while switching audio routes.
11. On one non-controlling listener, trigger a real incoming call/audio-focus interruption, let the
    room advance by at least two songs, end the interruption, and verify that no automatic audio
    resumes. Tap Play/Rejoin once and verify that phone joins the current song/current room position.
12. On Android 16, keep cellular data enabled while connected to a private Wi-Fi network with no
    Internet. Join from Android 11/13 and transfer several full songs in both directions; verify the
    selected control/transfer route is `SYSTEM_DEFAULT` when the owning Wi-Fi network is already
    Android's active network, and stays `NETWORK_BOUND` when the room uses a non-default LAN.
13. Android 16 development-only Local Network Protection check: enable the platform compatibility
    restriction for the debug package, revoke Nearby devices, verify Create/Join requests permission,
    grant it, then verify discovery, control, full transfer, and playback. Disable the compatibility
    restriction after the test.
14. Interrupt and resume a large transfer across an API 30 ↔ API 36 pair and verify the final SHA-256
    matches before playback becomes eligible.

15. Swipe Unison away / remove its task while in a room and verify that phone leaves the room and stops synchronized playback rather than keeping a hidden room service alive.

## Evidence to retain

- APK version and git/source archive checksum;
- device model, API level and build fingerprint;
- room diagnostics from every phone;
- `Diagnostics` remains responsive, searchable, and free of raw credentials/paths;
- exact scenario and result;
- any interval where queue item or play/pause state diverged;
- whether automatic repair succeeded and how long it took.

A timing-only drift warning is not a state-divergence failure. Playing a different song or holding a
different play/pause intent is always a state-divergence failure.

## Android 16 local-network compatibility check

For debug qualification only, Android 16 can opt an app into Local Network Protection before the
future target-SDK enforcement. Exercise the permission path with:

```bash
adb shell am compat enable RESTRICT_LOCAL_NETWORK com.darius.unison.debug
# Reboot the Android 16 device after enabling the compatibility flag.
adb shell pm revoke com.darius.unison.debug android.permission.NEARBY_WIFI_DEVICES
# Exercise Create/Join, grant Nearby devices in the system prompt, then run control + transfer + playback.
adb shell am compat disable RESTRICT_LOCAL_NETWORK com.darius.unison.debug
```

Do not ship test-only compatibility flags or permissions in the APK.
