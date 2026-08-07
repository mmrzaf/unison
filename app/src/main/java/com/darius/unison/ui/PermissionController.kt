package com.darius.unison.ui

import android.Manifest
import android.os.Build

/** Runtime permissions that gate Unison's nearby-network workflows. */
object PermissionController {

    // Manifest.permission.NEARBY_WIFI_DEVICES (API 33+ runtime gate)
    private const val NEARBY_WIFI_DEVICES =
        "android.permission.NEARBY_WIFI_DEVICES"

    /**
     * Android 13+ exposes nearby Wi-Fi as a runtime permission.
     */
    fun localNetworkPermissions(
        apiLevel: Int = Build.VERSION.SDK_INT,
    ): Array<String> =
        if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(NEARBY_WIFI_DEVICES)
        } else {
            emptyArray()
        }

    fun offlineNetworkPermissions(
        apiLevel: Int = Build.VERSION.SDK_INT,
    ): Array<String> =
        if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
            localNetworkPermissions(apiLevel)
        } else {
            // Android 12 and earlier LocalOnlyHotspot requires precise location.
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
}
