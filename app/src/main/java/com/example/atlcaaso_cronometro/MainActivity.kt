package com.example.atlcaaso_cronometro

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import androidx.core.content.FileProvider

class MainActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var recordButton: Button
    private lateinit var mediaRecorder: MediaRecorder
    private var cameraDevice: CameraDevice? = null
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var cameraCaptureSessions: CameraCaptureSession
    private lateinit var cameraId: String
    private var isRecording = false
    private val videoDirectory: File by lazy {
        File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MyAppVideos")
    }

    private val permissionRequestCode = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        recordButton = findViewById(R.id.recordButton)
        mediaRecorder = MediaRecorder()

        recordButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO), permissionRequestCode)
            } else {
                if (isRecording) {
                    stopRecording()
                } else {
                    setupCamera()
                }
            }
        }

        // Create video directory if it doesn't exist
        if (!videoDirectory.exists()) {
            videoDirectory.mkdirs()
        }

        // Set up RecyclerView to list recorded videos
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = VideoAdapter(videoDirectory)
    }

    private fun setupCamera() {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            Log.e("CameraSetup", "Error accessing camera: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e("CameraSetup", "Unexpected error: ${e.message}")
            e.printStackTrace()
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
        }
    }

    private fun startPreview() {
        try {
            val surfaceTexture = textureView.surfaceTexture
            surfaceTexture?.setDefaultBufferSize(1920, 1080)
            val surface = Surface(surfaceTexture)
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    cameraCaptureSessions = session
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    try {
                        cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("Camera", "Configuration change")
                }
            }, null)
        } catch (e: Exception) {
            Log.e("StartPreview", "Error starting camera preview: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun startRecording() {
        try {
            val videoFile = File(videoDirectory, "video_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, videoFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, videoFile.absolutePath)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

                uri?.let {
                    val parcelFileDescriptor: ParcelFileDescriptor? = resolver.openFileDescriptor(it, "w")
                    parcelFileDescriptor?.let { pfd ->
                        mediaRecorder.setOutputFile(pfd.fileDescriptor)
                    }
                    parcelFileDescriptor?.close()
                }
            } else {
                mediaRecorder.setOutputFile(videoFile.absolutePath)
            }

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setVideoSize(1920, 1080)
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setVideoEncodingBitRate(10000000)
            mediaRecorder.setVideoFrameRate(30)
            mediaRecorder.setOrientationHint(90)
            mediaRecorder.prepare()

            val surface = mediaRecorder.surface
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            captureRequestBuilder.addTarget(surface)

            cameraDevice!!.createCaptureSession(listOf(surface, Surface(textureView.surfaceTexture)), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSessions = session
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    try {
                        cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                        mediaRecorder.start()
                        isRecording = true
                        recordButton.text = getString(R.string.stop)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("Camera", "Configuration change")
                }
            }, null)
        } catch (e: Exception) {
            Log.e("StartRecording", "Error starting recording: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder.stop()
            mediaRecorder.reset()
            startPreview()
            isRecording = false
            recordButton.text = getString(R.string.record)

            // Refresh RecyclerView
            findViewById<RecyclerView>(R.id.recyclerView).adapter?.notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e("StopRecording", "Error stopping recording: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupCamera()
            } else {
                // Handle permission denial
            }
        }
    }
}
