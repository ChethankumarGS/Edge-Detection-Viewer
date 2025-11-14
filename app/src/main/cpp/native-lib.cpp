#include <jni.h>
#include <string>
#include <android/log.h>
#include <opencv2/opencv.hpp>
#include "opencv-processor.h"

#define LOG_TAG "EdgeDetectionNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_edgedetection_MainActivity_initializeOpenCV(JNIEnv *env, jobject /* this */) {
    LOGD("Initializing OpenCV...");
    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_edgedetection_MainActivity_processFrame(
    JNIEnv *env,
    jobject /* this */,
    jbyteArray data,
    jint width,
    jint height,
    jint rotation) {

    if (data == nullptr) {
        LOGE("Input data is null");
        return nullptr;
    }

    jbyte *frameData = env->GetByteArrayElements(data, nullptr);
    if (frameData == nullptr) {
        LOGE("Failed to get byte array elements");
        return nullptr;
    }

    jsize dataSize = env->GetArrayLength(data);

    try {
        // Create Mat from input data (grayscale Y channel)
        // Input is already grayscale Y channel from CameraX
        cv::Mat grayMat(height, width, CV_8UC1, (unsigned char*)frameData);
        cv::Mat outputMat;

        // Apply Canny edge detection directly on grayscale
        cv::Canny(grayMat, outputMat, 50, 150);

        // Rotate if needed
        if (rotation == 90) {
            cv::rotate(outputMat, outputMat, cv::ROTATE_90_CLOCKWISE);
        } else if (rotation == 180) {
            cv::rotate(outputMat, outputMat, cv::ROTATE_180);
        } else if (rotation == 270) {
            cv::rotate(outputMat, outputMat, cv::ROTATE_90_COUNTERCLOCKWISE);
        }

        // Convert processed data back to byte array
        jbyteArray result = env->NewByteArray(outputMat.total());
        if (result != nullptr) {
            env->SetByteArrayRegion(result, 0, outputMat.total(),
                                    reinterpret_cast<jbyte*>(outputMat.data));
        }

        env->ReleaseByteArrayElements(data, frameData, JNI_ABORT);

        return result;

    } catch (const cv::Exception& e) {
        LOGE("OpenCV Exception: %s", e.what());
        env->ReleaseByteArrayElements(data, frameData, JNI_ABORT);
        return nullptr;
    } catch (...) {
        LOGE("Unknown exception in processFrame");
        env->ReleaseByteArrayElements(data, frameData, JNI_ABORT);
        return nullptr;
    }
}

JNIEXPORT jbyteArray JNICALL
Java_com_edgedetection_MainActivity_rotateRawFrame(
    JNIEnv *env,
    jobject /* this */,
    jbyteArray data,
    jint width,
    jint height,
    jint rotation) {

    if (data == nullptr) {
        LOGE("Input data is null");
        return nullptr;
    }

    jbyte *frameData = env->GetByteArrayElements(data, nullptr);
    if (frameData == nullptr) {
        LOGE("Failed to get byte array elements");
        return nullptr;
    }

    try {
        // Create Mat from input data (grayscale Y channel)
        cv::Mat grayMat(height, width, CV_8UC1, (unsigned char*)frameData);
        cv::Mat outputMat;

        // Rotate if needed
        if (rotation == 90) {
            cv::rotate(grayMat, outputMat, cv::ROTATE_90_CLOCKWISE);
        } else if (rotation == 180) {
            cv::rotate(grayMat, outputMat, cv::ROTATE_180);
        } else if (rotation == 270) {
            cv::rotate(grayMat, outputMat, cv::ROTATE_90_COUNTERCLOCKWISE);
        } else {
            outputMat = grayMat;
        }

        // Convert rotated data back to byte array
        jbyteArray result = env->NewByteArray(outputMat.total());
        if (result != nullptr) {
            env->SetByteArrayRegion(result, 0, outputMat.total(),
                                    reinterpret_cast<jbyte*>(outputMat.data));
        }

        env->ReleaseByteArrayElements(data, frameData, JNI_ABORT);

        return result;

    } catch (const cv::Exception& e) {
        LOGE("OpenCV Exception: %s", e.what());
        env->ReleaseByteArrayElements(data, frameData, JNI_ABORT);
        return nullptr;
    } catch (...) {
        LOGE("Unknown exception in rotateRawFrame");
        env->ReleaseByteArrayElements(data, frameData, JNI_ABORT);
        return nullptr;
    }
}

}

