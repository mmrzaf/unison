package com.darius.unison.playback

/** Platform capability gate for querying the actual media route rather than connected inventory. */
object OutputRouteQueryPolicy {
    const val ACTIVE_MEDIA_ROUTE_API = 33

    fun canQueryActiveMediaRoute(apiLevel: Int): Boolean = apiLevel >= ACTIVE_MEDIA_ROUTE_API
}
