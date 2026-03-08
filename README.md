# Raw Sweep

Android app concept + starter implementation for managing RAW photos (DNG) captured on Pixel phones.

## What this app does

- Scans device media storage for RAW photos (`image/x-adobe-dng` / `.dng`).
- Shows all RAW photos in a single grid.
- Supports tap-to-preview.
- Supports multi-select (long press to start selecting).
- Bulk delete selected RAW files with Android's built-in delete confirmation flow.

## Tech stack

- Kotlin
- Jetpack Compose
- MediaStore APIs

## Important behavior

Deletion uses `MediaStore.createDeleteRequest(...)`, so Android shows an OS confirmation dialog before files are removed.

## Build

Open the project in Android Studio and run the `app` module on a device with RAW photos.

> Minimum SDK is set to 30 (Android 11).