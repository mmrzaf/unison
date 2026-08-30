import org.junit.After
import org.junit.Before
import org.junit.Test

fun main() {
    val classes =
        listOf(
            com.darius.unison.protocol.Srp6aCoreRfc5054Test::class.java,
            com.darius.unison.protocol.PinPakeTest::class.java,
            com.darius.unison.room.RoomIngressAuthorityTest::class.java,
            com.darius.unison.room.PeerEndpointAuthorityTest::class.java,
            com.darius.unison.room.SessionJobRegistryTest::class.java,
            com.darius.unison.room.RoomLifecycleSeamRegressionTest::class.java,
        )
    var passed = 0
    classes.forEach { clazz ->
        val before = clazz.declaredMethods.filter { it.isAnnotationPresent(Before::class.java) }
        val after = clazz.declaredMethods.filter { it.isAnnotationPresent(After::class.java) }
        clazz.declaredMethods
            .filter { it.isAnnotationPresent(Test::class.java) }
            .forEach { test ->
                val instance = clazz.getDeclaredConstructor().newInstance()
                try {
                    before.forEach { it.invoke(instance) }
                    test.invoke(instance)
                    passed += 1
                } finally {
                    after.forEach { it.invoke(instance) }
                }
            }
    }
    println("HARDENING_TESTS_OK ($passed tests)")
}
