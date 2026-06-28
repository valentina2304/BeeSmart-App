package com.example.beesmart.ui.inspections

import com.example.beesmart.network.models.InspectionPhotoResponse
import java.io.File

/**
 * Represents a photo that may be either local (a File) or remote (a server URL).
 */
sealed class PhotoItem {
    data class Local(
        val file: File,
        val tempId: String,
        var description: String? = null
    ) : PhotoItem()

    data class Remote(
        val photo: InspectionPhotoResponse
    ) : PhotoItem()

    /** Stable key for tracking per-photo AI analysis within a session. */
    val key: String
        get() = when (this) {
            is Local -> "local:$tempId"
            is Remote -> "remote:${photo.id}"
        }
}
