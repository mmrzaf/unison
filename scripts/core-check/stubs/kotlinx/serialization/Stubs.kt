package kotlinx.serialization

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class Serializable

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class SerialName(val value: String)
