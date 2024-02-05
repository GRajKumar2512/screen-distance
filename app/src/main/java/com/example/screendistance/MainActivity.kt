package com.example.screendistance

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE
import com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var F = 1f           // Focal length
    private var sensorX = 0f
    private var sensorY = 0f

    companion object {
        const val AVERAGE_EYE_DISTANCE = 63 // in mm
        const val IMAGE_WIDTH = 1024
        const val IMAGE_HEIGHT = 1024
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppContent()
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Composable
    fun AppContent() {
        val context = LocalContext.current

        // state variable for the permission state
        val permissionState = remember { mutableStateOf(false) }

        // create an instance of the Activity Result Launcher
        val requestPermissionLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                permissionState.value = isGranted
                if (!isGranted) {
                    Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }

        // if camera permission, then set state true otherwise launch permission launcher
        DisposableEffect(context) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                permissionState.value = true
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }

            onDispose {
                cameraExecutor.shutdown()
            }
        }

        // on permission proceed otherwise display "permission required"
        if (permissionState.value) {
            SetupCamera(context)
        } else {
            PermissionRequired()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Composable
    private fun SetupCamera(context: Context) {
        val textView = remember { mutableStateOf("Distance: - mm") }

        DisposableEffect(context) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { imageProxy ->
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees;

                    if (imageProxy.imageInfo()) {
                        val leftEyePos = imageProxy[0].getLandmark(LEFT_EYE)?.position
                        val rightEyePos = imageProxy[0].getLandmark(RIGHT_EYE)?.position

                        if (leftEyePos != null && rightEyePos != null) {
                            val deltaX = Math.abs(leftEyePos.x - rightEyePos.x)
                            val deltaY = Math.abs(leftEyePos.y - rightEyePos.y)

                            val distance: Float = if (deltaX >= deltaY) {
                                F * (AVERAGE_EYE_DISTANCE / sensorX) * (IMAGE_WIDTH / deltaX)
                            } else {
                                F * (AVERAGE_EYE_DISTANCE / sensorY) * (IMAGE_HEIGHT / deltaY)
                            }

                            textView.value = "Distance: ${String.format("%.0f", distance)} mm"
                        }
                    }
                })

                cameraProvider.unbindAll()

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.bindToLifecycle(
                    context as LifecycleOwner,
                    cameraSelector,
                    imageAnalysis
                )

            }, context.mainExecutor)

            onDispose {
                cameraExecutor.shutdown()
            }
        }

        // the content for display
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = textView.value,
                    style = MaterialTheme.typography.displayMedium
                )
            }
        }
    }

    @Composable
    private fun PermissionRequired() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Camera permission is required to use this app.",
                style = MaterialTheme.typography.displayLarge
            )
        }
    }
}
