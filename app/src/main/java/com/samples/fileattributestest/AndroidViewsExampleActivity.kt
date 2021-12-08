package com.samples.fileattributestest

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.samples.fileattributestest.databinding.AndroidViewsExampleActivityBinding

class AndroidViewsExampleActivity : AppCompatActivity() {
    private lateinit var binding: AndroidViewsExampleActivityBinding
    private var mostRecentImage: ImageResource? = null

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateUi()
        }

    private val intentSenderLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                mostRecentImage?.let {
                    StorageUtils.editLastModificationDate(this, it)
                    mostRecentImage = StorageUtils.getMostRecentImage(this)
                    updateUi()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.android_views_example_activity)

        binding = AndroidViewsExampleActivityBinding.inflate(layoutInflater)
        setupUi()
        updateUi()
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun setupUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.requestPermissions.text = "Request read storage permission"
            binding.editImageAttribute.text = "Click to edit last item (write request)"
        } else {
            binding.requestPermissions.text = "Request read/write storage permissions"
            binding.editImageAttribute.text = "Click to edit last item (without write request)"
        }

        binding.requestPermissions.setOnClickListener {
            /**
             * On Android R+, we don't need to request [WRITE_EXTERNAL_STORAGE] to edit 3rd party
             * media files but we do need to request [READ_EXTERNAL_STORAGE] and explicit user
             * consent for the edited files.
             * On Android Q and lower, we need to request both [READ_EXTERNAL_STORAGE] and
             * [WRITE_EXTERNAL_STORAGE] to edit 3rd party files
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requestPermissions.launch(arrayOf(READ_EXTERNAL_STORAGE))
            } else {
                requestPermissions.launch(arrayOf(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE))
            }
        }

        binding.getMostRecentImage.setOnClickListener {
            mostRecentImage = StorageUtils.getMostRecentImage(this)
            binding.editImageAttribute.isEnabled = true
            binding.imageDetails.text = mostRecentImage.toString()
        }

        binding.editImageAttribute.setOnClickListener {
            /**
             * On Android R+, we use [android.provider.MediaStore.createWriteRequest] to request
             * user's consent to be able to edit the file, on Android Q and lower,
             * we don't have to request that consent to edit if we have the
             * [READ_EXTERNAL_STORAGE] permission
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                intentSenderLauncher.launch(
                    StorageUtils.editImageWithWriteRequest(this, mostRecentImage!!.uri)
                )
            } else {
                StorageUtils.editLastModificationDate(this, mostRecentImage!!)
                mostRecentImage = StorageUtils.getMostRecentImage(this)
                updateUi()
            }
        }
    }

    private fun updateUi() {
        val hasPermission = StorageUtils.hasStoragePermission(this)
        binding.requestPermissions.isEnabled = !hasPermission
        binding.getMostRecentImage.isEnabled = hasPermission

        /**
         * Display image metadata once it has been fetched
         */
        if (mostRecentImage == null) {
            binding.editImageAttribute.isEnabled = false
        } else {
            binding.editImageAttribute.isEnabled = true
            binding.imageDetails.text = mostRecentImage.toString()
        }
    }
}