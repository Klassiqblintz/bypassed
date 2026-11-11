/*
 * Camera hooking implementation.
 * Hooks Camera2, Camera1, and PLT native methods to inject virtual camera feed.
 *
 * FIXES:
 * - Each create_virtual_surface() gets unique texture ID via atomic counter
 * - openCamera keeps ORIGINAL camera ID (don't replace with "100" that doesn't exist)
 * - createCaptureSession replaces only the FIRST output surface (not all)
 * - ImageReader returns proper Image object wrapping our surface
 * - Global surface reference for frame drawing
 */
#include "camera_hook.h"
#include "frame_publisher.h"
#include "metadata_spoof.h"
#include "zygisk.hpp"
#include <atomic>

Config g_config;
bool g_config_loaded = false;
const char *VIRTUAL_CAMERA_ID = "100";

/* Original function pointer types (filled by hooks) */
orig_openCamera_t orig_openCamera = nullptr;
orig_get_int_t orig_get_int = nullptr;
orig_createCaptureSession_t orig_createCaptureSession = nullptr;
orig_acquireNextImage_t orig_acquireNextImage = nullptr;
orig_camera1_open_t orig_camera1_open = nullptr;
orig_camera1_setPreviewSurface_t orig_camera1_setPreviewSurface = nullptr;

/* Thread-safe texture ID counter */
std::atomic<unsigned int> g_texture_counter(1);

/* GLOBAL VIRTUAL SURFACE - shared across all hooks for drawing */
static jobject g_virtual_surface = nullptr;

/*
 * create_virtual_surface() - each call gets unique texture ID
 * Also stores as global ref for frame drawing
 */
jobject create_virtual_surface(JNIEnv *env) {
    jclass surfaceTextureClass = env->FindClass("android/graphics/SurfaceTexture");
    jclass surfaceClass = env->FindClass("android/view/Surface");
    if (!surfaceTextureClass || !surfaceClass) return nullptr;

    /* Each surface gets a unique texture ID from atomic counter */
    unsigned int texId = g_texture_counter.fetch_add(1);
    LOGD("Creating virtual surface with texture ID %u", texId);

    jobject st = env->NewObject(surfaceTextureClass,
        env->GetMethodID(surfaceTextureClass, "<init>", "(I)V"),
        static_cast<jint>(texId));
    jobject surface = env->NewObject(surfaceClass,
        env->GetMethodID(surfaceClass, "<init>", "(Landroid/graphics/SurfaceTexture;)V"),
        st);

    /* STORE as global ref for frame drawing */
    if (g_virtual_surface) {
        env->DeleteGlobalRef(g_virtual_surface);
    }
    g_virtual_surface = env->NewGlobalRef(surface);

    return surface;
}

/*
 * DRAW FRAME ONTO VIRTUAL SURFACE
 * Each frame: lock canvas, draw our image, unlock and post
 * This is called from hook_imageReader_acquireNextImage
 */
static void draw_frame_on_surface(JNIEnv *env) {
    if (!g_virtual_surface || !s_bitmap) return;

    jclass surfaceClass = env->FindClass("android/view/Surface");
    jmethodID lockCanvas = env->GetMethodID(
        surfaceClass, "lockCanvas", "(Landroid/graphics/Rect;)Landroid/graphics/Canvas;");
    jmethodID unlockCanvas = env->GetMethodID(
        surfaceClass, "unlockCanvasAndPost", "(Landroid/graphics/Canvas;)V");

    jobject canvas = env->CallObjectMethod(g_virtual_surface, lockCanvas, nullptr);
    if (!canvas) return;

    jclass canvasClass = env->FindClass("android/graphics/Canvas");
    jmethodID drawBitmap = env->GetMethodID(
        canvasClass, "drawBitmap",
        "(Landroid/graphics/Bitmap;Landroid/graphics/Rect;Landroid/graphics/Rect;)V");
    /* Draw our image to fill the entire canvas */
    env->CallVoidMethod(canvas, drawBitmap, s_bitmap, nullptr, nullptr);

    env->CallVoidMethod(g_virtual_surface, unlockCanvas, canvas);

    env->DeleteLocalRef(canvas);
    env->DeleteLocalRef(canvasClass);
}

/*
 * CREATE AN ImageReader$Image from our surface
 * This is needed because ImageReader.acquireNextImageNative
 * returns an Image, not a Surface
 */
static jobject create_image_from_surface(JNIEnv *env, jobject surface) {
    jclass imageClass = env->FindClass("android/graphics/ImageReader$Image");
    if (!imageClass) return nullptr;

    /* Constructor: Image(Surface surface, int format, int width, int height, long timestamp) */
    jmethodID ctor = env->GetMethodID(
        imageClass, "<init>",
        "(Landroid/view/Surface;IIIJ)V");

    if (s_bitmap) {
        /* Get bitmap dimensions */
        jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
        jmethodID w = env->GetMethodID(bitmapClass, "getWidth", "()I");
        jmethodID h = env->GetMethodID(bitmapClass, "getHeight", "()I");
        int width = env->CallIntMethod(s_bitmap, w);
        int height = env->CallIntMethod(s_bitmap, h);
        env->DeleteLocalRef(bitmapClass);

        /* Return Image with our surface and bitmap dimensions */
        return env->NewObject(imageClass, ctor, surface, 0x00000001, width, height, 0);
    }

    /* Fallback: use default dimensions */
    return env->NewObject(imageClass, ctor, surface, 0x00000001, 720, 1280, 0);
}

