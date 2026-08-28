package com.subscriptiontracker.platform.reminder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class NotificationPermissionStatus {
    GRANTED,
    DENIED,
    NOT_REQUIRED,
}

fun interface NotificationPermissionStatusProvider {
    fun currentStatus(): NotificationPermissionStatus
}

class AndroidNotificationPermissionStatusProvider(
    private val context: Context,
) : NotificationPermissionStatusProvider {
    override fun currentStatus(): NotificationPermissionStatus = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> NotificationPermissionStatus.NOT_REQUIRED
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED -> NotificationPermissionStatus.GRANTED
        else -> NotificationPermissionStatus.DENIED
    }
}

class NotificationAppSettingsIntentFactory(
    private val context: Context,
) {
    fun create(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
