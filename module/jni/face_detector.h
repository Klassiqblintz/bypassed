/*
 * Face detector header.
 * Uses OpenCV for face detection and landmark-based warp for real-time face swap.
 */
#ifndef KYC_FACE_DETECTOR_H
#define KYC_FACE_DETECTOR_H

#include <jni.h>
#include <android/log.h>
#include <opencv2/opencv.hpp>
#include <opencv2/face.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp>
#include <opencv2/objdetect.hpp>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "FaceDetector", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "FaceDetector", __VA_ARGS__)

struct FaceRect {
    float x, y, w, h;
};

struct Landmarks {
    float points[10]; // 5 key points: left_eye(2), right_eye(2), nose(2)
};

/**
 * Initialize the face detector with OpenCV Haar cascade.
 */
void init_face_detector();

/**
 * Detect faces in a frame. Returns array of FaceRect via JNI.
 */
jobjectArray detect_faces(JNIEnv *env, jobject bitmap);

/**
 * Extract 5-point landmarks from detected face region.
 * Returns Landmarks array via JNI.
 */
jobjectArray get_landmarks(JNIEnv *env, jobject bitmap, jobject faceRect);

/**
 * Warp an injected image to match detected face shape and render to surface.
 */
jobject warp_to_face(JNIEnv *env, jobject injected_bitmap, jobjectArray landmarks,
                     jobject output_surface, float head_angle);

/**
 * Shutdown the face detector.
 */
void shutdown_face_detector();

#endif // KYC_FACE_DETECTOR_H
