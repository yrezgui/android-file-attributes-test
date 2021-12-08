package com.samples.fileattributestest

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import java.io.File
import kotlin.coroutines.resume

data class ImageResource(
    val uri: Uri,
    val filename: String,
    val size: Long,
    val mimeType: String,
    val lastModified: Long,
    val path: String,
)

object StorageUtils {
    /**
     * Check if app has [READ_EXTERNAL_STORAGE] and [WRITE_EXTERNAL_STORAGE] permissions
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ContextCompat.checkSelfPermission(
                context,
                READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(
                context,
                WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if app has [android.Manifest.permission.MANAGE_EXTERNAL_STORAGE] permission
     */
    fun hasManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            false
        }
    }

    /**
     * Logic to create a [MediaStore.createWriteRequest] for
     * [ActivityResultContracts.StartIntentSenderForResult]
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun editImageWithWriteRequest(context: Context, uri: Uri): IntentSenderRequest {
        return IntentSenderRequest.Builder(
            MediaStore.createWriteRequest(
                context.contentResolver,
                listOf(uri)
            )
        )
            .build()
    }

    fun editFileContent(context: Context, imageResource: ImageResource) {
        runBlocking {
            delay(2000L)
        }
        context.contentResolver.openOutputStream(imageResource.uri).use { outputStream ->
            val bitmap = Bitmap.createBitmap(768, 448, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val paint = Paint()
            paint.color = Color.GREEN
            paint.style = Paint.Style.FILL
            canvas.drawPaint(paint)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }

        runBlocking {
            val file = File(imageResource.path)
            scanPath(context, file.absolutePath, "image/jpeg")
            val fileLastModification = Instant.fromEpochMilliseconds(file.lastModified())
            println("Img Modified -> file($fileLastModification)")
        }
    }

    /**
     * Update the file last modification date with given path and re-scan it to update its metadata
     * on MediaStore
     */
    fun editLastModificationDate(context: Context, imageResource: ImageResource) {
        val file = File(imageResource.path)
        val now = Clock.System.now()
        file.setLastModified(now.toEpochMilliseconds())

        runBlocking {
            scanPath(context, file.absolutePath, "image/jpeg")
            val fileLastModification = Instant.fromEpochMilliseconds(file.lastModified())
            println("setLastModified file($now)")
            println("Img scanner  -> file($fileLastModification)")
        }
    }

    /**
     * Scan a path to update its metadata on MediaStore
     */
    suspend fun scanPath(context: Context, path: String, mimeType: String): Uri? {
        return suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(
                context,
                arrayOf(path),
                arrayOf(mimeType)
            ) { _, scannedUri ->
                if (scannedUri == null) {
                    continuation.cancel(Exception("File $path could not be scanned"))
                } else {
                    continuation.resume(scannedUri)
                }
            }
        }
    }

    /**
     * Query MediaStore to get the most recently added image
     */
    fun getMostRecentImage(context: Context): ImageResource {
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.ImageColumns._ID,
            MediaStore.Images.ImageColumns.DISPLAY_NAME,
            MediaStore.Images.ImageColumns.SIZE,
            MediaStore.Images.ImageColumns.MIME_TYPE,
            MediaStore.Images.ImageColumns.DATE_MODIFIED,
            MediaStore.Images.ImageColumns.DATA,
        )

        val cursor = context.contentResolver.query(
            imageCollection,
            projection,
            null,
            null,
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        ) ?: throw Exception("Query could not be executed")

        cursor.use {
            if (!cursor.moveToFirst()) {
                throw Exception("Could not find any images")
            }

            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val displayNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeTypeColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateModifiedColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

            return ImageResource(
                uri = ContentUris.withAppendedId(imageCollection, cursor.getLong(idColumn)),
                filename = cursor.getString(displayNameColumn),
                size = cursor.getLong(sizeColumn),
                mimeType = cursor.getString(mimeTypeColumn),
                lastModified = cursor.getLong(dateModifiedColumn),
                path = cursor.getString(dataColumn),
            )
        }
    }
}