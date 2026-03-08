package com.rawsweep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val viewModel: RawPhotoViewModel by viewModels()
    private var hasMediaPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasMediaPermission = checkMediaPermission()

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasMediaPermission = granted
            if (granted) {
                viewModel.refresh()
            }
        }

        val deleteLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.onDeleteConfirmed()
            }
        }

        if (hasMediaPermission) {
            viewModel.refresh()
        }

        setContent {
            MaterialTheme {
                RawSweepScreen(
                    viewModel = viewModel,
                    hasPermission = hasMediaPermission,
                    onGrantPermission = {
                        permissionLauncher.launch(requiredMediaPermission())
                    },
                    onRefresh = { viewModel.refresh() },
                    onDeleteSelected = {
                        val sender = viewModel.buildDeleteIntentSender()
                        if (sender != null) {
                            runCatching {
                                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            }.onFailure {
                                Toast.makeText(
                                    this,
                                    getString(R.string.load_error),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
        }
    }

    private fun requiredMediaPermission(): String {
        return if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun checkMediaPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            requiredMediaPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun RawSweepScreen(
    viewModel: RawPhotoViewModel,
    hasPermission: Boolean,
    onGrantPermission: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var previewPhoto by remember { mutableStateOf<RawPhoto?>(null) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = {
                    if (hasPermission) {
                        Text(stringResource(R.string.raw_photos_found, uiState.photos.size))
                    } else {
                        Text(stringResource(id = R.string.app_name))
                    }
                },
                actions = {
                    if (hasPermission) {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (hasPermission && uiState.selectedCount > 0) {
                Surface(shadowElevation = 8.dp, tonalElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.selected_count, uiState.selectedCount))
                        Button(onClick = onDeleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.delete_selected))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        when {
            !hasPermission -> {
                PermissionState(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    onGrantPermission = onGrantPermission
                )
            }

            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.load_error),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            uiState.photos.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_raw_photos),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            else -> {
                RawPhotoGrid(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    photos = uiState.photos,
                    selectedIds = uiState.selectedIds,
                    onPreview = { previewPhoto = it },
                    onToggleSelection = viewModel::toggleSelection
                )
            }
        }
    }

    previewPhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { previewPhoto = null },
            title = { Text(text = stringResource(R.string.preview_title)) },
            text = {
                Column {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = photo.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = DateFormat.getDateTimeInstance().format(Date(photo.dateTaken)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { previewPhoto = null }) {
                    Text(stringResource(R.string.close_preview))
                }
            }
        )
    }
}

@Composable
private fun PermissionState(
    modifier: Modifier = Modifier,
    onGrantPermission: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.permission_explanation),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Button(onClick = onGrantPermission) {
                Text(text = stringResource(R.string.grant_permission))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RawPhotoGrid(
    modifier: Modifier = Modifier,
    photos: List<RawPhoto>,
    selectedIds: Set<Long>,
    onPreview: (RawPhoto) -> Unit,
    onToggleSelection: (Long) -> Unit
) {
    val selectionMode = selectedIds.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(photos, key = { it.id }) { photo ->
            val selected = photo.id in selectedIds
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .combinedClickable(
                        onClick = {
                            if (selectionMode) {
                                onToggleSelection(photo.id)
                            } else {
                                onPreview(photo)
                            }
                        },
                        onLongClick = { onToggleSelection(photo.id) }
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        text = photo.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}
