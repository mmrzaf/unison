import org.junit.After
import org.junit.Before
import org.junit.Test

fun main() {
    val classes =
        listOf(
            com.darius.unison.util.DiagnosticEventTest::class.java,
            com.darius.unison.util.DiagnosticLogTest::class.java,
        )
    var passed = 0
    classes.forEach { type ->
        val beforeMethods = type.declaredMethods.filter { it.getAnnotation(Before::class.java) != null }
        val afterMethods = type.declaredMethods.filter { it.getAnnotation(After::class.java) != null }
        type.declaredMethods
            .filter { it.getAnnotation(Test::class.java) != null }
            .sortedBy { it.name }
            .forEach { method ->
                val instance = type.getDeclaredConstructor().newInstance()
                try {
                    beforeMethods.forEach { it.invoke(instance) }
                    method.invoke(instance)
                    passed++
                } catch (error: java.lang.reflect.InvocationTargetException) {
                    throw AssertionError("${type.simpleName}.${method.name} failed", error.targetException)
                } finally {
                    afterMethods.forEach { it.invoke(instance) }
                }
            }
    }
    println("DIAGNOSTICS_TESTS_OK ($passed tests)")
}
