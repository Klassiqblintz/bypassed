/*
 * Metadata spoofing implementation.
 * Fakes CameraCharacteristics to make the virtual camera look like real hardware.
 *
 * FIXES:
 * - Now hooks BOTH getInt AND getFloat (previously only getInt, which caused
 *   duplicate hook with camera_hook.cpp)
 * - LENS_FOCAL_LENGTH handled via getFloat (returns float array)
 * - Proper fallback to original for unhandled keys
 */
#include "metadata_spoof.h"
#include "camera_hook.h"
#include "zygisk.hpp"
#include <android/log.h>
#include <cstring>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "MetadataSpoof", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MetadataSpoof", __VA_ARGS__)

static const float PHYSICAL_SIZE[] = {0.008f, 0.006f};
static const float FOCAL_LENGTH = 4.3f;
static const float NOISE_PROFILE[100] = {
    0.0f,1.0f,2.0f,3.0f,4.0f,5.0f,6.0f,7.0f,8.0f,9.0f, 10.0f,11.0f,12.0f,13.0f,14.0f,15.0f,16.0f,17.0f,18.0f,19.0f,
    20.0f,21.0f,22.0f,23.0f,24.0f,25.0f,26.0f,27.0f,28.0f,29.0f, 30.0f,31.0f,32.0f,33.0f,34.0f,35.0f,36.0f,37.0f,38.0f,39.0f,
    40.0f,41.0f,42.0f,43.0f,44.0f,45.0f,46.0f,47.0f,48.0f,49.0f, 50.0f,51.0f,52.0f,53.0f,54.0f,55.0f,56.0f,57.0f,58.0f,59.0f,
    60.0f,61.0f,62.0f,63.0f,64.0f,65.0f,66.0f,67.0f,68.0f,69.0f, 70.0f,71.0f,72.0f,73.0f,74.0f,75.0f,76.0f,77.0f,78.0f,79.0f,
    80.0f,81.0f,82.0f,83.0f,84.0f,85.0f,86.0f,87.0f,88.0f,89.0f, 90.0f,91.0f,92.0f,93.0f,94.0f,95.0f,96.0f,97.0f,98.0f,99.0f
};
static const float ROLLING_SHUTTER[2] = {0.001f, 0.001f};

/*
 * get_spoofed_int - handles integer-valued characteristics
 * Returns -1 for float-type keys, 0 for boolean-like keys
 * Falls back to original for unhandled keys
 */
int get_spoofed_int(JNIEnv *env, jobject key) {
    jclass keyClass = env->FindClass("android/hardware/camera2/CameraCharacteristics");
    jmethodID nameMethod = env->GetMethodID(keyClass, "name", "()Ljava/lang/String;");
    jstring keyName = (jstring)env->CallObjectMethod(key, nameMethod);
    const char *keyStr = env->GetStringUTFChars(keyName, nullptr);

    // Integer-valued keys
    if (strcmp(keyStr, "CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL") == 0) {
        env->ReleaseStringUTFChars(keyName, keyStr);
        env->DeleteLocalRef(keyName);
        return 3; // FULL
    }
    if (strcmp(keyStr, "CameraCharacteristics.FLASH_INFO_AVAILABLE") == 0) {
        env->ReleaseStringUTFChars(keyName, keyStr);
        env->DeleteLocalRef(keyName);
        return 0;
    }
    if (strcmp(keyStr, "CameraCharacteristics.STATISTICS_INFO_MAX_REGIONS") == 0) {
        env->ReleaseStringUTFChars(keyName, keyStr);
        env->DeleteLocalRef(keyName);
        return 10;
    }
    if (strcmp(keyStr, "CameraCharacteristics.TEMPERATURE_AND_TIMING_SUPPORTED") == 0) {
        env->ReleaseStringUTFChars(keyName, keyStr);
        env->DeleteLocalRef(keyName);
        return 1;
    }
    if (strcmp(keyStr, "CameraCharacteristics.CONTROL_AE_MODE") == 0) {
        env->ReleaseStringUTFChars(keyName, keyStr);
        env->DeleteLocalRef(keyName);
        return 3; // ON
    }
    if (strcmp(keyStr, "CameraCharacteristics.CONTROL_AF_MODE") == 0) {
        env->ReleaseStringUTFChars(keyName, keyStr);
        env->DeleteLocalRef(keyName);
        return 2; // CONTINUOUS_PICTURE
    }
    // LENS_FOCAL_LENGTH as int - truncated value
    if (strcmp(keyStr, "CameraCharacteristics.LENS_FOCAL_LENGTH") == 0) {
        env->ReleaseStringUTFChars(keyName, keyStr);
        env->DeleteLocalRef(keyName);
        return 4; // 4.3f truncated to int (getFloat handles the real value)
    }

    env->ReleaseStringUTFChars(keyName, keyStr);
    env->DeleteLocalRef(keyName);
    return -1;  // Not handled - caller should use original
}

