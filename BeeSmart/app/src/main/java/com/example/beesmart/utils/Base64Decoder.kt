package com.example.beesmart.utils

import android.util.Base64
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer

/**
 * Custom Coil Fetcher that decodes Base64 Data URIs.
 * Format: "data:image/jpeg;base64,/9j/4AAQSkZJRg..."
 */
class Base64Fetcher(
    private val data: String,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        android.util.Log.d("Base64Fetcher", "Fetching Data URI, length: ${data.length}")

        // Extract the Base64 part from the Data URI
        val base64Data = when {
            data.startsWith("data:image/") -> {
                val base64Index = data.indexOf("base64,")
                if (base64Index != -1) {
                    data.substring(base64Index + 7) // Skip "base64,"
                } else {
                    android.util.Log.e("Base64Fetcher", "Invalid Data URI format: no 'base64,' marker")
                    throw IllegalArgumentException("Invalid Data URI format")
                }
            }
            else -> data // Assume it's already Base64
        }

        // Decode Base64
        val imageBytes = try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            android.util.Log.d("Base64Fetcher", "Successfully decoded Base64, image size: ${bytes.size} bytes")
            bytes
        } catch (e: Exception) {
            android.util.Log.e("Base64Fetcher", "Failed to decode Base64: ${e.message}", e)
            throw IllegalArgumentException("Failed to decode Base64: ${e.message}", e)
        }

        // Build a BufferedSource from the bytes
        val buffer = Buffer().write(imageBytes)

        return SourceResult(
            source = ImageSource(
                source = buffer,
                context = options.context
            ),
            mimeType = "image/jpeg",
            dataSource = DataSource.MEMORY
        )
    }

    class Factory : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            // Only handle String values that are Base64 Data URIs
            if (data !is String) return null

            return if (data.startsWith("data:image/") && data.contains("base64,")) {
                android.util.Log.d("Base64Fetcher", "Creating fetcher for Data URI of length: ${data.length}")
                Base64Fetcher(data, options)
            } else {
                null
            }
        }
    }
}
