package com.darius.unison.ui

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionControllerTest {
    @Test
    fun ordinaryRoomPermissionsMatchSupportedApiMatrix() {
        assertEquals(emptyList<String>(), PermissionController.localNetworkPermissions(30).toList())
        assertEquals(
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES),
            PermissionController.localNetworkPermissions(33).toList(),
        )
        assertEquals(
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES),
            PermissionController.localNetworkPermissions(36).toList(),
        )
    }

    @Test
    fun offlineHotspotPermissionsMatchSupportedApiMatrix() {
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            PermissionController.offlineNetworkPermissions(30).toList(),
        )
        assertEquals(
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES),
            PermissionController.offlineNetworkPermissions(33).toList(),
        )
        assertEquals(
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES),
            PermissionController.offlineNetworkPermissions(36).toList(),
        )
    }
}
