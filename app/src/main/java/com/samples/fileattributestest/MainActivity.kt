package com.samples.fileattributestest

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns.DATE_ADDED
import android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.samples.fileattributestest.ui.theme.FileAttributesTestTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

data class ImageResource(
    val uri: Uri,
    val filename: String,
    val size: Long,
    val mimeType: String,
    val path: String?,
)

class MainActivity : ComponentActivity() {
    @ExperimentalMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FileAttributesTestTheme {
                var hasReadExternalStoragePermission by remember {
                    mutableStateOf(
                        hasReadExternalStoragePermission()
                    )
                }

                val requestPermission =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                        hasReadExternalStoragePermission = it
                    }

                val hasManageExternalStoragePermission by remember {
                    mutableStateOf(
                        hasManageExternalStoragePermission()
                    )
                }


                var lastImage by remember { mutableStateOf<ImageResource?>(null) }

                val intentSenderLauncher =
                    rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->
                        if (result.resultCode == RESULT_OK) {
                            lastImage?.let { editLastModificationDate(it) }
                        }
                    }

                Surface(Modifier.padding(top = 10.dp), color = MaterialTheme.colors.background) {
                    Column {
                        ListItem(trailing = { Text(hasReadExternalStoragePermission.toString()) }) {
                            Button(onClick = { requestPermission.launch(READ_EXTERNAL_STORAGE) }) {
                                Text("Request read storage permission")
                            }
                        }
                        Divider()

                        ListItem(trailing = { Text(hasManageExternalStoragePermission.toString()) }) {
                            Button(onClick = { requestManageExternalStoragePermission() }) {
                                Text("Request manage storage permission")
                            }
                        }
                        Divider()

                        ListItem {
                            Button(onClick = { lastImage = getImageResources(limit = 1).first() }) {
                                Text("Get last media")
                            }
                        }
                        Divider()

                        ListItem {
                            Button(
                                enabled = lastImage != null,
                                onClick = {
                                    val uri = lastImage!!.uri
                                    intentSenderLauncher.launch(editMediaWithWriteRequest(listOf(uri)))
                                }
                            ) {
                                Text("Click to edit last item (write request)")
                            }
                        }
                        Divider()

                        ListItem {
                            Button(onClick = { editLastModificationDate(lastImage!!) }) {
                                Text("Click to edit last item (Java IO)")
                            }
                        }
                        Divider()

                        if (lastImage != null) {
                            ListItem {
                                Text(lastImage.toString())
                            }
                            Divider()
                        }
                    }
                }
            }
        }
    }

    private fun hasReadExternalStoragePermission(): Boolean {
        return checkSelfPermission(READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasManageExternalStoragePermission(): Boolean {
        return Environment.isExternalStorageManager()
    }

    private fun requestManageExternalStoragePermission() {
        val intent = Intent(ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
        }

        startActivity(intent)
    }

    private fun editMediaWithWriteRequest(uris: List<Uri>): IntentSenderRequest {
        return IntentSenderRequest.Builder(MediaStore.createWriteRequest(contentResolver, uris))
            .build()
    }

    private fun editLastModificationDate(image: ImageResource) {
        val file = File(image.path)
        val now = System.currentTimeMillis()
        file.setLastModified(now)

        println("setLastModified (before scan): $now || ${file.lastModified()}")
        runBlocking {
            scanPath(file.absolutePath, "image/jpeg")
            println("setLastModified (after scan): $now || ${file.lastModified()}")
        }

        println("setLastModified: $now || ${file.lastModified()}")
    }

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

    private fun getImageResources(limit: Int = 50): List<ImageResource> {
        val mediaList = mutableListOf<ImageResource>()
        val externalContentUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            ?: throw Exception("External Storage not available")

        val projection = arrayOf(
            MediaStore.Images.ImageColumns._ID,
            MediaStore.Images.ImageColumns.DISPLAY_NAME,
            MediaStore.Images.ImageColumns.SIZE,
            MediaStore.Images.ImageColumns.MIME_TYPE,
            MediaStore.Images.ImageColumns.DATA,
        )

        val cursor = contentResolver.query(
            externalContentUri,
            projection,
            null,
            null,
            "$DATE_ADDED DESC"
        ) ?: throw Exception("Query could not be executed")

        cursor.use {
            while (cursor.moveToNext() && mediaList.size < limit) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeTypeColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

                val id = cursor.getInt(idColumn)
                val contentUri: Uri = ContentUris.withAppendedId(
                    externalContentUri,
                    id.toLong()
                )

                mediaList += ImageResource(
                    uri = contentUri,
                    filename = cursor.getString(displayNameColumn),
                    size = cursor.getLong(sizeColumn),
                    mimeType = cursor.getString(mimeTypeColumn),
                    path = cursor.getString(dataColumn),
                )
            }
        }

        println("Media query: $mediaList")

        return mediaList
    }
}