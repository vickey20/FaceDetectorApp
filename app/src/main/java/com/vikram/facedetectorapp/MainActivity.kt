package com.vikram.facedetectorapp

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Tracker
import com.google.android.gms.vision.face.Face
import com.google.android.gms.vision.face.FaceDetector
import com.google.android.gms.vision.face.LargestFaceFocusingProcessor

class MainActivity : AppCompatActivity(), FaceDetectedListener {
    private val TAG = MainActivity::class.java.simpleName
    private var cameraSource: CameraSource? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        runFaceDetection()
    }

    private fun requestPermissions() {
        requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
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
                    .setFacing(CameraSource.CAMERA_FACING_BACK)
                    .setRequestedPreviewSize(320, 240)
                    .build().start()

        } else {
            requestPermissions()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                runFaceDetection()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        cameraSource?.stop()
    }

    override fun onFaceDetected() {
        Log.d(TAG, "onFaceDetected")
        cameraSource?.stop()

        // TODO click photo
    }
}

class FaceTracker(private val listener: FaceDetectedListener): Tracker<Face>() {
    private val TAG = FaceTracker::class.java.simpleName

    private var faceId: Int = -1
    private var updateCount = 0

    override fun onDone() {
        super.onDone()
        updateCount = 0
        Log.d(TAG, "onDone")
    }

    override fun onMissing(p0: Detector.Detections<Face>) {
        super.onMissing(p0)
        updateCount = 0
        Log.d(TAG, "onMissing: ${p0.detectedItems}")
    }

    override fun onNewItem(p0: Int, p1: Face) {
        super.onNewItem(p0, p1)
        faceId = p0
        updateCount = 0
        Log.d(TAG, "onNewItem id: $p0. face: ${p1.id}")
    }

    override fun onUpdate(p0: Detector.Detections<Face>, p1: Face) {
        super.onUpdate(p0, p1)
        updateCount++
        Log.d(TAG, "onUpdate count: $updateCount. detectedItems: ${p0.detectedItems}. face Id: ${p1.id}")
        if (updateCount > 20) {
            listener.onFaceDetected()
        }
    }
}

interface FaceDetectedListener {
    fun onFaceDetected()
}