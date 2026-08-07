# Testing

## Complete repository gate

After the pinned Gradle distribution and dependency cache are available:

```bash
./scripts/verify-offline-ready.sh
./scripts/check-release-quality.sh
./gradlew --offline --no-daemon --stacktrace \
  testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease
```

`check-release-quality.sh` runs:

- source-tree, protocol, schema, security, and architecture invariants;
- Kotlin source sanity checks;
- deterministic reducer, authentication, transfer, lifecycle, playback, and synchronization tests;
- focused Media3/state-machine compilation checks;
- Android network lifecycle tests against stubs;
- playback-log analyzer self-test;
- 100,000-track search benchmark.

## Stability invariants

Keep these green:

- all READY peers converge on the latest queue revision, playback revision, queue item, and
  play/pause intent;
- a warming, stale-clock, or degraded peer cannot inflate transport timing or block healthy READY
  listeners;
- every release synchronization profile keeps long-play drift and player speed within its tested
  bounds;
- stale callbacks, packets, schedules, imports, and transfer results cannot mutate newer state;
- wrong item and wrong play/pause are repaired before position drift;
- coordinator and participant players use the same canonical application path;
- clear and leave invalidate pending queue preparation;
- control and file traffic reject tampering and replay;
- active leases prevent deletion or replacement;
- cancellation closes sockets and releases locks/jobs;
- meaningful player transitions enter the actor, position telemetry does not;
- one user action creates at most one effective canonical transition.

## Device qualification

Repository tests cannot prove real Wi-Fi scheduling, vendor Media3 behavior, Bluetooth buffering,
foreground restrictions, process death, or power management. Complete
[Physical-device qualification](PHYSICAL_DEVICE_QUALIFICATION.md) for every release candidate.

## Playback trace

Capture Logcat from before room creation until after leaving, then run:

```bash
./scripts/capture-playback-log.sh unison-playback.ndjson
./scripts/analyze-playback-log.py unison-playback.ndjson --strict
```

Strict analysis rejects transition storms, unavailable-song failures, circuit-breaker activation,
and playback-dispatch failures.
