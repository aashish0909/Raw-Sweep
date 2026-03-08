package com.rawsweep.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RawPhotoRepository(private val contentResolver: ContentResolver) {

    suspend fun loadRawPhotos(): List<RawPhoto> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<RawPhoto>()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.MIME_TYPE,
        )

        // Google Pixel stores RAW photos as DNG (image/x-adobe-dng)
        val selection = "${MediaStore.Images.Media.MIME_TYPE} = ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("image/x-adobe-dng", "%.dng")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(collection, id)

                photos.add(
                    RawPhoto(
                        id = id,
                        uri = contentUri,
                        displayName = cursor.getString(nameCol) ?: "unknown.dng",
                        size = cursor.getLong(sizeCol),
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateTaken = cursor.getLong(dateTakenCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        relativePath = cursor.getString(pathCol) ?: "",
                        mimeType = cursor.getString(mimeCol) ?: "image/x-adobe-dng",
                    )
                )
            }
        }

        photos
    }

    fun getDeleteRequest(uris: List<Uri>): android.app.PendingIntent? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(contentResolver, uris).let { intent ->
                android.app.PendingIntent.getActivity(
                    null, 0, intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
            }
        } else {
            null
        }
    }

    suspend fun deletePhotosLegacy(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (uri in uris) {
            try {
                deleted += contentResolver.delete(uri, null, null)
            } catch (_: SecurityException) {
                // On Android 10 (Q), a RecoverableSecurityException is thrown;
                // this is handled at the Activity level via the intent sender.
            }
        }
        deleted
    }
}
