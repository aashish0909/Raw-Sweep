# Toolbox

A minimal Android utility app — a single hub for niche tools on your phone. Open the app, pick a tool, get things done.

## Tools

### Raw Photos
Find, preview, and batch-delete RAW (DNG) photos. When RAW mode is left on by accident, your Pixel saves both a JPEG and a DNG file for every shot — this tool lets you clean up the DNG files in bulk while keeping your JPEGs intact.

- **Automatic RAW detection** — Scans your device for all DNG files using the MediaStore API
- **Gallery grid** — Displays RAW photo thumbnails in a responsive grid with file sizes
- **Full-screen preview** — Pinch-to-zoom and swipe between photos with a horizontal pager
- **Photo details** — Bottom sheet showing file name, resolution, size, date taken, and path
- **Multi-select** — Long-press to enter selection mode, tap to toggle, select/deselect all
- **Batch delete** — Delete multiple RAW files at once using Android's `MediaStore.createDeleteRequest` for safe, system-mediated deletion (Android 11+)
- **Sort options** — Sort by date (newest/oldest), size (largest/smallest), or name (A-Z/Z-A)
- **Pull to refresh** — Swipe down to rescan for new RAW files

## Design

- **Material 3 + Dynamic Color** — Follows your Pixel's wallpaper-based theme (Android 12+)
- **Edge-to-edge** — Modern full-screen layout with proper inset handling
- **Minimal and usable** — Clean home screen with tool cards, no clutter

## Requirements

- Android 10 (API 29) or higher
- Google Pixel device (or any device that produces `.dng` RAW files)

## Build

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

A pre-built debug APK is available in the `apk/` directory.

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
    │   └── NavGraph.kt          # Navigation setup (home → gallery → preview)
    ├── screens/
    │   ├── HomeScreen.kt        # Main hub with tool cards
    │   ├── GalleryScreen.kt     # RAW photo grid with permission handling
    │   └── PreviewScreen.kt     # Full-screen zoomable preview with pager
    └── components/
        ├── PhotoGridItem.kt     # Individual photo card in the grid
        ├── SortMenu.kt          # Sort dropdown menu
        ├── DeleteConfirmDialog.kt # Delete confirmation dialog
        └── EmptyState.kt        # Empty state placeholder
```

## How It Works

1. **Home** — Open the app to see available tools. Tap a tool card to launch it.

2. **Raw Photos → Permission** — On first use, the tool requests `READ_MEDIA_IMAGES` (Android 13+) or `READ_EXTERNAL_STORAGE` (Android 10-12) to access your photos.

3. **Raw Photos → Scanning** — The tool queries `MediaStore.Images` for files with MIME type `image/x-adobe-dng` or the `.dng` extension.

4. **Raw Photos → Browsing** — RAW photos are displayed in a scrollable grid. Tap a photo to open a full-screen preview with pinch-to-zoom and swipe navigation.

5. **Raw Photos → Selecting** — Long-press any photo to enter selection mode. Tap to toggle selection, or use "Select All". The bottom bar shows the count and total size.

6. **Raw Photos → Deleting** — Tap "Delete RAW" to see a confirmation dialog. On Android 11+, the system shows its own delete confirmation. Only DNG files are deleted — your JPEGs remain untouched.

## Tech Stack

- **Kotlin** with **Jetpack Compose** for declarative UI
- **Material 3** design system with dynamic theming
- **Coil** for efficient image loading and caching
- **Navigation Compose** for screen transitions
- **MediaStore API** for querying and deleting media files
- **ViewModel** + **StateFlow** for reactive state management

## License

MIT
