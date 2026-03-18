package com.rawsweep.data

import android.net.Uri

data class RawPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val dateAdded: Long,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val relativePath: String,
    val mimeType: String,
) {
    val formattedSize: String
        get() {
            val mb = size / (1024.0 * 1024.0)
            return if (mb >= 1.0) "%.1f MB".format(mb)
            else "%.0f KB".format(size / 1024.0)
        }

    val isRawDng: Boolean
        get() = mimeType.equals("image/x-adobe-dng", ignoreCase = true) ||
                displayName.endsWith(".dng", ignoreCase = true)
}
