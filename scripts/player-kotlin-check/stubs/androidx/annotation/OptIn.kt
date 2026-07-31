package androidx.annotation

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FILE)
annotation class OptIn(val markerClass: Array<kotlin.reflect.KClass<*>>)
