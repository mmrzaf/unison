package com.darius.unison.ui

import android.Manifest
import android.os.Build

object PermissionController {
    fun offlineNetworkPermissions(): Array<String> =
        when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
            else ->
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
        }
}
