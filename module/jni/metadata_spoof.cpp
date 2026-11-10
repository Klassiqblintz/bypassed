/*
 * Metadata spoofing implementation.
 * Fakes CameraCharacteristics to make the virtual camera look like real hardware.
 *
 * Spoofed: SENSOR_INFO_PHYSICAL_SIZE, LENS_FOCAL_LENGTH,
 * NOISE_PROFILE, ROLLING_SHUTTER_TIMING, and various control modes.
 */
#include "metadata_spoof.h"
#include "camera_hook.h"
#include <android/log.h>
#include <cstring>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "MetadataSpoof", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MetadataSpoof", __VA_ARGS__)

static const float PHYSICAL_SIZE[] = {0.008f, 0.006f};
static const float FOCAL_LENGTH = 4.3f;
static const int32_t NOISE_PROFILE[100] = {
    0,1,2,3,4,5,6,7,8,9, 10,11,12,13,14,15,16,17,18,19,
    20,21,22,23,24,25,26,27,28,29, 30,31,32,33,34,35,36,37,38,39,
    40,41,42,43,44,45,46,47,48,49, 50,51,52,53,54,55,56,57,58,59,
    60,61,62,63,64,65,66,67,68,69, 70,71,72,73,74,75,76,77,78,79,
    80,81,82,83,84,85,86,87,88,89, 90,91,92,93,94,95,96,97,98,99
};
static const int32_t ROLLING_SHUTTER[2] = {1, 1};

int get_spoofed_int(jobject key) {
    JNIEnv *env = nullptr;
    if (!env) return 0;

    jclass keyClass = env->FindClass("android/hardware/camera2/CameraCharacteristics");
    jmethodID nameMethod = env->GetMethodID(keyClass, "name", "()Ljava/lang/String;");
    jstring keyName = (jstring)env->CallObjectMethod(key, nameMethod);
    const char *keyStr = env->GetStringUTFChars(keyName, nullptr);

    if (strcmp(keyStr, "CameraCharacteristics.LENS_FOCAL_LENGTH") == 0) {
        return static_cast<jint>(FOCAL_LENGTH);
    }
    if (strcmp(keyStr, "CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL") == 0) {
        return 3; // FULL
    }
    if (strcmp(keyStr, "CameraCharacteristics.FLASH_INFO_AVAILABLE") == 0) {
        return 0;
    }
    if (strcmp(keyStr, "CameraCharacteristics.STATISTICS_INFO_MAX_REGIONS") == 0) {
        return 10;
    }
    if (strcmp(keyStr, "CameraCharacteristics.TEMPERATURE_AND_TIMING_SUPPORTED") == 0) {
        return 1;
    }

    env->ReleaseStringUTFChars(keyName, keyStr);
    env->DeleteLocalRef(keyName);
    return 0;
}

jfloatArray get_spoofed_float(jobject key) {
    JNIEnv *env = nullptr;
    if (!env) return nullptr;

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
    return nullptr;
}

void register_metadata_hooks(void *api_ptr, JNIEnv *env) {
    auto *api = (zygisk::Api *)api_ptr;

    JNINativeMethod methods[] = {
        { "getInt",
          "(Landroid/hardware/camera2/CameraMetadata;Landroid/hardware/camera2/CameraCharacteristics;)I",
          (void*)hook_camera2_get_int },
    };

    api->hookJniNativeMethods(env,
        "android/hardware/camera2/CameraCharacteristics",
        methods, 1);

    LOGD("Metadata hooks registered");
}
