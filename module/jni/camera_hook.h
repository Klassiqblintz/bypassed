/*
 * Camera hooking header.
 * Declares all hook functions for Camera2, Camera1, and PLT hooks.
 */
#ifndef KYC_CAMERA_HOOK_H
#define KYC_CAMERA_HOOK_H

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <atomic>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "CameraHook", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "CameraHook", __VA_ARGS__)

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

extern Config g_config;
extern bool g_config_loaded;
extern const char *VIRTUAL_CAMERA_ID;

/* Load config from config.json and copy to global g_config */
void load_config_to_global(void);

/* Thread-safe texture ID generator */
extern std::atomic<unsigned int> g_texture_counter;

/* Original function pointer types (filled by hooks) */
typedef void (*orig_openCamera_t)(JNIEnv*, jobject, jstring, jobject, jobject);
typedef jint (*orig_get_int_t)(JNIEnv*, jobject);
typedef void (*orig_createCaptureSession_t)(JNIEnv*, jobject, jobjectArray, jobject, jobject);
typedef jobject (*orig_acquireNextImage_t)(JNIEnv*, jobject);
typedef jobject (*orig_camera1_open_t)(JNIEnv*, jobject, jint);
typedef void (*orig_camera1_setPreviewSurface_t)(JNIEnv*, jobject, jobject);

extern orig_openCamera_t orig_openCamera;
extern orig_get_int_t orig_get_int;
extern orig_createCaptureSession_t orig_createCaptureSession;
extern orig_acquireNextImage_t orig_acquireNextImage;
extern orig_camera1_open_t orig_camera1_open;
extern orig_camera1_setPreviewSurface_t orig_camera1_setPreviewSurface;

void register_camera_hooks(void *api, JNIEnv *env);
void register_plt_hooks(void *api, void *env);
jobject create_virtual_surface(JNIEnv *env);
jint hook_camera2_get_int(JNIEnv *env, jobject thiz, jobject key);

/* Call into metadata_spoof for unhandled keys */
jint call_spoofed_get_int(JNIEnv *env, jobject key);

#endif // KYC_CAMERA_HOOK_H
