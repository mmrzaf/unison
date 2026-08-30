# ADR-003: Canonical playback owns distributed intent

**Status:** Accepted

Media3 is a device-local execution engine, not the distributed source of truth. Canonical queue/item,
play/pause, seek, natural-boundary, and replay intent are decided by room logic; `PlayerExecutor` is the
single Media3 mutation authority on each device.
