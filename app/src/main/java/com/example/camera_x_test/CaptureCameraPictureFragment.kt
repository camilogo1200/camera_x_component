package com.example.camera_x_test

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResult
import com.example.camera_x_test.databinding.CaptureCameraPictureFragmentBinding
import ironark.com.charge.utils.PermissionsManager
import java.text.SimpleDateFormat
import java.util.Locale

class CaptureCameraPictureFragment : Fragment() {

    private lateinit var binding: CaptureCameraPictureFragmentBinding
    private var cameraStarted = false
    private var imageCapture: ImageCapture? = null
    private var latestPictureUri: Uri? = null
    private lateinit var permissionManager: PermissionsManager

    // Select back camera as a default
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().let {
            permissionManager = PermissionsManager(it, it.activityResultRegistry)
            lifecycle.addObserver(permissionManager)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.capture_camera_picture_fragment,
            container,
            false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindListeners()
        initView()
    }

    private fun bindListeners() {
        binding.captureImageBtn.setOnClickListener {
            it.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            takePicture()
        }

        binding.switchCameraBtn.setOnClickListener {
            it.postDelayed({
                it.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }, 1L)
            toggleCamera()
        }
        binding.previewDismissBtn?.setOnClickListener { hidePreviewContainer() }
        binding.previewSuccessBtn?.setOnClickListener { setImageResult() }
    }

    private fun setImageResult() {
        latestPictureUri?.let {
            setFragmentResult(
                CAPTURED_IMAGE_URI,
                bundleOf(CAPTURED_IMAGE_URI to "$it")
            )
            closeCameraFragment()
        }
    }

    private fun hidePreviewContainer() {
        binding.previewImageContainer?.isVisible = false
        binding.cameraContainer?.isVisible = true
        binding.viewFinder.isVisible = true
    }

    private fun initView() {
        if (checkPermissions()) {
            startCameraPreview()
        }
    }

    private fun checkPermissions(): Boolean {
        val permissionsGranted = allPermissionsGranted()
        if (!permissionsGranted) {
            REQUIRED_PERMISSIONS.forEach {
                permissionManager.requestPermission(
                    it, ::grantedPermission, ::deniedListener
                )
            }
        }
        return permissionsGranted
    }

    private fun deniedListener() {
        closeCameraFragment()
    }

    private fun closeCameraFragment() {
        requireActivity().let {
            childFragmentManager.apply {
                commit {
                    remove(this@CaptureCameraPictureFragment)
                }
                popBackStack()
            }
        }
    }

    private fun grantedPermission() {
        startCameraPreview()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        startCameraPreview()
    }

    private fun startCameraPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                binding.viewFinder.isVisible = true

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
        cameraStarted = true
    }

    private fun takePicture() {
        if (!cameraStarted) return
        binding.viewFinder.isVisible = true
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                requireActivity().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    latestPictureUri = output.savedUri
                    binding.viewFinder.isVisible = false
                    showPreviewImage(latestPictureUri)
                }
            }
        )
    }

    private fun showPreviewImage(latestPictureUri: Uri?) {
        latestPictureUri?.let {
            binding.previewImage?.setImageURI(it)
            binding.cameraContainer?.isVisible = false
            binding.previewImageContainer?.isVisible = true
        }
    }

    private fun toggleCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCameraPreview()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val CAPTURED_MEDIA_PREFIX = "content://media/"
        const val CAPTURED_IMAGE_URI = "image_captured"
        private const val TAG = "CaptureCameraPicture"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
                //,Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }


}
