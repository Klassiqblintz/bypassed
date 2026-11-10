/*
 * Face detector header.
 * Simple face detection using Android's built-in FaceDetector (or fallback to center).
 */
#ifndef KYC_FACE_DETECTOR_H
#define KYC_FACE_DETECTOR_H

#include <jni.h>
#include <android/log.h>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "FaceDetector", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "FaceDetector", __VA_ARGS__)

struct FaceRect {
    float x, y, w, h;
};

/**
 * Initialize the face detector.
 */
void init_face_detector();

/**
 * Detect faces in a frame. Returns array of FaceRect via JNI.
 * Uses Android's FaceDetector API if available, otherwise returns a center-face rect.
 */
jobjectArray detect_faces(JNIEnv *env, jobject bitmap);

/**
 * Extract landmarks from detected face region.
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
