# Unison's room protocol and persisted room model are deliberately small. Keep them intact so
# Kotlin Serialization remains deterministic across release builds and protocol revisions.
-keep,includedescriptorclasses class com.darius.unison.protocol.** { *; }
-keep,includedescriptorclasses class com.darius.unison.model.** { *; }

# AndroidX libraries provide their own consumer rules. Preserve metadata used by Room, Kotlin,
# and generated adapters without retaining the rest of the application wholesale.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault
