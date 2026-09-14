# Unison uses generated serializers and generated Room adapters through static references. AndroidX
# dependencies ship their own consumer rules, so the application deliberately has no package-wide
# keep rules. If a future release needs a keep rule, add the narrowest rule for the exact reflected
# entry point rather than retaining an entire package.

# Structured diagnostics intentionally use Kotlin simpleName for these sealed event/command families,
# and the release analyzers consume those stable names. Preserve names only; R8 may still shrink and
# optimize the classes and their members normally.
-keepnames class com.darius.unison.model.AppCommand$*
-keepnames class com.darius.unison.model.UserCommand$*
-keepnames class com.darius.unison.protocol.ProtocolBody$*
-keepnames class com.darius.unison.room.RoomEvent$*
-keepnames class com.darius.unison.playback.PlaybackFailure$*
