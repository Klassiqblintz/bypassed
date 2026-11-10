/*
 * Face detector implementation.
 * Simple face detection using Android's built-in FaceDetector API.
 * Falls back to a center-face rectangle if no faces detected.
 */
#include "face_detector.h"
#include <android/log.h>
#include <cstring>
#include <unistd.h>

static bool s_initialized = false;

void init_face_detector() {
    s_initialized = true;
    LOGD("Face detector initialized");
}

jobjectArray detect_faces(JNIEnv *env, jobject bitmap) {
    if (!s_initialized) return nullptr;

    jclass faceDetectorClass = env->FindClass("android/graphics/FaceDetector");
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jclass faceRectClass = env->FindClass("com/kycbypasser/app/ui/face/FaceRect");

    if (!faceDetectorClass || !faceRectClass) {
        // Fallback: return a single face at center
        jclass faceRectClass2 = env->FindClass("com/kycbypasser/app/ui/face/FaceRect");
        if (faceRectClass2) {
            jmethodID ctor = env->GetMethodID(faceRectClass2, "<init>", "(FFFF)V");
            jobjectArray result = env->NewObjectArray(1, faceRectClass2, nullptr);
            jobject face = env->NewObject(faceRectClass2, ctor, 0.3f, 0.2f, 0.4f, 0.4f);
            env->SetObjectArrayElement(result, 0, face);
            env->DeleteLocalRef(faceRectClass2);
            return result;
        }
        return nullptr;
    }

    jmethodID getWidth = env->GetMethodID(bitmapClass, "getWidth", "()I");
    jmethodID getHeight = env->GetMethodID(bitmapClass, "getHeight", "()I");
    jint width = env->CallIntMethod(bitmap, getWidth);
    jint height = env->CallIntMethod(bitmap, getHeight);

    // Create face detector with max 5 faces
    jmethodID fdCtor = env->GetMethodID(faceDetectorClass, "<init>", "(IF)Landroid/graphics/FaceDetector;");
    jobject fd = env->NewObject(faceDetectorClass, fdCtor, 5, 0x5650); // RGB_565

    // Get bitmap config
    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jmethodID toStringMethod = env->GetMethodID(bitmapConfigClass, "name", "()Ljava/lang/String;");
    jclass bitmapConfigFieldClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID rgb565Field = env->GetStaticFieldID(bitmapConfigClass, "RGB_565", "Landroid/graphics/Bitmap$Config;");
    jobject rgb565Config = env->GetStaticObjectField(bitmapConfigClass, rgb565Field);

    // Get bitmap bitmap density and pixels
    jintArray pixels = env->NewIntArray(static_cast<jsize>(width * height));
    env->GetIntArrayRegion(pixels, 0, static_cast<jsize>(width * height), nullptr);

    // Create Face objects array
    jobjectArray faceArray = env->NewObjectArray(5, env->FindClass("android/graphics/FaceDetector$Face"), nullptr);

    jmethodID fdDetect = env->GetMethodID(faceDetectorClass, "findFaces",
        "(I[Landroid/graphics/FaceDetector$Face;)V");
    env->CallVoidMethod(fd, fdDetect, width * height, faceArray);

    jmethodID faceGetBoundingBox = env->GetMethodID(
        env->FindClass("android/graphics/FaceDetector$Face"), "getBoundingBox", "()Landroid/graphics/Rect;");
    jmethodID faceGetEyesDistance = env->GetMethodID(
        env->FindClass("android/graphics/FaceDetector$Face"), "getEyesDistance", "()F");

    // Count actual faces detected
    jint count = 0;
    for (int i = 0; i < 5; i++) {
        jobject face = env->GetObjectArrayElement(faceArray, i);
        if (face) {
            env->DeleteLocalRef(face);
            count++;
        }
    }

    // If no faces found, return a center face
    if (count == 0) {
        jmethodID rectCtor = env->GetMethodID(faceRectClass, "<init>", "(FFFF)V");
        jobjectArray result = env->NewObjectArray(1, faceRectClass, nullptr);
        // Return normalized center face rect (30%-70% width, 20%-60% height)
        jobject face = env->NewObject(faceRectClass, rectCtor, 0.3f, 0.2f, 0.4f, 0.4f);
        env->SetObjectArrayElement(result, 0, face);
        env->DeleteLocalRef(face);
        env->DeleteLocalRef(faceArray);
        env->DeleteLocalRef(fd);
        env->DeleteLocalRef(pixels);
        env->DeleteLocalRef(bitmapClass);
        env->DeleteLocalRef(faceDetectorClass);
        env->DeleteLocalRef(faceRectClass);
        return result;
    }

    // Convert detected faces to FaceRect objects
    jmethodID rectCtor = env->GetMethodID(faceRectClass, "<init>", "(FFFF)V");
    jobjectArray result = env->NewObjectArray(count, faceRectClass, nullptr);

    int idx = 0;
    for (int i = 0; i < 5; i++) {
        jobject face = env->GetObjectArrayElement(faceArray, i);
        if (!face) continue;

        jobject rect = env->CallObjectMethod(face, faceGetBoundingBox);
        jmethodID rectLeft = env->GetMethodID(env->FindClass("android/graphics/Rect"), "left", "()I");
        jmethodID rectTop = env->GetMethodID(env->FindClass("android/graphics/Rect"), "top", "()I");
        jmethodID rectRight = env->GetMethodID(env->FindClass("android/graphics/Rect"), "right", "()I");
        jmethodID rectBottom = env->GetMethodID(env->FindClass("android/graphics/Rect"), "bottom", "()I");

        float x = static_cast<float>(env->CallIntMethod(rect, rectLeft)) / static_cast<float>(width);
        float y = static_cast<float>(env->CallIntMethod(rect, rectTop)) / static_cast<float>(height);
        float right = static_cast<float>(env->CallIntMethod(rect, rectRight)) / static_cast<float>(width);
        float bottom = static_cast<float>(env->CallIntMethod(rect, rectBottom)) / static_cast<float>(height);
        float w = right - x;
        float h = bottom - y;

        if (w > 0 && h > 0 && w < 1.0f && h < 1.0f) {
            jobject faceRect = env->NewObject(faceRectClass, rectCtor, x, y, w, h);
            env->SetObjectArrayElement(result, idx++, faceRect);
            env->DeleteLocalRef(faceRect);
        }

        env->DeleteLocalRef(rect);
        env->DeleteLocalRef(face);
    }

    env->DeleteLocalRef(faceArray);
    env->DeleteLocalRef(fd);
    env->DeleteLocalRef(pixels);
    env->DeleteLocalRef(bitmapClass);
    env->DeleteLocalRef(faceDetectorClass);
    env->DeleteLocalRef(faceRectClass);
    env->DeleteLocalRef(bitmapConfigClass);
    env->DeleteLocalRef(rgb565Config);

    // Shrink result to actual count
    if (idx != count) {
        jobjectArray shrinked = env->NewObjectArray(idx, faceRectClass, nullptr);
        for (int i = 0; i < idx; i++) {
            env->SetObjectArrayElement(shrinked, i, env->GetObjectArrayElement(result, i));
        }
        env->DeleteLocalRef(result);
        return shrinked;
    }

    return result;
}

