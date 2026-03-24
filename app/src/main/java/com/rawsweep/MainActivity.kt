package com.rawsweep

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.rawsweep.ui.navigation.AppNavGraph
import com.rawsweep.ui.theme.RawSweepTheme
import com.rawsweep.viewmodel.GalleryViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels()
    private var hasPermission by mutableStateOf(false)
    private var pendingDeleteCount: Int = 0
    private var pendingDeletedFileNames: List<String> = emptyList()
    private var pendingOpenGooglePhotosCleanup: Boolean = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            viewModel.loadPhotos()
        }
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.onDeleteCompleted(pendingDeleteCount)
            if (pendingOpenGooglePhotosCleanup && pendingDeleteCount > 0) {
                openGooglePhotosForManualCleanup(pendingDeletedFileNames)
            }
        } else {
            Toast.makeText(this, "Delete cancelled", Toast.LENGTH_SHORT).show()
        }
        clearPendingDeleteContext()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hasPermission = checkPermission()

        setContent {
            RawSweepTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    AppNavGraph(
                        navController = navController,
                        viewModel = viewModel,
                        hasPermission = hasPermission,
                        onRequestPermission = { requestPermission() },
                        onDeleteRequest = { alsoDeleteFromGooglePhotos ->
                            handleDeleteRequest(alsoDeleteFromGooglePhotos)
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val newPermission = checkPermission()
        if (!newPermission && hasPermission) {
            viewModel.resetLoadState()
        }
        if (newPermission && !hasPermission) {
            hasPermission = true
            viewModel.loadPhotos()
        }
        hasPermission = newPermission
    }

    private fun checkPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }

    private fun handleDeleteRequest(alsoDeleteFromGooglePhotos: Boolean) {
        val state = viewModel.uiState.value
        val selectedPhotos = state.photos.filter { state.selectedIds.contains(it.id) }
        val uris = selectedPhotos.map { it.uri }

        if (uris.isEmpty()) return

        pendingDeleteCount = uris.size
        pendingDeletedFileNames = selectedPhotos.map { it.displayName }
        pendingOpenGooglePhotosCleanup = alsoDeleteFromGooglePhotos

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = MediaStore.createDeleteRequest(contentResolver, uris)
                val request = IntentSenderRequest.Builder(intent.intentSender).build()
                deleteLauncher.launch(request)
            } catch (e: Exception) {
                clearPendingDeleteContext()
                Toast.makeText(this, "Failed to create delete request: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            viewModel.requestDeleteSelected { deleted ->
                if (deleted > 0 && alsoDeleteFromGooglePhotos) {
                    openGooglePhotosForManualCleanup(pendingDeletedFileNames)
                }
                clearPendingDeleteContext()
            }
        }
    }

    private fun clearPendingDeleteContext() {
        pendingDeleteCount = 0
        pendingDeletedFileNames = emptyList()
        pendingOpenGooglePhotosCleanup = false
    }

    private fun openGooglePhotosForManualCleanup(fileNames: List<String>) {
        val dedupedNames = fileNames.distinct()
        if (dedupedNames.isNotEmpty()) {
            val clipboard = getSystemService(ClipboardManager::class.java)
            val clipboardText = dedupedNames.joinToString(separator = "\n")
            clipboard.setPrimaryClip(ClipData.newPlainText("Deleted RAW file names", clipboardText))
        }

        val launched = try {
            val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.apps.photos")
            if (launchIntent != null) {
                startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }

        if (!launched) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://photos.google.com"))
            startActivity(fallbackIntent)
        }

        Toast.makeText(
            this,
            "Google Photos opened. Deleted RAW file names copied to clipboard. Search and delete cloud copies.",
            Toast.LENGTH_LONG
        ).show()
    }
}
