package com.darius.unison.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputRouteQueryPolicyTest {
    @Test
    fun android11And12DoNotClaimActiveRouteQuerySupport() {
        assertFalse(OutputRouteQueryPolicy.canQueryActiveMediaRoute(30))
        assertFalse(OutputRouteQueryPolicy.canQueryActiveMediaRoute(31))
        assertFalse(OutputRouteQueryPolicy.canQueryActiveMediaRoute(32))
    }

    @Test
    fun android13AndNewerUseActiveMediaRouteQuery() {
        assertTrue(OutputRouteQueryPolicy.canQueryActiveMediaRoute(33))
        assertTrue(OutputRouteQueryPolicy.canQueryActiveMediaRoute(36))
    }
}
