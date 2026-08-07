package org.junit

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Test

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Before

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class After

@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Rule

object Assert {
    fun assertTrue(value: Boolean) {
        if (!value) throw AssertionError("Expected true")
    }

    fun assertFalse(value: Boolean) {
        if (value) throw AssertionError("Expected false")
    }

    fun assertNull(value: Any?) {
        if (value != null) throw AssertionError("Expected null, got $value")
    }

    fun assertNotNull(value: Any?) {
        if (value == null) throw AssertionError("Expected non-null value")
    }

    fun <T> assertEquals(expected: T, actual: T) {
        if (expected != actual) throw AssertionError("Expected <$expected>, got <$actual>")
    }

    fun <T> assertNotEquals(unexpected: T, actual: T) {
        if (unexpected == actual) throw AssertionError("Did not expect <$actual>")
    }

    fun assertEquals(expected: Long, actual: Long, delta: Long) {
        if (kotlin.math.abs(expected - actual) > delta) {
            throw AssertionError("Expected <$expected> ± $delta, got <$actual>")
        }
    }

    fun <T : Throwable> assertThrows(type: Class<T>, block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (type.isInstance(error)) return type.cast(error)
            throw AssertionError("Expected ${type.name}, got ${error::class.java.name}", error)
        }
        throw AssertionError("Expected ${type.name} to be thrown")
    }
}
