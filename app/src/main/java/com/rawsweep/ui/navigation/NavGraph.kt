package com.rawsweep.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rawsweep.ui.screens.GalleryScreen
import com.rawsweep.ui.screens.HomeScreen
import com.rawsweep.ui.screens.PreviewScreen
import com.rawsweep.viewmodel.GalleryViewModel

object Routes {
    const val HOME = "home"
    const val GALLERY = "gallery"
    const val PREVIEW = "preview/{photoId}"

    fun preview(photoId: Long) = "preview/$photoId"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: GalleryViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToRawPhotos = {
                    navController.navigate(Routes.GALLERY)
                },
            )
        }

        composable(Routes.GALLERY) {
            GalleryScreen(
                viewModel = viewModel,
                hasPermission = hasPermission,
                onRequestPermission = onRequestPermission,
                onPhotoClick = { photoId ->
                    navController.navigate(Routes.preview(photoId))
                },
                onDeleteRequest = onDeleteRequest,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PREVIEW,
            arguments = listOf(
                navArgument("photoId") { type = NavType.LongType }
            ),
        ) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getLong("photoId") ?: return@composable
            PreviewScreen(
                viewModel = viewModel,
                initialPhotoId = photoId,
                onBack = { navController.popBackStack() },
                onDeleteRequest = onDeleteRequest,
            )
        }
    }
}
