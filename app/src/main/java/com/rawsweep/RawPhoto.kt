package com.rawsweep

import android.net.Uri

data class RawPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTaken: Long,
    val sizeBytes: Long
)
