package com.rawsweep.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rawsweep.ui.components.DeleteConfirmDialog
import com.rawsweep.ui.components.EmptyState
import com.rawsweep.ui.components.PhotoGridItem
import com.rawsweep.ui.components.SortMenu
import com.rawsweep.viewmodel.GalleryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPhotoClick: (photoId: Long) -> Unit,
    onDeleteRequest: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    if (!hasPermission) {
        PermissionRequestScreen(
            onRequestPermission = onRequestPermission,
            onBack = onBack,
        )
        return
    }

    LaunchedEffect(Unit) {
        viewModel.loadPhotosIfNeeded()
    }

    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var alsoDeleteFromGooglePhotos by remember { mutableStateOf(true) }

    BackHandler(enabled = state.isSelectionMode) {
        viewModel.clearSelection()
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    if (showDeleteDialog) {
        val selectedSize = viewModel.getSelectedSize()
        val formattedSize = formatSize(selectedSize)
        DeleteConfirmDialog(
            count = state.selectedIds.size,
            formattedSize = formattedSize,
            alsoDeleteFromGooglePhotos = alsoDeleteFromGooglePhotos,
            onAlsoDeleteFromGooglePhotosChange = { alsoDeleteFromGooglePhotos = it },
            onConfirm = {
                showDeleteDialog = false
                onDeleteRequest(alsoDeleteFromGooglePhotos)
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (state.isSelectionMode) {
                        Text(
                            "${state.selectedIds.size} selected",
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Column {
                            Text("Raw Photos", fontWeight = FontWeight.Bold)
                            if (state.photos.isNotEmpty()) {
                                Text(
                                    "${state.photos.size} RAW files · ${formatSize(state.totalRawSize)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (state.isSelectionMode) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    if (state.isSelectionMode) {
                        IconButton(onClick = {
                            if (state.selectedIds.size == state.photos.size) {
                                viewModel.clearSelection()
                            } else {
                                viewModel.selectAll()
                            }
                        }) {
                            Icon(
                                imageVector = if (state.selectedIds.size == state.photos.size)
                                    Icons.Filled.Deselect
                                else
                                    Icons.Default.SelectAll,
                                contentDescription = if (state.selectedIds.size == state.photos.size)
                                    "Deselect all"
                                else
                                    "Select all",
                            )
                        }
                    } else {
                        SortMenu(
                            currentSort = state.sortOption,
                            onSortSelected = { viewModel.setSortOption(it) },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (state.isSelectionMode)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = state.isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                BottomAppBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "${state.selectedIds.size} file${if (state.selectedIds.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                formatSize(viewModel.getSelectedSize()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        FilledTonalButton(
                            onClick = { showDeleteDialog = true },
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Delete RAW",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Scanning for RAW photos…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.photos.isEmpty() -> {
                EmptyState(modifier = Modifier.padding(padding))
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.loadPhotos() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = state.photos,
                            key = { it.id },
                        ) { photo ->
                            PhotoGridItem(
                                photo = photo,
                                isSelected = state.selectedIds.contains(photo.id),
                                isSelectionMode = state.isSelectionMode,
                                onTap = {
                                    if (state.isSelectionMode) {
                                        viewModel.toggleSelection(photo.id)
                                    } else {
                                        onPhotoClick(photo.id)
                                    }
                                },
                                onLongPress = {
                                    viewModel.toggleSelection(photo.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Raw Photos", fontWeight = FontWeight.Bold) },
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Photo Access Required",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "This tool needs access to your photos to find and manage RAW (DNG) files from your camera.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Access")
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024)
    else if (mb >= 1) "%.1f MB".format(mb)
    else "%.0f KB".format(bytes / 1024.0)
}
