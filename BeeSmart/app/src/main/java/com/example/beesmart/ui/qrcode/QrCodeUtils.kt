package com.example.beesmart.ui.qrcode

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Downloads
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale

object QrCodeUtils {

    fun generateQrBitmap(content: String, size: Int = 768): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        return bitMatrix.toBitmap()
    }

    suspend fun saveToGallery(context: Context, bitmap: Bitmap, hiveName: String): Uri = withContext(Dispatchers.IO) {
        val displayName = "Hive_${sanitizeFileName(hiveName)}_${System.currentTimeMillis()}.png"
        val targetCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_PICTURES}/BeeSmart")
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(targetCollection, contentValues)
            ?: throw IllegalStateException("Nu s-a putut crea fișierul în MediaStore")

        resolver.openOutputStream(uri)?.use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw IllegalStateException("Eșec la salvarea imaginii QR")
            }
        } ?: throw IllegalStateException("Nu s-a putut deschide stream-ul de ieșire")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        uri
    }

    fun shareBitmap(context: Context, bitmap: Bitmap, hiveName: String) {
        val folder = File(context.cacheDir, "qr")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val file = File(folder, "Hive_${sanitizeFileName(hiveName)}.png")
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IllegalStateException("Nu s-a putut scrie fișierul temporar pentru QR")
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Distribuie codul QR"))
    }

    suspend fun exportQrPdf(
        context: Context,
        bitmap: Bitmap,
        hiveName: String,
        apiaryName: String,
        qrLink: String
    ): Uri = withContext(Dispatchers.IO) {
        val displayName = "Hive_${sanitizeFileName(hiveName)}_${System.currentTimeMillis()}.pdf"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(collection, contentValues)
                ?: throw IllegalStateException("Nu s-a putut crea fișierul PDF")

            writePdfToUri(context, uri, bitmap, hiveName, apiaryName, qrLink)

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            uri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                throw IllegalStateException("Nu s-a putut crea directorul Descărcări")
            }
            val file = File(downloadsDir, displayName)
            FileOutputStream(file).use { stream ->
                writePdfToStream(stream, bitmap, hiveName, apiaryName, qrLink)
            }
            Uri.fromFile(file)
        }
    }

    private fun writePdfToUri(
        context: Context,
        uri: Uri,
        bitmap: Bitmap,
        hiveName: String,
        apiaryName: String,
        qrLink: String
    ) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            writePdfToStream(stream, bitmap, hiveName, apiaryName, qrLink)
        } ?: throw IllegalStateException("Nu s-a putut salva PDF-ul")
    }

    private fun writePdfToStream(
        stream: OutputStream,
        bitmap: Bitmap,
        hiveName: String,
        apiaryName: String,
        qrLink: String
    ) {
        val pdf = PdfDocument()
        val pageWidth = 612
        val pageHeight = 792
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas
        val margin = 36f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.BLACK
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f
            color = Color.DKGRAY
        }

        canvas.drawText("Stup: $hiveName", margin, 60f, titlePaint)
        canvas.drawText("Stupină: $apiaryName", margin, 90f, bodyPaint)
        canvas.drawText("Link QR: $qrLink", margin, 120f, bodyPaint)
        canvas.drawText("Scanează cu aplicația BeeSmart pentru detalii.", margin, 150f, bodyPaint)

        val qrSize = 360f
        val left = (pageWidth - qrSize) / 2f
        val top = 200f
        val rect = RectF(left, top, left + qrSize, top + qrSize)
        canvas.drawBitmap(bitmap, null, rect, null)

        canvas.drawText("Tipărește și lipește pe stup pentru identificare rapidă.", margin, top + qrSize + 60f, bodyPaint)

        pdf.finishPage(page)
        pdf.writeTo(stream)
        pdf.close()
    }

    private fun BitMatrix.toBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    private fun sanitizeFileName(input: String): String {
        return input.replace("[^A-Za-z0-9_]".toRegex(), "_")
            .lowercase(Locale.getDefault())
            .take(40)
            .ifEmpty { "hive" }
    }
}
