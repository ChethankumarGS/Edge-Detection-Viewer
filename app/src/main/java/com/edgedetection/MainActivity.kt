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

class MainActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: GLRenderer
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fpsTextView: TextView
    private lateinit var toggleButton: Button
    private var imageAnalyzer: ImageAnalysis? = null
    
    @Volatile
    private var showProcessed = true

    @Volatile
    private var frameCount = 0
    @Volatile
    private var lastFpsTime = System.currentTimeMillis()

    companion object {
        private const val TAG = "EdgeDetection"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        
        private const val TARGET_WIDTH = 640
        private const val TARGET_HEIGHT = 480
        private const val FPS_UPDATE_INTERVAL_MS = 1000L

        init {
            try {
                System.loadLibrary("native-lib")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }

    external fun processFrame(
        data: ByteArray,
        width: Int,
        height: Int,
        rotation: Int
    ): ByteArray?

    external fun rotateRawFrame(
        data: ByteArray,
        width: Int,
        height: Int,
        rotation: Int
    ): ByteArray?

    external fun initializeOpenCV(): Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupOpenGL()
        setupToggleButton()
        
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize OpenCV
        if (!initializeOpenCV()) {
            showError("Failed to initialize OpenCV")
            Log.e(TAG, "OpenCV initialization failed")
        }

        // Request camera permission
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun initializeViews() {
        glSurfaceView = findViewById(R.id.glSurfaceView)
        fpsTextView = findViewById(R.id.fpsTextView)
        toggleButton = findViewById(R.id.toggleButton)
    }

    private fun setupOpenGL() {
        glSurfaceView.apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            renderer = GLRenderer()
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }

    private fun setupToggleButton() {
        toggleButton.setOnClickListener {
            showProcessed = !showProcessed
            toggleButton.text = if (showProcessed) "Show Raw" else "Show Processed"
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider initialization failed", e)
                showError("Failed to start camera")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(TARGET_WIDTH, TARGET_HEIGHT))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .apply {
                setAnalyzer(cameraExecutor, ImageAnalyzer())
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
            imageAnalyzer = imageAnalysis
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
            showError("Failed to bind camera")
        }
    }

    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        private val yDataBuffer = ThreadLocal<ByteArray>()

        override fun analyze(image: ImageProxy) {
            try {
                processImage(image)
            } catch (e: Exception) {
                Log.e(TAG, "Frame processing failed", e)
            } finally {
                image.close()
            }
        }

        private fun processImage(image: ImageProxy) {
            // Extract Y channel (luminance) from YUV_420_888 format
            val yBuffer = image.planes[0].buffer
            val ySize = yBuffer.remaining()
            
            // Reuse buffer to reduce GC pressure
            val yData = yDataBuffer.get()?.takeIf { it.size == ySize } 
                ?: ByteArray(ySize).also { yDataBuffer.set(it) }
            
            yBuffer.position(0)
            yBuffer.get(yData)

            val width = image.width
            val height = image.height
            val rotation = image.imageInfo.rotationDegrees

            val processedData = if (showProcessed) {
                processFrame(yData, width, height, rotation)
            } else {
                rotateRawFrame(yData, width, height, rotation)
            }

            processedData?.let { data ->
                renderer.updateTexture(data, width, height)
                glSurfaceView.requestRender()
            } ?: run {
                Log.w(TAG, "Frame processing returned null")
            }

            updateFPS()
        }
    }

    private fun updateFPS() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastFpsTime

        if (timeDiff >= FPS_UPDATE_INTERVAL_MS) {
            val fps = (frameCount * 1000 / timeDiff).toInt()
            runOnUiThread {
                fpsTextView.text = getString(R.string.fps_format, fps)
            }
            frameCount = 0
            lastFpsTime = currentTime
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this, 
            REQUIRED_PERMISSIONS, 
            REQUEST_CODE_PERMISSIONS
        )
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
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
                showError("Camera permission is required")
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraExecutor.shutdown()
            renderer.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }
}