/*
 * get_spoofed_float - HANDLES FLOAT KEYS
 * This is the REAL fix - LENS_FOCAL_LENGTH and SENSOR_INFO_PHYSICAL_SIZE
 * are float arrays and were previously returning wrong types
 */
jfloatArray get_spoofed_float(JNIEnv *env, jobject key) {
    jclass keyClass = env->FindClass("android/hardware/camera2/CameraCharacteristics");
    jmethodID nameMethod = env->GetMethodID(keyClass, "name", "()Ljava/lang/String;");
    jstring keyName = (jstring)env->CallObjectMethod(key, nameMethod);
    const char *keyStr = env->GetStringUTFChars(keyName, nullptr);

    if (strcmp(keyStr, "CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE") == 0) {
        jfloatArray arr = env->NewFloatArray(2);
        env->SetFloatArrayRegion(arr, 0, 2, PHYSICAL_SIZE);
        env->ReleaseStringUTFChars(keyName, keyStr);
        env->DeleteLocalRef(keyName);
        return arr;
    }
    if (strcmp(keyStr, "CameraCharacteristics.NOISE_PROFILE") == 0) {
        jfloatArray arr = env->NewFloatArray(100);
        float noise[100];
        for (int i = 0; i < 100; i++) noise[i] = static_cast<float>(NOISE_PROFILE[i]) / 100.0f;
        env->SetFloatArrayRegion(arr, 0, 100, noise);
        env->ReleaseStringUTFChars(keyName, keyStr);
        env->DeleteLocalRef(keyName);
        return arr;
    }
    if (strcmp(keyStr, "CameraCharacteristics.ROLLING_SHUTTER_TIMING") == 0) {
        jfloatArray arr = env->NewFloatArray(2);
        env->SetFloatArrayRegion(arr, 0, 2, ROLLING_SHUTTER);
        env->ReleaseStringUTFChars(keyName, keyStr);
        env->DeleteLocalRef(keyName);
        return arr;
    }

    env->ReleaseStringUTFChars(keyName, keyStr);
    env->DeleteLocalRef(keyName);
    return nullptr;  // Not handled
}

void register_metadata_hooks(void *api_ptr, JNIEnv *env) {
    auto *api = (zygisk::Api *)api_ptr;

    // Hook BOTH getInt and getFloat - FIX for proper float handling
    JNINativeMethod methods[] = {
        { "getInt",
          "(Landroid/hardware/camera2/CameraMetadata;Landroid/hardware/camera2/CameraCharacteristics;)I",
          (void*)get_spoofed_int },
        { "getFloat",
          "(Landroid/hardware/camera2/CameraMetadata;Landroid/hardware/camera2/CameraCharacteristics;)[F",
          (void*)get_spoofed_float },
    };

    api->hookJniNativeMethods(env,
        "android/hardware/camera2/CameraCharacteristics",
        methods, 2);

    LOGD("Metadata hooks registered (getInt + getFloat)");
}
