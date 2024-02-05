package com.example.screendistance

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark

class FaceAnalyzer(private val listener: (List<Face>) -> Unit) : ImageAnalysis.Analyzer {
    private val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    private val faceDetector = FaceDetection.getClient(highAccuracyOpts)

    @OptIn(ExperimentalGetImage::class) override fun analyze(image: ImageProxy) {
        val mediaImage = image.image
        if (mediaImage != null) {
            val inputImage =
                InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)

            val result = faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    listener.invoke(faces)
                    val leftEyePos = faces[0].getLandmark(FaceLandmark.LEFT_EYE)?.position
                    val rightEyePos = faces[0].getLandmark(FaceLandmark.RIGHT_EYE)?.position

                    if (leftEyePos != null && rightEyePos != null) {
                        val deltaX = Math.abs(leftEyePos.x - rightEyePos.x)
                        val deltaY = Math.abs(leftEyePos.y - rightEyePos.y)

                        val distance: Float = if (deltaX >= deltaY) {
                            F * (MainActivity.AVERAGE_EYE_DISTANCE / sensorX) * (MainActivity.IMAGE_WIDTH / deltaX)
                        } else {
                            F * (MainActivity.AVERAGE_EYE_DISTANCE / sensorY) * (MainActivity.IMAGE_HEIGHT / deltaY)
                        }

                        return@addOnSuccessListener distance
                    }

                    image.close()
                }
                .addOnFailureListener {
                    image.close()
                }
        }
    }
}
