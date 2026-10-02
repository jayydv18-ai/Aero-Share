package com.example.aeroshare.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionItem(
    val permission: String,
    val title: String,
    val description: String,
    val isGranted: Boolean
)

object PermissionManager {

    fun getAllRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        // 1. Android 13+ (Tiramisu, API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 2. Android 12+ (Snow Cone, API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Android 11 and lower
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // 3. Location (needed for Wi-Fi Direct and BLE Nearby discovery)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        // 4. Camera (for QR Code scanning)
        permissions.add(Manifest.permission.CAMERA)

        return permissions.distinct()
    }

    fun getPermissionsStatus(context: Context): List<PermissionItem> {
        val list = mutableListOf<PermissionItem>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(
                PermissionItem(
                    permission = Manifest.permission.NEARBY_WIFI_DEVICES,
                    title = "Nearby Wi-Fi Devices",
                    description = "Required for high-speed Wi-Fi Direct peer-to-peer file transfer",
                    isGranted = isGranted(context, Manifest.permission.NEARBY_WIFI_DEVICES)
                )
            )
            list.add(
                PermissionItem(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    title = "Notifications",
                    description = "Shows live transfer progress, speed (MB/s), and completion alerts in background",
                    isGranted = isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bleGranted = isGranted(context, Manifest.permission.BLUETOOTH_SCAN) &&
                    isGranted(context, Manifest.permission.BLUETOOTH_ADVERTISE) &&
                    isGranted(context, Manifest.permission.BLUETOOTH_CONNECT)

            list.add(
                PermissionItem(
                    permission = Manifest.permission.BLUETOOTH_SCAN,
                    title = "Nearby Bluetooth Devices",
                    description = "Scans, advertises, and connects with nearby sender/receiver devices",
                    isGranted = bleGranted
                )
            )
        }

        list.add(
            PermissionItem(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                title = "Device Location",
                description = "Required by Android OS for Wi-Fi Direct & local network discovery",
                isGranted = isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        )

        list.add(
            PermissionItem(
                permission = Manifest.permission.CAMERA,
                title = "Camera (QR Scanner)",
                description = "Scan receiver's QR code for instant direct pairing without searching",
                isGranted = isGranted(context, Manifest.permission.CAMERA)
            )
        )

        return list
    }

    fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun areAllPermissionsGranted(context: Context): Boolean {
        return getAllRequiredPermissions().all { isGranted(context, it) }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
