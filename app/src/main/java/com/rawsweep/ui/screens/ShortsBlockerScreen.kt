package com.rawsweep.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = ShortsBlockerService.isServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

            StatusCard(isActive = isServiceEnabled)

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
            BulletItem("Shorts from anywhere — Shorts opened from recommendations, links, or any other source are blocked")

            Spacer(Modifier.height(16.dp))
            Text(
                "Shorts may still appear visually in search results and recommendations, but they cannot be opened while the blocker is active.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusCard(isActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isActive)
                    Icons.Filled.CheckCircle
                else
                    Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = if (isActive)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (isActive) "Active" else "Not Active",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (isActive)
                        "YouTube Shorts are being blocked"
                    else
                        "Enable the accessibility service to start blocking",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer,
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
