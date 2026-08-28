package com.subscriptiontracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subscriptiontracker.presentation.CoreViewModel
import com.subscriptiontracker.presentation.ScheduleRefresh
import com.subscriptiontracker.ui.screens.CoreApp
import com.subscriptiontracker.ui.theme.SubscriptionTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = application as SubscriptionTrackerApplication
        setContent {
            SubscriptionTrackerTheme {
                val model: CoreViewModel = viewModel {
                    CoreViewModel(
                        container.subscriptionRepository,
                        container.recurringEventRepository,
                        container.quotaRepository,
                        container.backupGateway,
                        ScheduleRefresh { container.scheduleReconciler.reconcile() },
                    )
                }
                val state by model.state.collectAsStateWithLifecycle()
                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json"),
                ) { uri ->
                    uri ?: return@rememberLauncherForActivityResult
                    contentResolver.openOutputStream(uri)?.let { output -> model.export(output) { output.close() } }
                }
                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri ?: return@rememberLauncherForActivityResult
                    contentResolver.openInputStream(uri)?.let(model::validateImport)
                }
                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refresh() }
                CoreApp(
                    state = state,
                    model = model,
                    notificationStatus = container.notificationPermissionStatus.currentStatus(),
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onOpenAppSettings = { startActivity(container.notificationSettingsIntentFactory.create()) },
                    onExport = { exportLauncher.launch("subscription-tracker-backup.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
                )
            }
        }
    }
}
