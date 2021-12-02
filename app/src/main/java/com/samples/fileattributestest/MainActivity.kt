package com.samples.fileattributestest

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns.DATE_ADDED
import android.text.format.Formatter.formatShortFileSize
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.samples.fileattributestest.ui.theme.FileAttributesTestTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    @ExperimentalMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FileAttributesTestTheme {
                val context = LocalContext.current
                var hasStoragePermission by remember { mutableStateOf(hasStoragePermission()) }

                val requestPermission =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                        hasStoragePermission = it
                    }

                var mostRecentImage by remember { mutableStateOf<ImageResource?>(null) }

                val intentSenderLauncher =
                    rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->
                        if (result.resultCode == RESULT_OK) {
                            mostRecentImage?.let { editLastModificationDate(it.path) }
                        }
                    }

                Surface(Modifier.padding(top = 10.dp), color = MaterialTheme.colors.background) {
                    Column {
                        ListItem(trailing = { Text(hasStoragePermission.toString()) }) {
                            Button(onClick = { requestPermission.launch(READ_EXTERNAL_STORAGE) }) {
                                Text("Request read storage permission")
                            }
                        }
                        Divider()

                        ListItem {
                            Button(
                                enabled = hasStoragePermission,
                                onClick = { mostRecentImage = getMostRecentImage() }
                            ) {
                                Text("Get most recent image")
                            }
                        }
                        Divider()

                        ListItem {
                            /**
                             * On Android R+, we use [MediaStore.createWriteRequest] to request
                             * user's consent to be able to edit the file, on Android Q and lower,
                             * we don't have to request that consent to edit if we have the
                             * [READ_EXTERNAL_STORAGE] permission
                             */
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                Button(
                                    enabled = mostRecentImage != null,
                                    onClick = {
                                        intentSenderLauncher.launch(
                                            editImageWithWriteRequest(
                                                mostRecentImage!!.uri
                                            )
                                        )
                                    }
                                ) {
                                    Text("Click to edit last item (write request)")
                                }
                            } else {
                                Button(
                                    enabled = mostRecentImage != null,
                                    onClick = {
                                        editLastModificationDate(mostRecentImage!!.path)
                                    }
                                ) {
                                    Text("Click to edit last item (without write request)")
                                }
                            }
                        }
                        Divider()
                        Spacer(Modifier.height(20.dp))

                        /**
                         * Display image metadata once it has been fetched
                         */
                        mostRecentImage?.let { image ->
                            ListItem(trailing = { Text("Filename") }) {
                                Text(image.filename)
                            }
                            Divider()

                            ListItem(trailing = { Text("Uri") }) {
                                Text(image.uri.toString())
                            }
                            Divider()

                            ListItem(trailing = { Text("Size") }) {
                                Text(formatShortFileSize(context, image.size))
                            }
                            Divider()

                            ListItem(trailing = { Text("Mime Type") }) {
                                Text(image.mimeType)
                            }
                            Divider()

                            ListItem(trailing = { Text("Last Modified") }) {
                                Text(image.lastModified.toString())
                            }
                            Divider()

                            ListItem(trailing = { Text("Path") }) {
                                Text(image.path)
                            }
                            Divider()
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if app has [READ_EXTERNAL_STORAGE] permission
     */
    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Logic to create a [MediaStore.createWriteRequest] for
     * [ActivityResultContracts.StartIntentSenderForResult]
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun editImageWithWriteRequest(uri: Uri): IntentSenderRequest {
        return IntentSenderRequest.Builder(
            MediaStore.createWriteRequest(
                contentResolver,
                listOf(uri)
            )
        )
            .build()
    }

    /**
     * Update the file last modification date with given path and re-scan it to update its metadata
     * on MediaStore
     */
    private fun editLastModificationDate(path: String) {
        val file = File(path)
        val now = System.currentTimeMillis()
        file.setLastModified(now)

        runBlocking {
            scanPath(file.absolutePath, "image/jpeg")
            println("setLastModified: now($now) || lastModified(${file.lastModified()})")
        }
    }

    /**
     * Scan a path to update its metadata on MediaStore
     */
    private suspend fun scanPath(path: String, mimeType: String): Uri? {
        return suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(
                this,
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

    data class ImageResource(
        val uri: Uri,
        val filename: String,
        val size: Long,
        val mimeType: String,
        val lastModified: Long,
        val path: String,
    )

    /**
     * Query MediaStore to get the most recently added image
     */
    private fun getMostRecentImage(): ImageResource {
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

        val cursor = contentResolver.query(
            imageCollection,
            projection,
            null,
            null,
            "$DATE_ADDED DESC"
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