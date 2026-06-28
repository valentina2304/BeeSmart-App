package com.example.beesmart.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photoManager = PhotoManager(context)

    @Test
    fun imageToBase64_compactsLargeImagesForUpload() {
        val source = createLargeJpeg("upload-large.jpg", width = 2600, height = 1800)

        val encoded = photoManager.imageToBase64(source)
        val decoded = Base64.decode(encoded, Base64.NO_WRAP)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(ByteArrayInputStream(decoded), null, options)

        assertTrue(decoded.size < source.length())
        assertTrue(options.outWidth <= 1920)
        assertTrue(options.outHeight <= 1920)
    }

    @Test
    fun imageToAnalysisBase64_usesAiSpecificSizeLimit() {
        val source = createLargeJpeg("analysis-large.jpg", width = 2600, height = 1800)

        val encoded = photoManager.imageToAnalysisBase64(source)
        val decoded = Base64.decode(encoded, Base64.NO_WRAP)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(ByteArrayInputStream(decoded), null, options)

        assertTrue(options.outWidth <= 1536)
        assertTrue(options.outHeight <= 1536)
    }

    private fun createLargeJpeg(name: String, width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (x * 255 / width)
                val g = (y * 255 / height)
                val b = ((x xor y) and 0xFF)
                pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        val file = File(context.cacheDir, name)
        FileOutputStream(file).use { out ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out))
        }
        bitmap.recycle()
        return file
    }
}
