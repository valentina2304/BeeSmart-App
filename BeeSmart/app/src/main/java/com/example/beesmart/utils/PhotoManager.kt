package com.example.beesmart.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class for handling photos:
 * - Creating temporary files for the camera
 * - Compressing images
 * - Automatic EXIF-based rotation
 * - Converting to Base64 for upload
 */
class PhotoManager(private val context: Context) {

    companion object {
        private const val PHOTO_QUALITY = 85 // JPEG quality (0-100)
        private const val MAX_IMAGE_DIMENSION = 1920 // Max width/height in pixels
        private const val AI_PHOTO_QUALITY = 72
        private const val AI_MAX_IMAGE_DIMENSION = 1536
    }

    /**
     * Creates a temporary file for the camera photo.
     * Returns the Uri to use with the camera intent.
     */
    fun createTempPhotoFile(): Pair<File, Uri> {
        // Timestamp for a unique file name
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val photoFileName = "INSPECTION_${timestamp}.jpg"

        // Store in the cache directory
        val storageDir = File(context.cacheDir, "photos").apply {
            if (!exists()) mkdirs()
        }

        val photoFile = File(storageDir, photoFileName)

        // Create the URI via FileProvider
        val photoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )

        return Pair(photoFile, photoUri)
    }

    /**
     * Processes the photo: rotation, resizing, compression.
     * Returns the processed file.
     */
    fun processPhoto(sourceFile: File): File {
        // Read the image
        var bitmap = decodeBitmapOrThrow(sourceFile)

        // Fix orientation based on EXIF
        bitmap = rotateImageIfRequired(bitmap, sourceFile.absolutePath)

        // Resize if too large
        bitmap = resizeImage(bitmap, MAX_IMAGE_DIMENSION)

        // Create the processed file
        val storageDir = File(context.filesDir, "inspection_photos").apply {
            if (!exists()) mkdirs()
        }
        val processedFile = File(storageDir, "processed_${sourceFile.name}")

        // Save with compression
        FileOutputStream(processedFile).use { out ->
            val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, out)
            require(compressed) { "Fotografia nu a putut fi comprimata" }
        }
        require(processedFile.length() > 0L) { "Fotografia procesata este goala" }

        // Free the memory
        bitmap.recycle()

        return processedFile
    }

    /**
     * Converts the image file to Base64 for upload.
     */
    fun imageToBase64(file: File): String {
        return imageToBase64(file, MAX_IMAGE_DIMENSION, PHOTO_QUALITY)
    }

    /**
     * Creates a compact version for AI analysis.
     *
     * The stored photo stays at higher quality, but DeepBee receives a
     * predictably sized image so it doesn't hit the timeout on phones
     * whose cameras produce large files.
     */
    fun imageToAnalysisBase64(file: File): String {
        return imageToBase64(file, AI_MAX_IMAGE_DIMENSION, AI_PHOTO_QUALITY)
    }

    private fun imageToBase64(file: File, maxDimension: Int, quality: Int): String {
        var bitmap = decodeBitmapOrThrow(file)
        bitmap = rotateImageIfRequired(bitmap, file.absolutePath)
        bitmap = resizeImage(bitmap, maxDimension)

        val out = ByteArrayOutputStream()
        val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bitmap.recycle()
        require(compressed) { "Fotografia nu a putut fi comprimata" }

        val bytes = out.toByteArray()
        require(bytes.isNotEmpty()) { "Fotografia procesata este goala" }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Saves the Base64 image as a local file.
     * Useful for caching after downloading from the server.
     */
    fun base64ToFile(base64: String, fileName: String): File {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val storageDir = File(context.filesDir, "photos").apply {
            if (!exists()) mkdirs()
        }
        val file = File(storageDir, fileName)
        file.writeBytes(bytes)
        return file
    }

    /**
     * Rotates the image based on EXIF information.
     */
    private fun rotateImageIfRequired(bitmap: Bitmap, imagePath: String): Bitmap {
        val exif = ExifInterface(imagePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
            else -> bitmap
        }
    }

    private fun decodeBitmapOrThrow(file: File): Bitmap {
        require(file.exists() && file.length() > 0L) { "Fotografia nu exista sau este goala" }
        return BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalArgumentException("Fotografia nu poate fi citita ca imagine")
    }

    /**
     * Rotates the bitmap by the specified angle.
     */
    private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotated
    }

    /**
     * Resizes the image if it exceeds the maximum dimension.
     */
    private fun resizeImage(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // If the image is already under the limit, return it as is
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        // Compute the new size while keeping the aspect ratio
        val scale = if (width > height) {
            maxDimension.toFloat() / width
        } else {
            maxDimension.toFloat() / height
        }

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        bitmap.recycle()
        return resized
    }

    /**
     * Deletes old temporary photos (cleanup).
     */
    fun cleanupTempPhotos() {
        val tempDir = File(context.cacheDir, "photos")
        if (tempDir.exists()) {
            val files = tempDir.listFiles() ?: return
            val threshold = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // 24h

            files.forEach { file ->
                if (file.lastModified() < threshold) {
                    file.delete()
                }
            }
        }
    }

    /**
     * Returns the file size in KB.
     */
    fun getFileSizeKB(file: File): Long {
        return file.length() / 1024
    }
}
