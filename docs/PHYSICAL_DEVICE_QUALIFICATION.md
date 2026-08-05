# Physical-device qualification

Use at least three phones from two manufacturers across Android API 30–33. Test router Wi-Fi and
LocalOnlyHotspot. Repeat the playback cases with built-in speakers and Bluetooth output.

## Pass criteria

At all times, every connected and ready listener eventually reports the latest canonical playback
revision, queue revision, queue item and desired play/pause state. A detected mismatch must repair
without leaving and rejoining the room.

## Required scenarios

1. Create a room, join two listeners, add at least twenty tracks and play for one hour.
2. Rapidly alternate Play and Pause from different phones for two minutes.
3. Select Next, Previous and arbitrary queue items while another listener is downloading.
4. Reorder and clear the queue while imports and transfer preparation are active.
5. Turn one screen off for five minutes, then wake it and verify automatic convergence.
6. Background and foreground every phone while playback continues.
7. Disable Wi-Fi on one listener for thirty seconds, restore it and verify full state repair.
8. Disconnect the coordinator and verify election/reconnect behavior.
9. Kill and restart a participant process, then rejoin the active room.
10. Repeat song changes with Bluetooth connected and while switching audio routes.

## Evidence to retain

- APK version and git/source archive checksum;
- device model, API level and build fingerprint;
- room diagnostics from every phone;
- exact scenario and result;
- any interval where queue item or play/pause state diverged;
- whether automatic repair succeeded and how long it took.

A timing-only drift warning is not a state-divergence failure. Playing a different song or holding a
different play/pause intent is always a state-divergence failure.
