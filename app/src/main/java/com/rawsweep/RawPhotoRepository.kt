package com.rawsweep

import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore

class RawPhotoRepository(
    private val contentResolver: ContentResolver
) {
    fun loadRawPhotos(): List<RawPhoto> {
        val imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE
        )

        val selection = "(${MediaStore.Images.Media.MIME_TYPE} IN (?, ?) OR LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) LIKE ?)"
        val selectionArgs = arrayOf("image/x-adobe-dng", "image/dng", "%.dng")
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        val photos = mutableListOf<RawPhoto>()
        contentResolver.query(
            imageCollection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                photos += RawPhoto(
                    id = id,
                    uri = ContentUris.withAppendedId(imageCollection, id),
                    displayName = cursor.getString(nameColumn) ?: "RAW_$id",
                    dateTaken = cursor.getLong(dateTakenColumn),
                    sizeBytes = cursor.getLong(sizeColumn)
                )
            }
        }
        return photos
    }

    fun createDeleteRequest(uris: Collection<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        return MediaStore.createDeleteRequest(contentResolver, uris.toList()).intentSender
    }
}
