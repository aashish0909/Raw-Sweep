package com.rawsweep

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                RawSweepScreen()
            }
        }
    }
}

data class RawPhotoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val dateTakenMs: Long
)

private val rawMimeTypes = listOf(
    "image/x-adobe-dng",
    "image/dng",
    "image/x-canon-cr2",
    "image/x-canon-cr3",
    "image/x-nikon-nef",
    "image/x-sony-arw",
    "image/x-fuji-raf",
    "image/x-panasonic-rw2",
    "image/x-pentax-pef",
    "image/x-olympus-orf"
)

private val rawExtensions = listOf(
    "dng", "cr2", "cr3", "nef", "arw", "raf", "rw2", "orf", "pef"
)

private fun queryRawPhotos(context: Context): List<RawPhotoItem> {
    val resolver = context.contentResolver
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED
    )

    val mimeSelection = rawMimeTypes.joinToString(" OR ") { "${MediaStore.Images.Media.MIME_TYPE} = ?" }
    val nameSelection = rawExtensions.joinToString(" OR ") {
        "LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) LIKE ?"
    }
    val selection = "($mimeSelection) OR ($nameSelection)"
    val args = buildList {
        addAll(rawMimeTypes)
        addAll(rawExtensions.map { "%.$it" })
    }.toTypedArray()

    val photos = mutableListOf<RawPhotoItem>()
    resolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        args,
        "${MediaStore.Images.Media.DATE_TAKEN} DESC"
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val dateTakenIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIndex)
            val name = cursor.getString(nameIndex).orEmpty()
            val mimeType = cursor.getString(mimeIndex)
            val size = cursor.getLong(sizeIndex)
            val dateTaken = cursor.getLong(dateTakenIndex)
            val dateAddedSeconds = cursor.getLong(dateAddedIndex)
            val fallbackDateTaken = dateAddedSeconds * 1000L
            val itemUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

            photos += RawPhotoItem(
                id = id,
                uri = itemUri,
                displayName = name,
                mimeType = mimeType,
                sizeBytes = size,
                dateTakenMs = if (dateTaken > 0L) dateTaken else fallbackDateTaken
            )
        }
    }
    return photos
}

private fun requiredMediaPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RawSweepScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permission = requiredMediaPermission()

    var hasPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var reloadTrigger by remember { mutableStateOf(0) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var previewItem by remember { mutableStateOf<RawPhotoItem?>(null) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            Toast.makeText(context, "Permission is required to read RAW photos", Toast.LENGTH_LONG).show()
        }
    }

    val deleteIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Deleted selected RAW photos", Toast.LENGTH_SHORT).show()
            selectedIds = emptySet()
            reloadTrigger++
        } else {
            Toast.makeText(context, "Delete cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    val rawPhotos by produceState(initialValue = emptyList<RawPhotoItem>(), hasPermission, reloadTrigger) {
        value = if (hasPermission) {
            withContext(Dispatchers.IO) {
                queryRawPhotos(context)
            }
        } else {
            emptyList()
        }
    }

    LaunchedEffect(rawPhotos) {
        val visibleIds = rawPhotos.map { it.id }.toSet()
        selectedIds = selectedIds.intersect(visibleIds)
    }

    val selectedItems = rawPhotos.filter { it.id in selectedIds }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RAW Photos (${rawPhotos.size})") },
                actions = {
                    IconButton(onClick = { reloadTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            if (selectedItems.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedItems.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = {
                            val urisToDelete = selectedItems.map { it.uri }
                            if (urisToDelete.isEmpty()) return@Button

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val request = MediaStore.createDeleteRequest(
                                    context.contentResolver,
                                    urisToDelete
                                )
                                deleteIntentLauncher.launch(
                                    IntentSenderRequest.Builder(request.intentSender).build()
                                )
                            } else {
                                scope.launch(Dispatchers.IO) {
                                    var deletedCount = 0
                                    urisToDelete.forEach { uri ->
                                        try {
                                            deletedCount += context.contentResolver.delete(uri, null, null)
                                        } catch (_: RecoverableSecurityException) {
                                            // Android 10 may require per-item user action.
                                        } catch (_: SecurityException) {
                                            // Skip entries we are not allowed to delete directly.
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "Deleted $deletedCount RAW photos",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        selectedIds = emptySet()
                                        reloadTrigger++
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Delete")
                    }
                }
            }
        }
    ) { innerPadding ->
        if (!hasPermission) {
            PermissionBody(
                paddingValues = innerPadding,
                onGrantClick = { requestPermissionLauncher.launch(permission) }
            )
            return@Scaffold
        }

        if (rawPhotos.isEmpty()) {
            EmptyRawListBody(paddingValues = innerPadding)
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(rawPhotos, key = { it.id }) { item ->
                val checked = item.id in selectedIds
                RawPhotoRow(
                    item = item,
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        selectedIds = if (isChecked) {
                            selectedIds + item.id
                        } else {
                            selectedIds - item.id
                        }
                    },
                    onPreviewClick = { previewItem = item }
                )
                HorizontalDivider()
            }
        }
    }

    previewItem?.let { item ->
        PreviewDialog(
            item = item,
            onDismiss = { previewItem = null }
        )
    }
}

@Composable
private fun PermissionBody(
    paddingValues: PaddingValues,
    onGrantClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Allow media access so RawSweep can list your RAW files.",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGrantClick) {
            Text("Grant Permission")
        }
    }
}

@Composable
private fun EmptyRawListBody(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No RAW photos found",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun RawPhotoRow(
    item: RawPhotoItem,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onPreviewClick: () -> Unit
) {
    val context = LocalContext.current
    val takenText = remember(item.dateTakenMs) {
        val date = Date(item.dateTakenMs)
        DateFormat.format("yyyy-MM-dd HH:mm", date).toString()
    }
    val detailText = remember(item.sizeBytes, item.mimeType) {
        val sizeText = Formatter.formatFileSize(context, item.sizeBytes)
        "$sizeText • ${(item.mimeType ?: "raw").lowercase(Locale.ROOT)}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumbnail(uri = item.uri, size = 92)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName.ifBlank { "RAW_${item.id}" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = takenText,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onPreviewClick) {
            Icon(Icons.Default.Visibility, contentDescription = "Preview")
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PreviewDialog(
    item: RawPhotoItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text(item.displayName.ifBlank { "RAW_${item.id}" }) },
        text = {
            Column {
                Thumbnail(
                    uri = item.uri,
                    size = 1200,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.mimeType ?: "raw",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@Composable
private fun Thumbnail(
    uri: Uri,
    size: Int,
    modifier: Modifier = Modifier.size(size.dp)
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, size) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(uri, Size(size, size), null)
            } catch (_: Throwable) {
                null
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text("RAW")
        }
    }
}
