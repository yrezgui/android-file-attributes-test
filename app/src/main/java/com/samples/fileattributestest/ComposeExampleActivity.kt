package com.samples.fileattributestest

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.format.Formatter.formatShortFileSize
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.samples.fileattributestest.ui.theme.FileAttributesTestTheme
import kotlinx.coroutines.runBlocking
import android.content.Intent
import android.net.Uri
import android.provider.Settings


class ComposeExampleActivity : ComponentActivity() {
    @ExperimentalMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FileAttributesTestTheme {
                val context = LocalContext.current
                var hasStoragePermission by remember {
                    mutableStateOf(
                        StorageUtils.hasStoragePermission(
                            context
                        )
                    )
                }

                val requestPermissions =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                        hasStoragePermission = StorageUtils.hasStoragePermission(context)
                    }

                var mostRecentImage by remember { mutableStateOf<ImageResource?>(null) }

                val intentSenderLauncher =
                    rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->
                        if (result.resultCode == RESULT_OK) {
                            mostRecentImage?.let {
                                StorageUtils.editLastModificationDate(context, it)
                                StorageUtils.editFileContent(context, it)
                            }
                        }
                    }

                Surface(Modifier.padding(top = 10.dp), color = MaterialTheme.colors.background) {
                    Column {
                        ListItem(trailing = { Text(hasStoragePermission.toString()) }) {

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                Button(onClick = {
                                    requestPermissions.launch(arrayOf(READ_EXTERNAL_STORAGE))
                                }) {
                                    Text("Request read storage permission")
                                }
                            } else {
                                Button(onClick = {
                                    requestPermissions.launch(
                                        arrayOf(
                                            READ_EXTERNAL_STORAGE,
                                            WRITE_EXTERNAL_STORAGE
                                        )
                                    )
                                }) {
                                    Text("Request read/write storage permissions")
                                }
                            }
                        }
                        Divider()


                        ListItem(trailing = { Text(StorageUtils.hasManageStoragePermission().toString()) }) {
                            Button(onClick = { requireExternalManagePermission() }) {
                                Text("Request manage storage permission")
                            }
                        }
                        Divider()

                        ListItem {
                            Button(
                                enabled = hasStoragePermission,
                                onClick = {
                                    mostRecentImage = StorageUtils.getMostRecentImage(context)
                                }
                            ) {
                                Text("Get most recent image")
                            }
                        }
                        Divider()


                        ListItem {
                            Button(
                                enabled = mostRecentImage != null,
                                onClick = {
                                    StorageUtils.editLastModificationDate(context, mostRecentImage!!)
                                    StorageUtils.editFileContent(context, mostRecentImage!!)
                                }
                            ) {
                                Text("Click to edit last item (manage)")
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
                                            StorageUtils.editImageWithWriteRequest(
                                                context,
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

                                        StorageUtils.editLastModificationDate(context, mostRecentImage!!)
                                        StorageUtils.editFileContent(context, mostRecentImage!!)
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
                        mostRecentImage?.let { ImageDetails(it) }
                    }
                }
            }
        }
    }

    private fun requireExternalManagePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(Intent().apply {
                action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                data = Uri.fromParts("package", packageName, null)
            })
        }
    }
}

@ExperimentalMaterialApi
@Composable
fun ImageDetails(image: ImageResource) {
    val context = LocalContext.current

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