# Raw Sweep

An Android app for Google Pixel users to quickly find, preview, and batch-delete RAW (DNG) photos. When RAW mode is left on by accident, your Pixel saves both a JPEG and a DNG file for every shot — Raw Sweep lets you clean up the DNG files in bulk while keeping your JPEGs intact.

## Features

- **Automatic RAW detection** — Scans your device for all DNG files using the MediaStore API
- **Gallery grid** — Displays RAW photo thumbnails in a responsive grid with file sizes
- **Full-screen preview** — Pinch-to-zoom and swipe between photos with a horizontal pager
- **Photo details** — Bottom sheet showing file name, resolution, size, date taken, and path
- **Multi-select** — Long-press to enter selection mode, tap to toggle, select/deselect all
- **Batch delete** — Delete multiple RAW files at once with a confirmation dialog; uses Android's `MediaStore.createDeleteRequest` for safe, system-mediated deletion (Android 11+)
- **Sort options** — Sort by date (newest/oldest), size (largest/smallest), or name (A-Z/Z-A)
- **Pull to refresh** — Swipe down to rescan for new RAW files
- **Material 3 + Dynamic Color** — Follows your Pixel's wallpaper-based theme (Android 12+)
- **Edge-to-edge** — Modern full-screen layout with proper inset handling

## Screenshots

*Install the app on your Pixel to see it in action — it requires real DNG files to display.*

## Requirements

- Android 10 (API 29) or higher
- Google Pixel device (or any device that produces `.dng` RAW files)

## Build

1. Clone the repository
2. Open the project in [Android Studio](https://developer.android.com/studio) (Hedgehog or newer recommended)
3. Sync Gradle and let it download dependencies
4. Run on a connected Pixel device or emulator

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Project Structure

```
app/src/main/java/com/rawsweep/
├── MainActivity.kt              # Entry point, permissions, delete intent handling
├── data/
│   ├── RawPhoto.kt              # Data model for a RAW photo
│   ├── RawPhotoRepository.kt    # MediaStore queries and delete operations
│   └── SortOption.kt            # Sort option enum
├── viewmodel/
│   └── GalleryViewModel.kt      # UI state management and business logic
└── ui/
    ├── theme/
    │   └── Theme.kt             # Material 3 theme with dynamic color support
    ├── navigation/
    │   └── NavGraph.kt          # Navigation setup (gallery → preview)
    ├── screens/
    │   ├── GalleryScreen.kt     # Main grid screen with top/bottom bars
    │   └── PreviewScreen.kt     # Full-screen zoomable preview with pager
    └── components/
        ├── PhotoGridItem.kt     # Individual photo card in the grid
        ├── SortMenu.kt          # Sort dropdown menu
        ├── DeleteConfirmDialog.kt # Delete confirmation dialog
        └── EmptyState.kt        # Empty state placeholder
```

## How It Works

1. **Permission** — On first launch, the app requests `READ_MEDIA_IMAGES` (Android 13+) or `READ_EXTERNAL_STORAGE` (Android 10-12) to access your photos.

2. **Scanning** — The app queries `MediaStore.Images` for files with MIME type `image/x-adobe-dng` or the `.dng` extension, which is the RAW format used by Google Pixel cameras.

3. **Browsing** — RAW photos are displayed in a scrollable grid. Each thumbnail shows the file size. Tap a photo to open a full-screen preview with pinch-to-zoom and swipe navigation.

4. **Selecting** — Long-press any photo to enter selection mode. Tap photos to toggle selection, or use the "Select All" button. The bottom bar shows the count and total size of selected files.

5. **Deleting** — Tap "Delete RAW" to see a confirmation dialog. On Android 11+, the system shows its own delete confirmation for safety. Only the selected DNG files are deleted — your JPEG copies remain untouched.

## Tech Stack

- **Kotlin** with **Jetpack Compose** for declarative UI
- **Material 3** design system with dynamic theming
- **Coil** for efficient image loading and caching
- **Navigation Compose** for screen transitions
- **MediaStore API** for querying and deleting media files
- **ViewModel** + **StateFlow** for reactive state management

## License

MIT
