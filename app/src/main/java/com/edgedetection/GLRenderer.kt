package com.edgedetection

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer : GLSurfaceView.Renderer {

    private var program: Int = 0
    private var positionHandle: Int = 0
    private var textureCoordHandle: Int = 0
    private var textureHandle: Int = 0

    private val vertexBuffer: FloatBuffer
    private val textureCoordBuffer: FloatBuffer

    private var textureId: Int = 0
    
    @Volatile
    private var textureData: ByteArray? = null
    @Volatile
    private var textureWidth: Int = 0
    @Volatile
    private var textureHeight: Int = 0
    @Volatile
    private var updateTexture = false

    companion object {
        private const val TAG = "GLRenderer"
        private const val COORDS_PER_VERTEX = 3
        private const val COORDS_PER_TEXTURE = 2
        private const val VERTEX_STRIDE = COORDS_PER_VERTEX * 4 // 4 bytes per float
        private const val TEXTURE_STRIDE = COORDS_PER_TEXTURE * 4
        
        private val vertices = floatArrayOf(
            -1f, -1f, 0f,
             1f, -1f, 0f,
            -1f,  1f, 0f,
             1f,  1f, 0f
        )

        private val textureCoords = floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
        )

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = aTextureCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTextureCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTextureCoord);
            }
        """
    }

    init {
        // Allocate direct buffers in native memory (not GC'd)
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }

        textureCoordBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(textureCoords)
                position(0)
            }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        if (vertexShader == 0 || fragmentShader == 0) {
            Log.e(TAG, "Failed to compile shaders")
            return
        }

        program = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vertexShader)
            GLES20.glAttachShader(prog, fragmentShader)
            GLES20.glLinkProgram(prog)

            // Check link status
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Program link failed: ${GLES20.glGetProgramInfoLog(prog)}")
                GLES20.glDeleteProgram(prog)
                return
            }

            // Clean up shaders after linking
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
        }

        // Get attribute and uniform locations
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        textureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")

        // Generate and configure texture
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        textureId = textureIds[0]
        
        // Pre-configure texture parameters (only once)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Update texture if new data available
        if (updateTexture) {
            textureData?.let { data ->
                updateTextureData(data, textureWidth, textureHeight)
            }
            updateTexture = false
        }

        // Use program and set attributes
        GLES20.glUseProgram(program)

        // Bind vertex positions
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle, 
            COORDS_PER_VERTEX, 
            GLES20.GL_FLOAT, 
            false, 
            VERTEX_STRIDE, 
            vertexBuffer
        )

        // Bind texture coordinates
        GLES20.glEnableVertexAttribArray(textureCoordHandle)
        GLES20.glVertexAttribPointer(
            textureCoordHandle, 
            COORDS_PER_TEXTURE, 
            GLES20.GL_FLOAT, 
            false, 
            TEXTURE_STRIDE, 
            textureCoordBuffer
        )

        // Bind texture and set uniform
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        // Draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Clean up
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureCoordHandle)
    }

    private fun updateTextureData(data: ByteArray, width: Int, height: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        
        // Wrap existing byte array (zero-copy)
        val buffer = ByteBuffer.wrap(data)
        
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 
            0, 
            GLES20.GL_LUMINANCE,
            width, 
            height, 
            0,
            GLES20.GL_LUMINANCE, 
            GLES20.GL_UNSIGNED_BYTE, 
            buffer
        )
    }

    fun updateTexture(data: ByteArray, width: Int, height: Int) {
        textureData = data
        textureWidth = width
        textureHeight = height
        updateTexture = true
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)

            // Check compilation status
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(TAG, "Shader compilation failed: ${GLES20.glGetShaderInfoLog(shader)}")
                GLES20.glDeleteShader(shader)
                return 0
            }
        }
    }
    
    fun cleanup() {
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }
}

