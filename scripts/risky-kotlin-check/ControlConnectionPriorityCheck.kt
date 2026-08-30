import com.darius.unison.network.ControlConnectionPriorityTest

fun main() {
    val test = ControlConnectionPriorityTest()
    test.readyQueuesDrainInPriorityOrder()
    test.sustainedLowerPriorityReadinessCannotStarveGuaranteedOrClockTraffic()
    println("CONTROL_CONNECTION_PRIORITY_OK")
}
