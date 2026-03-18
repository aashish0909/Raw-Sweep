package com.rawsweep.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rawsweep.service.ShortsBlockerService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsBlockerScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var isServiceEnabled by remember { mutableStateOf(false) }
    var isBlockingEnabled by remember { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = ShortsBlockerService.isServiceEnabled(context)
                isBlockingEnabled = ShortsBlockerService.isBlockingEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isFullyActive = isServiceEnabled && isBlockingEnabled
    val isPaused = isServiceEnabled && !isBlockingEnabled

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shorts Blocker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))

            StatusCard(isFullyActive = isFullyActive, isPaused = isPaused)

            if (isServiceEnabled) {
                Spacer(Modifier.height(16.dp))

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Block Shorts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (isBlockingEnabled) "Shorts are being blocked"
                                else "Blocking is paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(
                            checked = isBlockingEnabled,
                            onCheckedChange = { enabled ->
                                isBlockingEnabled = enabled
                                ShortsBlockerService.setBlockingEnabled(context, enabled)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isServiceEnabled) "Open Accessibility Settings" else "Enable in Settings")
            }

            if (!isServiceEnabled) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "How to enable",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                NumberedStep("1", "Tap \"Enable in Settings\" above")
                NumberedStep("2", "Find \"Toolbox\" or \"Shorts Blocker\" in the list")
                NumberedStep("3", "Toggle the service on")
                NumberedStep("4", "Confirm when prompted")
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "What it blocks",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            BulletItem("Shorts tab — tapping it immediately redirects you back")
            BulletItem("Shorts from search — opening a Short from search results is blocked")
            BulletItem("Shorts from anywhere — Shorts opened from home feed, subscriptions, recommendations, or links are blocked")

            Spacer(Modifier.height(16.dp))
            Text(
                "Shorts may still appear visually in feeds and search results, but they cannot be opened while blocking is active.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusCard(isFullyActive: Boolean, isPaused: Boolean) {
    val containerColor = when {
        isFullyActive -> MaterialTheme.colorScheme.primaryContainer
        isPaused -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when {
        isFullyActive -> MaterialTheme.colorScheme.onPrimaryContainer
        isPaused -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    val icon = when {
        isFullyActive -> Icons.Filled.CheckCircle
        isPaused -> Icons.Outlined.PauseCircleOutline
        else -> Icons.Outlined.ErrorOutline
    }
    val title = when {
        isFullyActive -> "Active"
        isPaused -> "Paused"
        else -> "Not Active"
    }
    val subtitle = when {
        isFullyActive -> "YouTube Shorts are being blocked"
        isPaused -> "Toggle on to resume blocking"
        else -> "Enable the accessibility service to start blocking"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun NumberedStep(number: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BulletItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "•",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(16.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