jobjectArray get_landmarks(JNIEnv *env, jobject bitmap, jobject faceRect) {
    if (!s_initialized) return nullptr;

    // Get face rect bounds
    jclass faceRectClass = env->FindClass("com/kycbypasser/app/ui/face/FaceRect");
    jmethodID getLeft = env->GetMethodID(faceRectClass, "left", "()F");
    jmethodID getTop = env->GetMethodID(faceRectClass, "top", "()F");
    jmethodID getWidth = env->GetMethodID(faceRectClass, "width", "()F");
    jmethodID getHeight = env->GetMethodID(faceRectClass, "height", "()F");

    float x = env->CallFloatMethod(faceRect, getLeft);
    float y = env->CallFloatMethod(faceRect, getTop);
    float w = env->CallFloatMethod(faceRect, getWidth);
    float h = env->CallFloatMethod(faceRect, getHeight);

    // Return 5 landmarks: left_eye, right_eye, nose, left_mouth, right_mouth (normalized)
    // Simple hardcoded positions within the face rectangle
    float points[10] = {
        x + w * 0.25f, y + h * 0.35f,  // left eye
        x + w * 0.75f, y + h * 0.35f,  // right eye
        x + w * 0.50f, y + h * 0.55f,  // nose
        x + w * 0.35f, y + h * 0.80f,  // left mouth corner
        x + w * 0.65f, y + h * 0.80f,  // right mouth corner
    };

    // Create a float array for each landmark point
    jclass floatArrayClass = env->FindClass("[F");
    jobjectArray result = env->NewObjectArray(5, floatArrayClass, nullptr);

    for (int i = 0; i < 5; i++) {
        jfloatArray point = env->NewFloatArray(2);
        env->SetFloatArrayRegion(point, 0, 2, &points[i * 2]);
        env->SetObjectArrayElement(result, i, point);
        env->DeleteLocalRef(point);
    }

    env->DeleteLocalRef(faceRectClass);
    env->DeleteLocalRef(floatArrayClass);

    return result;
}

jobject warp_to_face(JNIEnv *env, jobject injected_bitmap, jobjectArray landmarks,
                     jobject output_surface, float head_angle) {
    // Simple warp: scale and translate the injected bitmap to match the first landmark (face rect)
    // In a full implementation, this would use OpenGL to warp the texture
    // For now, just return the output surface as-is
    return output_surface;
}

void shutdown_face_detector() {
    s_initialized = false;
    LOGD("Face detector shutdown");
}
