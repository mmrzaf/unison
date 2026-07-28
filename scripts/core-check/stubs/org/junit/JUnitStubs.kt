package org.junit

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Test

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

    fun assertEquals(expected: Long, actual: Long, delta: Long) {
        if (kotlin.math.abs(expected - actual) > delta) {
            throw AssertionError("Expected <$expected> ± $delta, got <$actual>")
        }
    }
}
