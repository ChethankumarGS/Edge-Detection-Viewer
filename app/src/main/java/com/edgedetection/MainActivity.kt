package com.edgedetection

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: GLRenderer
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fpsTextView: TextView
    private lateinit var toggleButton: Button
    private var imageAnalyzer: ImageAnalysis? = null
    private var showProcessed = true

    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()

    companion object {
        private const val TAG = "EdgeDetection"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        init {
            System.loadLibrary("native-lib")
        }
    }

    external fun processFrame(
        data: ByteArray,
        width: Int,
        height: Int,
        rotation: Int
    ): ByteArray

    external fun rotateRawFrame(
        data: ByteArray,
        width: Int,
        height: Int,
        rotation: Int
    ): ByteArray

    external fun initializeOpenCV(): Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        fpsTextView = findViewById(R.id.fpsTextView)
        toggleButton = findViewById(R.id.toggleButton)

        setupOpenGL()

        toggleButton.setOnClickListener {
            showProcessed = !showProcessed
            toggleButton.text = if (showProcessed) "Show Raw" else "Show Processed"
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize OpenCV
        if (!initializeOpenCV()) {
            Toast.makeText(this, "Failed to initialize OpenCV", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupOpenGL() {
        glSurfaceView.setEGLContextClientVersion(2)
        renderer = GLRenderer()
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageAnalyzer
                )
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            val processingTime = measureTimeMillis {
                // Extract Y channel (luminance) from YUV_420_888 format
                // For edge detection, we only need the Y channel
                val yBuffer = image.planes[0].buffer
                val ySize = yBuffer.remaining()
                val yData = ByteArray(ySize)
                yBuffer.get(yData)

                val width = image.width
                val height = image.height
                val rotation = image.imageInfo.rotationDegrees

                if (showProcessed) {
                    // Process frame for edge detection (only Y channel needed)
                    val processedData = processFrame(
                        yData,
                        width,
                        height,
                        rotation
                    )
                    renderer.updateTexture(processedData, width, height)
                } else {
                    // Apply rotation to raw preview as well
                    val rotatedData = rotateRawFrame(
                        yData,
                        width,
                        height,
                        rotation
                    )
                    renderer.updateTexture(rotatedData, width, height)
                }

                glSurfaceView.requestRender()
            }

            updateFPS()
            image.close()
        }
    }

    private fun updateFPS() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastFpsTime

        if (timeDiff >= 1000) {
            val fps = frameCount * 1000 / timeDiff
            runOnUiThread {
                fpsTextView.text = "FPS: $fps"
            }
            frameCount = 0
            lastFpsTime = currentTime
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

