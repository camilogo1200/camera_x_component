package com.example.camera_x_test

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResult
import com.example.camera_x_test.databinding.CaptureCameraPictureFragmentBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureCameraPictureFragment : Fragment() {

    private lateinit var binding: CaptureCameraPictureFragmentBinding
    private lateinit var cameraExecutor: ExecutorService
    private var cameraStarted = false
    private var imageCapture: ImageCapture? = null
    private var latestPictureUri: Uri? = null

    // Select back camera as a default
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

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
            }, 10L)

            it.postDelayed({
                requireActivity().window.decorView.performHapticFeedback(
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
        latestPictureUri?.path?.let {
            setFragmentResult(
                CAPTURED_IMAGE_URI,
                bundleOf(CAPTURED_IMAGE_URI to "${CAPTURED_MEDIA_PREFIX}${it}")
            )
        }
    }

    private fun hidePreviewContainer() {
        binding.previewImageContainer?.isVisible = false
        binding.cameraContainer?.isVisible = true
    }

    private fun initView() {
        if (checkPermissions()) {
            if (!cameraStarted) {
                cameraExecutor = Executors.newSingleThreadExecutor()
                startCameraPreview()
            } else {
                takePicture()
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val permissionsGranted = allPermissionsGranted()
        if (!permissionsGranted) {
            activity?.let {
                ActivityCompat.requestPermissions(
                    it, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }
        }
        return permissionsGranted
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCameraPreview()
            } else {
                context?.let {
                    Toast.makeText(
                        it, "Permissions not granted by the user.", Toast.LENGTH_SHORT
                    ).show()
                }
                activity?.let {
                    it.supportFragmentManager.commit {
                        remove(this@CaptureCameraPictureFragment)
                    }
                }
            }
        }
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

            imageCapture = ImageCapture.Builder().build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
        cameraStarted = true
    }

    private fun takePicture() {
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
                    /*val msg = "Photo capture succeeded: ${output.savedUri}"
                    context?.let {
                        Toast.makeText(it, msg, Toast.LENGTH_SHORT).show()
                        Log.d(TAG, msg)
                    }
                    */
                    latestPictureUri = output.savedUri
                    showPreviewImage(latestPictureUri)
                }
            }
        )
    }

    private fun showPreviewImage(latestPictureUri: Uri?) {
        binding.cameraContainer?.isVisible = false
        binding.previewImageContainer?.isVisible = true
        latestPictureUri?.path?.let {
            binding.previewImage?.setImageURI(Uri.parse("$CAPTURED_MEDIA_PREFIX{it}"))
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val CAPTURED_MEDIA_PREFIX = "content://media/"
        private const val CAPTURED_IMAGE_URI = "image_captured"
        private const val TAG = "CaptureCameraPicture"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }


}
