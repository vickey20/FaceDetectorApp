package com.vikram.facedetectorapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Tracker
import com.google.android.gms.vision.face.Face
import com.google.android.gms.vision.face.FaceDetector
import com.google.android.gms.vision.face.LargestFaceFocusingProcessor
import com.vikram.facedetectorapp.databinding.ActivityMainBinding
import java.io.File
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), FaceDetectedListener {
    companion object {
        private const val TAG = "MainActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private var cameraSource: CameraSource? = null
    private var imageCapture: ImageCapture? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private var rotation = 0

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            runFaceDetection()
        } else {
            requestPermissions()
        }

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        calculateRotation()
    }

    override fun onFaceDetected() {
        Log.d(TAG, "onFaceDetected")
        takePhoto()
    }

    private fun runFaceDetection() {
        val faceDetector = FaceDetector.Builder(applicationContext)
                .setProminentFaceOnly(true)
                .build()

        faceDetector.setProcessor(LargestFaceFocusingProcessor(
                faceDetector,
                FaceTracker(this)
        ))

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraSource = CameraSource.Builder(applicationContext, faceDetector)
                    .setFacing(CameraSource.CAMERA_FACING_FRONT)
                    .setRequestedPreviewSize(320, 240)
                    .setRequestedFps(15.0f)
                    .setAutoFocusEnabled(true)
                    .build().start()

        } else {
            requestPermissions()
        }
    }

    private fun takePhoto() {
        getCameraInfo { info ->
            // disable shutter sound
            getCamera { camera ->
                camera?.let {
                    if (info.canDisableShutterSound) camera.enableShutterSound(false)
                }
            }
        }

        cameraSource?.takePicture(null, { byteArray ->
            Log.d(TAG, "onTakePhoto")

            val originalBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            binding.image.setImageBitmap(originalBitmap)

            Handler(Looper.getMainLooper()).postDelayed({
                val rotateMatrix = Matrix()
                rotateMatrix.postRotate(rotation.toFloat())
                val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0,
                        originalBitmap.width, originalBitmap.height, rotateMatrix, false)
                binding.image.setImageBitmap(rotatedBitmap)
            }, 1000)

            cameraSource?.stop()
            cameraSource?.release()
        })
    }

    private fun calculateRotation() {
        rotation = windowManager.defaultDisplay.rotation
        val degree = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        getCameraInfo { info ->
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                Log.d(TAG, "facing front rotation: $rotation. degrees: $degree. orintation: ${info.orientation}")
                rotation = (info.orientation + degree) % 360
                rotation = (360 - rotation) % 360
            } else {
                rotation = (info.orientation - degree + 360) % 360
            }
        }
        Log.d(TAG, "calculateRotation: $rotation")
    }

    private fun getCameraInfo(onComplete: (Camera.CameraInfo) -> Unit) {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_FRONT, info)
        onComplete(info)
    }

    private fun getCamera(onComplete: (Camera?) -> Unit ) {
        val fields = CameraSource::class.java.declaredFields
        fields.forEach { field ->
            if (field.type == Camera::class.java) {
                field.isAccessible = true
                try {
                    val camera = field.get(cameraSource) as Camera
                    onComplete(camera)
                } catch (e: IllegalAccessException) {
                    onComplete(null)
                }
            }
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }

        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                runFaceDetection()
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        cameraSource?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        cameraSource?.stop()
        cameraSource?.release()
    }

/*    private fun startCamera() {
        Log.d(TAG, "startCamera")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.viewFinder.createSurfaceProvider())
                    }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    fun onCaptureClicked(view: View) {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
                outputDirectory,
                SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                        .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
                outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                val msg = "Photo captured: $savedUri"
                Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                Log.d(TAG, msg)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
            }
        })
    }*/
}

class FaceTracker(private val listener: FaceDetectedListener): Tracker<Face>() {
    private val TAG = FaceTracker::class.java.simpleName

    private var faceId: Int = -1
    private var updateCount = 0

    override fun onNewItem(p0: Int, p1: Face) {
        super.onNewItem(p0, p1)
        faceId = p0
        updateCount = 0
        Log.d(TAG, "onNewItem id: $p0. face id: ${p1.id}")
    }

    override fun onUpdate(p0: Detector.Detections<Face>, p1: Face) {
        super.onUpdate(p0, p1)
        updateCount++
        Log.d(TAG, "onUpdate count: $updateCount. detectedItems: ${p0.detectedItems}. face Id: ${p1.id}")
        if (updateCount > 15) {
            listener.onFaceDetected()
        }
    }

    override fun onMissing(p0: Detector.Detections<Face>) {
        super.onMissing(p0)
        updateCount = 0
        Log.d(TAG, "onMissing: ${p0.detectedItems}")
    }

    override fun onDone() {
        super.onDone()
        updateCount = 0
        Log.d(TAG, "onDone")
    }
}

interface FaceDetectedListener {
    fun onFaceDetected()
}