/*
 * Face detector implementation.
 * Uses OpenCV Haar cascade for face detection, then extracts 5-point landmarks
 * and warps the injected image to match the detected face shape.
 */
#include "face_detector.h"
#include <android/log.h>
#include <android/bitmap.h>
#include <dlfcn.h>
#include <pthread.h>

static cv::CascadeClassifier s_face_cascade;
static bool s_initialized = false;

// Load Haar cascade from assets or memory
static bool load_cascade() {
    // Use OpenCV's built-in frontal face cascade
    const char *cascade_path = "/data/adb/modules/kyc_bypasser/cascade/haarcascade_frontalface_default.xml";
    s_face_cascade.load(cascade_path);
    if (!s_face_cascade.empty()) {
        LOGD("Loaded Haar cascade from: %s", cascade_path);
        return true;
    }

    // Try alternative paths
    const char *alt_paths[] = {
        "/data/adb/modules/kyc_bypasser/cascades/haarcascade_frontalface_default.xml",
        "/data/adb/modules/kyc_bypasser/cascade/haarcascade_frontalface_alt2.xml",
        nullptr
    };
    for (int i = 0; alt_paths[i]; i++) {
        s_face_cascade.load(alt_paths[i]);
        if (!s_face_cascade.empty()) {
            LOGD("Loaded Haar cascade from: %s", alt_paths[i]);
            return true;
        }
    }

    // Try loading from OpenCV's internal data
    s_face_cascade.load(cv::CascadeClassifier::getDefaultCascadePath("haarcascade_frontalface_default.xml"));
    if (!s_face_cascade.empty()) {
        LOGD("Loaded Haar cascade from internal path");
        return true;
    }

    LOGE("Failed to load any Haar cascade");
    return false;
}

void init_face_detector() {
    if (s_initialized) return;
    LOGD("Initializing face detector");
    if (load_cascade()) {
        s_initialized = true;
        LOGD("Face detector initialized successfully");
    } else {
        LOGE("Face detector init failed");
    }
}

/**
 * Convert Android bitmap to OpenCV Mat.
 */
static cv::Mat bitmap_to_mat(JNIEnv *env, jobject bitmap) {
    AndroidBitmapInfo info;
    void *pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_OK) {
        LOGE("Failed to get bitmap info");
        return cv::Mat();
    }

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_OK) {
        LOGE("Failed to lock bitmap pixels");
        return cv::Mat();
    }

    cv::Mat mat(info.height, info.width, CV_8UC4, pixels);
    cv::Mat rgba;
    cv::cvtColor(mat, rgba, cv::COLOR_RGBA2GRAY); // Grayscale for detection

    AndroidBitmap_unlockPixels(env, bitmap);
    return rgba;
}

/**
 * Detect faces using Haar cascade.
 * Returns array of FaceRect via JNI.
 */
jobjectArray detect_faces(JNIEnv *env, jobject bitmap) {
    if (!s_initialized) return nullptr;

    cv::Mat gray = bitmap_to_mat(env, bitmap);
    if (gray.empty()) return nullptr;

    std::vector<cv::Rect> faces;
    s_face_cascade.detectMultiScale(
        gray,
        faces,
        1.1,  // scaleFactor
        3,    // minNeighbors
        0,    // flags
        cv::Size(30, 30) // minSize
    );

    if (faces.empty()) {
        LOGD("No faces detected");
        return nullptr;
    }

    LOGD("Detected %zu faces", faces.size());

    // Create result array
    jclass faceRectClass = env->FindClass("com/kycbypasser/app/face/FaceRect");
    jmethodID ctor = env->GetMethodID(faceRectClass, "<init>", "(FFFF)V");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(faces.size()), faceRectClass, nullptr);

    for (size_t i = 0; i < faces.size(); i++) {
        // Convert OpenCV rect to normalized FaceRect
        float x = static_cast<float>(faces[i].x) / static_cast<float>(gray.cols);
        float y = static_cast<float>(faces[i].y / static_cast<float>(gray.rows);
        float w = static_cast<float>(faces[i].width) / static_cast<float>(gray.cols);
        float h = static_cast<float>(faces[i].height) / static_cast<float>(gray.rows);

        jobject face = env->NewObject(faceRectClass, ctor, x, y, w, h);
        env->SetObjectArrayElement(result, static_cast<jsize>(i), face);
    }

    return result;
}

/**
 * Extract 5-point landmarks from detected face region.
 * Uses simple heuristic based on face geometry.
 */
jobjectArray get_landmarks(JNIEnv *env, jobject bitmap, jobject faceRect) {
    if (!s_initialized) return nullptr;

    // Get face rect from Java object
    jclass faceRectClass = env->FindClass("com/kycbypasser/app/face/FaceRect");
    jmethodID getX = env->GetMethodID(faceRectClass, "getX", "()F");
    jmethodID getY = env->GetMethodID(faceRectClass, "getY", "()F");
    jmethodID getW = env->GetMethodID(faceRectClass, "getW", "()F");
    jmethodID getH = env->GetMethodID(faceRectClass, "getH", "()F");

    float x = env->CallFloatMethod(faceRect, getX);
    float y = env->CallFloatMethod(faceRect, getY);
    float w = env->CallFloatMethod(faceRect, getW);
    float h = env->CallFloatMethod(faceRect, getH);

    // Create landmarks object
    jclass landmarkClass = env->FindClass("com/kycbypasser/app/face/Landmarks");
    jmethodID ctor = env->GetMethodID(landmarkClass, "<init>", "([F)V");
    jfloatArray points = env->NewFloatArray(10);
    jfloat pts[10] = {
        x + w * 0.25, y + h * 0.25,  // Left eye
        x + w * 0.75, y + h * 0.25,  // Right eye
        x + w * 0.50, y + h * 0.50,  // Nose tip
        x + w * 0.30, y + h * 0.70,  // Left mouth corner
        x + w * 0.70, y + h * 0.70,  // Right mouth corner
    };
    env->SetFloatArrayRegion(points, 0, 10, pts);
    jobject landmarks = env->NewObject(landmarkClass, ctor, points);

    return landmarks;
}