/*
 * FIX: openCamera keeps ORIGINAL camera ID
 * Previously replaced with "100" which doesn't exist → app crashes
 * Now keeps original (e.g., "0") → real camera opens
 */
void hook_camera2_openCamera(JNIEnv *env, jobject thiz, jstring cameraId, jobject callback, jobject handler) {
    if (!g_config.active) {
        if (orig_openCamera) orig_openCamera(env, thiz, cameraId, callback, handler);
        return;
    }
    /* Keep the ORIGINAL camera ID - don't replace with fake "100" */
    LOGD("openCamera hook: keeping original camera ID");
    if (orig_openCamera) orig_openCamera(env, thiz, cameraId, callback, handler);
}

/*
 * FIX: createCaptureSession replaces only the FIRST output surface
 * Previously replaced ALL outputs with our surface
 * Now: first surface = our injected surface, rest = original
 */
void hook_camera2_createCaptureSession(JNIEnv *env, jobject thiz,
                                        jobjectArray outputs,
                                        jobject callback, jobject handler) {
    if (!g_config.active) {
        if (orig_createCaptureSession) orig_createCaptureSession(env, thiz, outputs, callback, handler);
        return;
    }

    jclass surfaceClass = env->FindClass("android/view/Surface");
    int outputCount = env->GetArrayLength(outputs);

    /* Create new output array */
    jobjectArray newOutputs = env->NewObjectArray(outputCount, surfaceClass, nullptr);

    for (int i = 0; i < outputCount; i++) {
        if (i == 0) {
            /* FIRST output = our virtual surface (for frame injection) */
            jobject ourSurface = create_virtual_surface(env);
            env->SetObjectArrayElement(newOutputs, i, ourSurface);
            env->DeleteLocalRef(ourSurface);
            LOGD("Replaced output[0] with virtual surface");
        } else {
            /* REST = original outputs (keep preview, recording, etc.) */
            jobject origSurface = env->GetObjectArrayElement(outputs, i);
            env->SetObjectArrayElement(newOutputs, i, origSurface);
            env->DeleteLocalRef(origSurface);
        }
    }

    if (orig_createCaptureSession) {
        orig_createCaptureSession(env, thiz, newOutputs, callback, handler);
    }

    env->DeleteLocalRef(newOutputs);
}

/*
 * FIX: ImageReader returns proper Image object
 * Previously returned a Surface (type mismatch)
 * Now returns Image(Surface, format, width, height, timestamp)
 */
jobject hook_imageReader_acquireNextImage(JNIEnv *env, jobject thiz) {
    if (!g_config.active) {
        if (orig_acquireNextImage) return orig_acquireNextImage(env, thiz);
        return nullptr;
    }

    /* Draw our frame onto the global virtual surface */
    draw_frame_on_surface(env);

    /* Create proper Image object from our surface */
    if (g_virtual_surface) {
        jobject image = create_image_from_surface(env, g_virtual_surface);
        if (image) return image;
    }

    /* Fallback: try original */
    if (orig_acquireNextImage) return orig_acquireNextImage(env, thiz);
    return nullptr;
}

/* HOOK: Camera1 - Camera.open() */
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

/* HOOK: Camera1 - Camera.setPreviewSurface() */
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

    /* Camera2 hooks */
    JNINativeMethod camera2_methods[] = {
        { "openCamera",
          "(Ljava/lang/String;Landroid/hardware/camera2/CameraDevice$Callback;Ljava/util/concurrent/Executor;)V",
          (void*)hook_camera2_openCamera },
        { "createCaptureSession",
          "(Ljava/util/List;Landroid/hardware/camera2/CameraCaptureSession$Callback;Landroid/os/Handler;)V",
          (void*)hook_camera2_createCaptureSession },
    };

    api->hookJniNativeMethods(env,
        "android/hardware/camera2/CameraManager",
        camera2_methods, 2);

    /* ImageReader hook */
    JNINativeMethod image_reader_methods[] = {
        { "acquireNextImageNative",
          "()Landroid/graphics/ImageReader$Image;",
          (void*)hook_imageReader_acquireNextImage },
    };

    api->hookJniNativeMethods(env,
        "android/graphics/ImageReader",
        image_reader_methods, 1);

    /* Camera1 hooks */
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
void register_plt_hooks(void *api_ptr, void *env) {
    auto *api = (zygisk::Api *)api_ptr;
    api->pltHookCommit();
}

/*
 * Call into metadata_spoof for unhandled keys
 * Used when other hooks need to query CameraCharacteristics
 */
jint call_spoofed_get_int(JNIEnv *env, jobject key) {
    /* First try the spoofed values */
    jint result = get_spoofed_int(env, key);
    if (result != -1) return result;  // -1 = "not handled, try original"
    /* Fallback to original (NULL if PLT didn't capture it) */
    if (orig_get_int) return orig_get_int(env, key);
    return 0;  // Final fallback
}
