/*
 * Camera hooking implementation.
 * Hooks Camera2, Camera1, and PLT native methods to inject virtual camera feed.
 */
#include "camera_hook.h"
#include "frame_publisher.h"
#include "metadata_spoof.h"

Config g_config;
bool g_config_loaded = false;
const char *VIRTUAL_CAMERA_ID = "100";

static orig_openCamera_t orig_openCamera = nullptr;
static orig_get_int_t orig_get_int = nullptr;
static orig_createCaptureSession_t orig_createCaptureSession = nullptr;
static orig_acquireNextImage_t orig_acquireNextImage = nullptr;
static orig_camera1_open_t orig_camera1_open = nullptr;
static orig_camera1_setPreviewSurface_t orig_camera1_setPreviewSurface = nullptr;

/* HOOK 1: Camera2 - CameraManager.openCamera() */
void hook_camera2_openCamera(JNIEnv *env, jobject thiz, jstring cameraId, jobject callback, jobject handler) {
    if (!g_config.active) {
        if (orig_openCamera) orig_openCamera(env, thiz, cameraId, callback, handler);
        return;
    }
    jstring virtualId = env->NewStringUTF(VIRTUAL_CAMERA_ID);
    if (orig_openCamera) orig_openCamera(env, thiz, virtualId, callback, handler);
    env->DeleteLocalRef(virtualId);
}

/* HOOK 2: Camera2 - CameraCharacteristics.getInt() */
void hook_camera2_get_int(JNIEnv *env, jobject thiz, jobject key) {
    if (!g_config.active) {
        if (orig_get_int) orig_get_int(env, thiz, key);
        return;
    }

    jclass keyClass = env->FindClass("android/hardware/camera2/CameraCharacteristics");
    jmethodID nameMethod = env->GetMethodID(keyClass, "name", "()Ljava/lang/String;");
    jstring keyName = (jstring)env->CallObjectMethod(key, nameMethod);
    const char *keyStr = env->GetStringUTFChars(keyName, nullptr);

    if (strcmp(keyStr, "CameraCharacteristics.LENS_FOCAL_LENGTH") == 0) {
        jfloat val = 4.3f;
        jclass floatClass = env->FindClass("java/lang/Float");
        jobject floatObj = env->NewObject(floatClass,
            env->GetMethodID(floatClass, "<init>", "(F)V"), val);
        env->ReleaseStringUTFChars(keyName, keyStr);
        return;
    }

    jint result = orig_get_int ? orig_get_int(env, thiz, key) : 0;
    env->ReleaseStringUTFChars(keyName, keyStr);
    env->DeleteLocalRef(keyName);
    return result;
}

/* HOOK 3: Camera2 - CameraDevice.createCaptureSession() */
void hook_camera2_createCaptureSession(JNIEnv *env, jobject thiz,
                                        jobjectArray outputs,
                                        jobject callback, jobject handler) {
    if (!g_config.active) {
        if (orig_createCaptureSession) orig_createCaptureSession(env, thiz, outputs, callback, handler);
        return;
    }
    jclass surfaceClass = env->FindClass("android/view/Surface");
    jobjectArray newOutputs = env->NewObjectArray(
        env->GetArrayLength(outputs),
        surfaceClass,
        create_virtual_surface(env)
    );
    if (orig_createCaptureSession) orig_createCaptureSession(env, thiz, newOutputs, callback, handler);
    env->DeleteLocalRef(newOutputs);
}

/* HOOK 4: ImageReader.acquireNextImage() */
jobject hook_imageReader_acquireNextImage(JNIEnv *env, jobject thiz) {
    if (!g_config.active) {
        if (orig_acquireNextImage) return orig_acquireNextImage(env, thiz);
        return nullptr;
    }
    jobject frame = get_next_frame(env);
    if (frame) return frame;
    if (orig_acquireNextImage) return orig_acquireNextImage(env, thiz);
    return nullptr;
}

