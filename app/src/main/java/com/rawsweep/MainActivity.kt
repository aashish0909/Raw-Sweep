package com.rawsweep

import android.Manifest
import android.content.pm.PackageManager
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
        viewModel.onDeleteRequestHandled()
        if (result.resultCode == RESULT_OK) {
            val count = viewModel.uiState.value.selectedIds.size
            viewModel.onDeleteCompleted(count)
        } else {
            Toast.makeText(this, "Delete cancelled", Toast.LENGTH_SHORT).show()
        }
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
                        onDeleteRequest = { handleDeleteRequest() },
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

    private fun handleDeleteRequest() {
        val state = viewModel.uiState.value
        val uris = state.photos
            .filter { state.selectedIds.contains(it.id) }
            .map { it.uri }

        if (uris.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = MediaStore.createDeleteRequest(contentResolver, uris)
                val request = IntentSenderRequest.Builder(intent.intentSender).build()
                deleteLauncher.launch(request)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to create delete request: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            viewModel.requestDeleteSelected()
        }
    }
}