/**
 * Warp an injected image to match detected face shape.
 * Uses affine transformation based on landmark positions.
 */
jobject warp_to_face(JNIEnv *env, jobject injected_bitmap, jobjectArray landmarks,
                     jobject output_surface, float head_angle) {
    if (!s_initialized) {
        LOGE("Face detector not initialized");
        return nullptr;
    }

    // Convert injected bitmap to OpenCV Mat
    AndroidBitmapInfo info;
    void *pixels = nullptr;

    if (AndroidBitmap_getInfo(env, injected_bitmap, &info) != ANDROID_BITMAP_RESULT_OK) {
        LOGE("Failed to get injected bitmap info");
        return nullptr;
    }

    if (AndroidBitmap_lockPixels(env, injected_bitmap, &pixels) != ANDROID_BITMAP_RESULT_OK) {
        LOGE("Failed to lock injected bitmap pixels");
        return nullptr;
    }

    cv::Mat injected(info.height, info.width, CV_8UC4, pixels);
    cv::Mat rgba;
    cv::cvtColor(injected, rgba, cv::COLOR_RGBA2RGB);

    AndroidBitmap_unlockPixels(env, injected_bitmap);

    // Get landmarks
    jclass landmarkClass = env->FindClass("com/kycbypasser/app/face/Landmarks");
    jmethodID getPoints = env->GetMethodID(landmarkClass, "getPoints", "()[F");
    jfloatArray points = (jfloatArray)env->CallObjectMethod(landmarks, getPoints);
    jfloat pts[10];
    env->GetFloatArrayRegion(points, 0, 10, pts);
    env->DeleteLocalRef(points);

    // Create source points from injected image (standard face)
    cv::Point2f src_points[5];
    src_points[0] = cv::Point2f(0.25f * rgba.cols, 0.25f * rgba.rows); // Left eye
    src_points[1] = cv::Point2f(0.75f * rgba.cols, 0.25f * rgba.rows); // Right eye
    src_points[2] = cv::Point2f(0.50f * rgba.cols, 0.50f * rgba.rows); // Nose tip
    src_points[3] = cv::Point2f(0.30f * rgba.cols, 0.70f * rgba.rows); // Left mouth
    src_points[4] = cv::Point2f(0.70f * rgba.cols, 0.70f * rgba.rows); // Right mouth

    // Create destination points from detected face landmarks (normalized to output surface)
    jclass surfaceClass = env->FindClass("android/view/Surface");
    jclass nativeWindowClass = env->FindClass("android/graphics/NativeWindow");
    jmethodID getWidth = env->GetMethodID(nativeWindowClass, "getWidth", "()I");
    jmethodID getHeight = env->GetMethodID(nativeWindowClass, "getHeight", "()I");

    // Get surface dimensions from JNI
    // For now, use standard resolution
    int width = 720, height = 1280;

    cv::Point2f dst_points[5];
    dst_points[0] = cv::Point2f(pts[0] * width, pts[1] * height); // Left eye
    dst_points[1] = cv::Point2f(pts[2] * width, pts[3] * height); // Right eye
    dst_points[2] = cv::Point2f(pts[4] * width, pts[5] * height); // Nose tip
    dst_points[3] = cv::Point2f(pts[6] * width, pts[7] * height); // Left mouth
    dst_points[4] = cv::Point2f(pts[8] * width, pts[9] * height); // Right mouth

    // Compute affine transformation
    cv::Mat warp_matrix = cv::getAffineTransform(src_points, dst_points);

    // Apply warp
    cv::Mat warped;
    cv::warpAffine(rgba, warped, warp_matrix, cv::Size(width, height));

    // Convert back to Android bitmap
    cv::Mat bgra;
    cv::cvtColor(warped, bgra, cv::COLOR_RGB2RGBA);

    jobject output_bitmap = nullptr;
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmap = env->GetStaticMethodID(
        bitmapClass, "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jstring configStr = env->NewStringUTF("ARGB_8888");
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    jmethodID valueOf = env->GetStaticMethodID(
        configClass, "valueOf", "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
    jobject config = env->CallStaticObjectMethod(configClass, valueOf, configStr);

    output_bitmap = env->CallStaticObjectMethod(
        bitmapClass, createBitmap, width, height, config);

    if (output_bitmap) {
        void *out_pixels;
        if (AndroidBitmap_lockPixels(env, output_bitmap, &out_pixels) == ANDROID_BITMAP_RESULT_OK) {
            cv::Mat out_mat(height, width, CV_8UC4, out_pixels);
            bgra.copyTo(out_mat);
            AndroidBitmap_unlockPixels(env, output_bitmap);
        }
    }

    return output_bitmap;
}

void shutdown_face_detector() {
    s_face_cascade.clear();
    s_initialized = false;
    LOGD("Face detector shut down");
}
