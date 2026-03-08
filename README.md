# RawSweep

RawSweep is an Android app that helps you find and clean up RAW photos (like `.dng`) in one place.

This is useful on Pixel phones where shooting in RAW + JPEG creates two files per shot. If you forget to disable RAW mode, RawSweep lets you quickly remove only the RAW copies in bulk.

## Features (MVP)

- Scans `MediaStore` for RAW image formats (`.dng`, `.cr2`, `.nef`, etc.)
- Shows all detected RAW photos in a single list
- Provides thumbnail preview for each item
- Supports multi-select
- Deletes selected RAW files in one action
  - On Android 11+ it uses the system delete confirmation flow

## Project structure

- `app/src/main/java/com/rawsweep/MainActivity.kt` - app UI + RAW query + delete flow
- `app/src/main/AndroidManifest.xml` - app permissions and activity

## Run locally

1. Open in Android Studio (latest stable).
2. Let Gradle sync.
3. Run on a physical device (recommended) or emulator with media files.
4. Grant media permission when prompted.

## Notes

- The app requests read permission (`READ_MEDIA_IMAGES` on Android 13+, `READ_EXTERNAL_STORAGE` on older versions).
- Delete behavior on Android 10 can be more restrictive than Android 11+ due to scoped storage limitations.