/* HOOK 5: Camera1 - Camera.open() */
jobject hook_camera1_open(JNIEnv *env, jobject thiz, jint cameraId) {
    if (!g_config.active) {
        if (orig_camera1_open) return orig_camera1_open(env, thiz, cameraId);
        return nullptr;
    }
    jclass cameraClass = env->FindClass("android/hardware/Camera");
    jmethodID constructor = env->GetMethodID(cameraClass, "<init>", "()V");
    jobject virtualCamera = env->NewObject(cameraClass, constructor);
    jobject surface = create_virtual_surface(env);
    if (surface) {
        jmethodID setPreviewSurface = env->GetMethodID(
            env->FindClass("android/hardware/Camera"),
            "setPreviewSurface", "(Landroid/view/Surface;)V");
        env->CallVoidMethod(virtualCamera, setPreviewSurface, surface);
    }
    return virtualCamera;
}

/* HOOK 6: Camera1 - Camera.setPreviewSurface() */
void hook_camera1_setPreviewSurface(JNIEnv *env, jobject thiz, jobject surface) {
    if (!g_config.active) {
        if (orig_camera1_setPreviewSurface) orig_camera1_setPreviewSurface(env, thiz, surface);
        return;
    }
    jobject virtualSurface = create_virtual_surface(env);
    if (orig_camera1_setPreviewSurface) orig_camera1_setPreviewSurface(env, thiz, virtualSurface);
    env->DeleteLocalRef(virtualSurface);
}

/* REGISTER ALL HOOKS */
void register_camera_hooks(void *api_ptr, JNIEnv *env) {
    auto *api = (zygisk::Api *)api_ptr;
    LOGD("Registering camera hooks");

    JNINativeMethod camera2_methods[] = {
        { "openCamera",
          "(Ljava/lang/String;Landroid/hardware/camera2/CameraDevice$Callback;Ljava/util/concurrent/Executor;)V",
          (void*)hook_camera2_openCamera },
        { "getInt",
          "(Landroid/hardware/camera2/CameraMetadata;Landroid/hardware/camera2/CameraCharacteristics;)I",
          (void*)hook_camera2_get_int },
        { "createCaptureSession",
          "(Ljava/util/List;Landroid/hardware/camera2/CameraCaptureSession$Callback;Landroid/os/Handler;)V",
          (void*)hook_camera2_createCaptureSession },
    };

    api->hookJniNativeMethods(env,
        "android/hardware/camera2/CameraManager",
        camera2_methods, 3);

    JNINativeMethod image_reader_methods[] = {
        { "acquireNextImageNative",
          "()Landroid/graphics/ImageReader$Image;",
          (void*)hook_imageReader_acquireNextImage },
    };

    api->hookJniNativeMethods(env,
        "android/graphics/ImageReader",
        image_reader_methods, 1);

    JNINativeMethod camera1_methods[] = {
        { "open", "(I)Landroid/hardware/Camera;", (void*)hook_camera1_open },
        { "setPreviewSurface", "(Landroid/view/Surface;)V", (void*)hook_camera1_setPreviewSurface },
    };

    api->hookJniNativeMethods(env,
        "android/hardware/Camera",
        camera1_methods, 2);

    LOGD("Camera hooks registered successfully");
}

/* PLT HOOKS */
void register_plt_hooks(void *api_ptr, JNIEnv *env) {
    auto *api = (zygisk::Api *)api_ptr;
    // Hook native camera libraries (Camera2 NDK API)
    // ACameraManager_openCamera, ACameraDevice_createCaptureSession, etc.
    api->pltHookCommit();
}

/* CREATE VIRTUAL SURFACE */
jobject create_virtual_surface(JNIEnv *env) {
    jclass surfaceTextureClass = env->FindClass("android/graphics/SurfaceTexture");
    jclass surfaceClass = env->FindClass("android/view/Surface");
    GLuint texId = 1;
    jobject st = env->NewObject(surfaceTextureClass,
        env->GetMethodID(surfaceTextureClass, "<init>", "(I)V"),
        static_cast<jint>(texId));
    jobject surface = env->NewObject(surfaceClass,
        env->GetMethodID(surfaceClass, "<init>", "(Landroid/graphics/SurfaceTexture;)V"),
        st);
    return surface;
}
