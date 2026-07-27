# Keep kotlinx.serialization-generated serializers used by polymorphic protocol bodies.
-if @kotlinx.serialization.Serializable class com.darius.unison.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keepclasseswithmembers class com.darius.unison.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Media3 and Room ship consumer rules. Add project-specific release rules here after profiling.
