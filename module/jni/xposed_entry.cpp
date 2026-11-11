/*
 * Xposed module entry for KYC Bypasser (VirtualXposed compatible).
 * Uses Xposed Framework to hook Camera2/Camera1 in target apps.
 * Works inside VirtualXposed sandbox (no root needed).
 *
 * This replaces the Zygisk module for non-root use.
 * The C++ native library is loaded per-app instead of system-wide.
 */
#include <jni.h>
#include <android/log.h>
#include <string>
#include <cstring>
#include <fstream>
#include <regex>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "KYC_XPOSED", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "KYC_XPOSED", __VA_ARGS__)

/* ===================== CONFIG ===================== */

enum InjectMode {
    MODE_STATIC = 0,
    MODE_VIDEO  = 1,
    MODE_REALTIME = 2
};

struct Config {
    bool active = false;
    int mode = MODE_STATIC;
    char image_path[512] = "";
    char video_path[512] = "";
    bool hide_module = true;
    bool auto_liveness = true;
    int fps = 30;
};

/* ===================== XPOSED HOOKS ===================== */

/* These are the Java-side hooks that replace the Zygisk hooks */
/* We hook Camera2 and Camera1 APIs directly from Java */

extern "C" {

/*
 * Xposed module initialization entry point.
 * Called by Xposed framework when the module is loaded.
 */
void XposedInit(JNIEnv *env) {
    LOGD("KYC Bypasser Xposed module initialized");
}

/*
 * Hook: Camera2 - CameraManager.openCamera
 * Replaces the camera ID with our virtual camera
 */
jobject hook_camera2_openCamera(
    JNIEnv *env,
    jobject thiz,
    jstring cameraId,
    jobject callback,
    jobject handler)
{
    /* Check if we should activate */
    /* Read config from module directory */
    FILE *f = fopen("/data/data/io.va.exposed/vxp/data/app/com.kycbypasser.xposed/"
                    "files/camera_config.json", "r");
    if (!f) {
        /* Fallback: use shared storage path */
        f = fopen("/storage/emulated/0/kyc_bypasser/config.json", "r");
    }

    if (!f) {
        /* No config - let original call through */
        jclass cls = env->FindClass("android/hardware/camera2/CameraManager");
        if (!cls) return nullptr;
        jmethodID orig = env->GetMethodID(cls, "openCamera",
            "(Ljava/lang/String;Landroid/hardware/camera2/CameraDevice$Callback;"
            "Ljava/util/concurrent/Executor;)V");
        if (orig) {
            env->CallVoidMethod(thiz, orig, cameraId, callback, handler);
        }
        return nullptr;
    }

    char buffer[4096];
    size_t total = 0;
    while (!feof(f) && total < sizeof(buffer) - 1) {
        total += fread(buffer + total, 1, sizeof(buffer) - total - 1, f);
    }
    buffer[total] = '\0';
    fclose(f);

    std::string json(buffer);
    bool active = false;
    std::regex active_re("\"active\"\\s*:\\s*(true|false)");
    std::smatch m;
    if (std::regex_search(json, m, active_re)) {
        active = (m[1] == "true");
    }

    if (!active) {
        /* Let original call through */
        jclass cls = env->FindClass("android/hardware/camera2/CameraManager");
        if (cls) {
            jmethodID orig = env->GetMethodID(cls, "openCamera",
                "(Ljava/lang/String;Landroid/hardware/camera2/CameraDevice$Callback;"
                "Ljava/util/concurrent/Executor;)V");
            if (orig) {
                env->CallVoidMethod(thiz, orig, cameraId, callback, handler);
            }
        }
        return nullptr;
    }

    /* ACTIVE: Replace camera ID with virtual camera ID */
    const char *virtual_id = "100";
    jstring newCameraId = env->NewStringUTF(virtual_id);

    jclass cls = env->FindClass("android/hardware/camera2/CameraManager");
    if (cls) {
        jmethodID orig = env->GetMethodID(cls, "openCamera",
            "(Ljava/lang/String;Landroid/hardware/camera2/CameraDevice$Callback;"
            "Ljava/util/concurrent/Executor;)V");
        if (orig) {
            env->CallVoidMethod(thiz, orig, newCameraId, callback, handler);
        }
    }

    env->DeleteLocalRef(newCameraId);
    LOGD("Camera2 openCamera hooked: using virtual camera");
    return nullptr;
}

/*
 * Hook: Camera2 - CameraDevice.createCaptureSession
 * Replaces first output surface with our virtual surface
 */
void hook_camera2_createCaptureSession(
    JNIEnv *env,
    jobject thiz,
    jobject outputs,
    jobject callback,
    jobject handler)
{
    jclass listClass = env->FindClass("java/util/List");
    if (!listClass) return;

    /* Get original output list size */
    jmethodID sizeMethod = env->GetMethodID(listClass, "size", "()I");
    int count = env->CallIntMethod(outputs, sizeMethod);

    if (count <= 0) return;

    /* Create new list */
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListCtor = env->GetMethodID(arrayListClass, "<init>", "(I)V");
    jobject newOutputs = env->NewObject(arrayListClass, arrayListCtor, count);

    jmethodID addMethod = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jmethodID getMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

    for (int i = 0; i < count; i++) {
        if (i == 0) {
            /* FIRST output = virtual surface */
            jclass surfaceTextureClass = env->FindClass("android/graphics/SurfaceTexture");
            jclass surfaceClass = env->FindClass("android/view/Surface");
            if (surfaceTextureClass && surfaceClass) {
                /* Create SurfaceTexture with unique texture ID */
                jobject st = env->NewObject(surfaceTextureClass,
                    env->GetMethodID(surfaceTextureClass, "<init>", "(I)V"), 1);
                jobject virtualSurface = env->NewObject(surfaceClass,
                    env->GetMethodID(surfaceClass, "<init>", "(Landroid/graphics/SurfaceTexture;)V"),
                    st);
                env->CallBooleanMethod(newOutputs, addMethod, virtualSurface);
                env->DeleteLocalRef(virtualSurface);
                env->DeleteLocalRef(st);
                env->DeleteLocalRef(surfaceTextureClass);
                env->DeleteLocalRef(surfaceClass);
                LOGD("createCaptureSession: replaced output[0] with virtual surface");
            }
        } else {
            /* REST = original outputs */
            jobject origSurface = env->CallObjectMethod(outputs, getMethod, i);
            env->CallBooleanMethod(newOutputs, addMethod, origSurface);
            env->DeleteLocalRef(origSurface);
        }
    }

    /* Call original with new outputs */
    jclass deviceClass = env->FindClass("android/hardware/camera2/CameraDevice");
    if (deviceClass) {
        jmethodID orig = env->GetMethodID(deviceClass, "createCaptureSession",
            "(Ljava/util/List;Landroid/hardware/camera2/CameraCaptureSession$Callback;"
            "Landroid/os/Handler;)V");
        if (orig) {
            env->CallVoidMethod(thiz, orig, newOutputs, callback, handler);
        }
    }

    env->DeleteLocalRef(newOutputs);
    env->DeleteLocalRef(listClass);
    env->DeleteLocalRef(arrayListClass);
}

/*
 * Hook: Camera1 - Camera.open
 * Returns a virtual Camera object
 */
jobject hook_camera1_open(JNIEnv *env, jclass thiz, jint cameraId) {
    jclass cameraClass = env->FindClass("android/hardware/Camera");
    if (!cameraClass) return nullptr;

    jmethodID constructor = env->GetMethodID(cameraClass, "<init>", "()V");
    jobject virtualCamera = env->NewObject(cameraClass, constructor);

    /* Set virtual preview surface */
    jclass surfaceTextureClass = env->FindClass("android/graphics/SurfaceTexture");
    jclass surfaceClass = env->FindClass("android/view/Surface");
    if (surfaceTextureClass && surfaceClass) {
        jobject st = env->NewObject(surfaceTextureClass,
            env->GetMethodID(surfaceTextureClass, "<init>", "(I)V"), 1);
        jobject surface = env->NewObject(surfaceClass,
            env->GetMethodID(surfaceClass, "<init>", "(Landroid/graphics/SurfaceTexture;)V"),
            st);
        jmethodID setPreviewSurface = env->GetMethodID(
            cameraClass, "setPreviewSurface", "(Landroid/view/Surface;)V");
        env->CallVoidMethod(virtualCamera, setPreviewSurface, surface);
        env->DeleteLocalRef(surface);
        env->DeleteLocalRef(st);
    }

    env->DeleteLocalRef(surfaceTextureClass);
    env->DeleteLocalRef(surfaceClass);
    LOGD("Camera1 open() hooked");
    return virtualCamera;
}

/*
 * Hook: Camera1 - Camera.setPreviewSurface
 * Replaces preview surface with virtual surface
 */
void hook_camera1_setPreviewSurface(JNIEnv *env, jobject thiz, jobject surface) {
    jclass cameraClass = env->FindClass("android/hardware/Camera");
    if (!cameraClass) return;

    jclass surfaceTextureClass = env->FindClass("android/graphics/SurfaceTexture");
    jclass surfaceClass = env->FindClass("android/view/Surface");
    if (surfaceTextureClass && surfaceClass) {
        jobject st = env->NewObject(surfaceTextureClass,
            env->GetMethodID(surfaceTextureClass, "<init>", "(I)V"), 1);
        jobject virtualSurface = env->NewObject(surfaceClass,
            env->GetMethodID(surfaceClass, "<init>", "(Landroid/graphics/SurfaceTexture;)V"),
            st);
        jmethodID setPreviewSurface = env->GetMethodID(cameraClass,
            "setPreviewSurface", "(Landroid/view/Surface;)V");
        env->CallVoidMethod(thiz, setPreviewSurface, virtualSurface);
        env->DeleteLocalRef(virtualSurface);
        env->DeleteLocalRef(st);
    }

    env->DeleteLocalRef(surfaceTextureClass);
    env->DeleteLocalRef(surfaceClass);
}

/*
 * Hook: CameraCharacteristics.getInt
 * Returns spoofed values for camera metadata
 */
jint hook_camera2_getInt(JNIEnv *env, jobject thiz, jobject key) {
    jclass keyClass = env->FindClass("android/hardware/camera2/CameraCharacteristics");
    if (!keyClass) return -1;

    jmethodID nameMethod = env->GetMethodID(keyClass, "name", "()Ljava/lang/String;");
    jstring keyName = (jstring)env->CallObjectMethod(key, nameMethod);
    const char *keyStr = env->GetStringUTFChars(keyName, nullptr);

    /* Integer-valued camera characteristics */
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
    if (strcmp(keyStr, "CameraCharacteristics.LENS_FOCAL_LENGTH") == 0) {
        env->ReleaseStringUTFChars(keyName, keyStr);
        env->DeleteLocalRef(keyName);
        return 4; // 4.3f truncated
    }

    env->ReleaseStringUTFChars(keyName, keyStr);
    env->DeleteLocalRef(keyName);

    /* Call original for unhandled keys */
    jmethodID orig = env->GetMethodID(keyClass, "getInt",
        "(Landroid/hardware/camera2/CameraCharacteristics;)I");
    if (orig) {
        return env->CallIntMethod(thiz, orig, key);
    }
    return 0;
}

/*
 * Hook: CameraCharacteristics.getFloat
 * Returns spoofed values for float camera metadata
 */
jfloatArray hook_camera2_getFloat(JNIEnv *env, jobject thiz, jobject key) {
    jclass keyClass = env->FindClass("android/hardware/camera2/CameraCharacteristics");
    if (!keyClass) return nullptr;

    jmethodID nameMethod = env->GetMethodID(keyClass, "name", "()Ljava/lang/String;");
    jstring keyName = (jstring)env->CallObjectMethod(key, nameMethod);
    const char *keyStr = env->GetStringUTFChars(keyName, nullptr);

    jfloatArray result = nullptr;

    if (strcmp(keyStr, "CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE") == 0) {
        result = env->NewFloatArray(2);
        float data[] = {0.008f, 0.006f};
        env->SetFloatArrayRegion(result, 0, 2, data);
    }
    if (strcmp(keyStr, "CameraCharacteristics.ROLLING_SHUTTER_TIMING") == 0) {
        result = env->NewFloatArray(2);
        float data[] = {0.001f, 0.001f};
        env->SetFloatArrayRegion(result, 0, 2, data);
    }

    env->ReleaseStringUTFChars(keyName, keyStr);
    env->DeleteLocalRef(keyName);

    return result;
}

/* ===================== NATIVE LIB LOADING ===================== */

/*
 * Load our native library into the target app's classloader.
 * This is the key difference from Zygisk: we load per-app instead of system-wide.
 */
void load_native_library(JNIEnv *env, jobject thiz) {
    /* Try VirtualXposed internal path first */
    const char *paths[] = {
        "/data/data/io.va.exposed/vxp/data/app/com.kycbypasser.xposed/"
        "lib/arm64-v8a/libkyc_bypasser.so",
        "/data/data/com.kycbypasser.xposed/lib/arm64-v8a/libkyc_bypasser.so",
        "/data/adb/modules/kyc_bypasser/lib/arm64-v8a/libkyc_bypasser.so",
        nullptr
    };

    for (int i = 0; paths[i] != nullptr; i++) {
        FILE *f = fopen(paths[i], "r");
        if (f) {
            fclose(f);
            try {
                env->CallStaticVoidMethod(
                    env->FindClass("java/lang/System"),
                    env->GetStaticMethodID(
                        env->FindClass("java/lang/System"),
                        "loadLibrary", "(Ljava/lang/String;)V"),
                    env->NewStringUTF(paths[i]));
                LOGD("Native library loaded from: %s", paths[i]);
                return;
            } catch (...) {
                LOGE("Failed to load native library from: %s", paths[i]);
            }
        }
    }
    LOGD("Native library not found, using Java-only hooks");
}

/* ===================== XPOSED BRIDGE ===================== */

/*
 * Xposed module info - required for the module to be recognized by Xposed.
 * Written in C so it can be found via nm/objdump.
 */
__attribute__((visibility("default")))
extern "C" void android_main(JNIEnv *env) {
    XposedInit(env);
    LOGD("KYC Bypasser Xposed module - android_main entry");
}

} // extern "C"
