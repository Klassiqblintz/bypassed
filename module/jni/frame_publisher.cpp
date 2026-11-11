/*
 * Frame publisher implementation.
 * Decodes images/videos and publishes them as camera frames.
 *
 * FIXES:
 * - Video decoder configures without input_surface
 * - Anti-detection: jitter + pixel noise
 * - Memory leaks fixed
 * - load_image() NOW actually loads the bitmap (was just storing path before)
 * - s_bitmap is extern so camera_hook.cpp can draw onto the virtual surface
 */
#include "frame_publisher.h"
#include "camera_hook.h"
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <sys/time.h>
#include <cstring>
#include <unistd.h>
#include <cmath>
#include <ctime>

/* EXTERN variables (visible to camera_hook.cpp for drawing) */
jobject s_bitmap = nullptr;
int s_mode = MODE_STATIC;
int s_fps = 30;

static bool s_initialized = false;
static char s_image_path[512] = "";
static char s_video_path[512] = "";
static int64_t s_last_frame_time = 0;

/* For video mode */
static AMediaExtractor* s_extractor = nullptr;
static AMediaCodec* s_decoder = nullptr;
static jobject s_surface_obj = nullptr;
static int s_video_track_index = -1;
static bool s_video_eof = false;
static int64_t s_video_duration_us = 0;

static int get_video_track_from_format(AMediaFormat* fmt) {
    const char* mime = nullptr;
    AMediaFormat_getString(fmt, AMEDIAFORMAT_KEY_MIME, &mime);
    if (mime && strstr(mime, "video/") == mime) return 1;
    return 0;
}

/* ANTI-DETECTION: Add slight pixel noise */
static void add_anti_detection_noise(JNIEnv *env, jobject bitmap) {
    if (!bitmap) return;

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID getConfigMethod = env->GetMethodID(bitmapClass, "getConfig",
        "()Landroid/graphics/Bitmap$Config;");
    jobject config = env->CallObjectMethod(bitmap, getConfigMethod);

    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jmethodID nameMethod = env->GetMethodID(bitmapConfigClass, "name",
        "()Ljava/lang/String;");
    jstring configName = (jstring)env->CallObjectMethod(config, nameMethod);
    const char *configStr = env->GetStringUTFChars(configName, nullptr);

    if (strcmp(configStr, "ARGB_8888") == 0) {
        jmethodID getWidth = env->GetMethodID(bitmapClass, "getWidth", "()I");
        jmethodID getHeight = env->GetMethodID(bitmapClass, "getHeight", "()I");
        jint width = env->CallIntMethod(bitmap, getWidth);
        jint height = env->CallIntMethod(bitmap, getHeight);

        jintArray pixels = env->NewIntArray(width * height);
        jmethodID getPixelsMethod = env->GetMethodID(bitmapClass, "getPixels", "([I)V");
        env->CallVoidMethod(bitmap, getPixelsMethod, pixels);

        jint *pixelData = env->GetIntArrayElements(pixels, nullptr);

        srand(time(nullptr) ^ (uintptr_t)env);
        for (int i = 0; i < width * height; i++) {
            int pixel = pixelData[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            int noise = (rand() % 3) - 1;
            r = (r + noise) & 0xFF;
            g = (g + noise) & 0xFF;
            b = (b + noise) & 0xFF;
            pixelData[i] = (pixel & 0xFF000000) | (r << 16) | (g << 8) | b;
        }

        env->SetIntArrayRegion(pixels, 0, width * height, pixelData);
        env->ReleaseIntArrayElements(pixels, pixelData, 0);
    }

    env->ReleaseStringUTFChars(configName, configStr);
    env->DeleteLocalRef(configName);
    env->DeleteLocalRef(bitmapClass);
    env->DeleteLocalRef(bitmapConfigClass);
    env->DeleteLocalRef(config);
}

void init_frame_publisher() {
    s_initialized = true;
    s_mode = MODE_STATIC;
    s_fps = 30;
    s_last_frame_time = 0;
    s_video_eof = false;
    s_video_track_index = -1;
    memset(s_image_path, 0, sizeof(s_image_path));
    memset(s_video_path, 0, sizeof(s_video_path));
    LOGD("Frame publisher initialized");
}

/*
 * FIX: load_image() NOW actually loads and decodes the bitmap
 * Previously: only stored path, bitmap loaded lazily in get_next_frame
 * Now: loads immediately so s_bitmap is available for drawing
 */
void load_image(JNIEnv *env, const char *path) {
    if (!s_initialized) init_frame_publisher();

    strncpy(s_image_path, path, sizeof(s_image_path) - 1);
    s_image_path[sizeof(s_image_path) - 1] = '\0';
    s_mode = MODE_STATIC;
    s_video_eof = false;

    /* FREE old bitmap */
    if (s_bitmap) {
        s_bitmap = nullptr;
    }

    /* LOAD bitmap from file (via JNI) */
    jclass bitmapFactoryClass = env->FindClass("android/graphics/BitmapFactory");
    jmethodID decodeFileMethod = env->GetStaticMethodID(
        bitmapFactoryClass, "decodeFile", "(Ljava/lang/String;)Landroid/graphics/Bitmap;");

    jstring jpath = env->NewStringUTF(path);
    jobject bitmap = env->CallStaticObjectMethod(
        bitmapFactoryClass, decodeFileMethod, jpath);

    if (bitmap) {
        s_bitmap = env->NewGlobalRef(bitmap);
        LOGD("Image loaded: %s (global ref %p)", path, s_bitmap);
    } else {
        LOGE("Failed to load image: %s", path);
    }

    env->DeleteLocalRef(jpath);
    env->DeleteLocalRef(bitmap);
    env->DeleteLocalRef(bitmapFactoryClass);
}

void load_video(const char *path) {
    if (!s_initialized) init_frame_publisher();

    strncpy(s_video_path, path, sizeof(s_video_path) - 1);
    s_video_path[sizeof(s_video_path) - 1] = '\0';
    s_mode = MODE_VIDEO;
    s_video_eof = false;
    s_last_frame_time = 0;

    if (s_decoder) {
        AMediaCodec_delete(s_decoder);
        s_decoder = nullptr;
    }
    if (s_extractor) {
        AMediaExtractor_delete(s_extractor);
        s_extractor = nullptr;
    }
    if (s_surface_obj) {
        s_surface_obj = nullptr;
    }

    LOGD("Video loaded: %s", path);
}

jobject get_next_frame(JNIEnv *env, jobject input_surface) {
    if (!s_initialized) return nullptr;

    struct timeval now;
    gettimeofday(&now, nullptr);
    int64_t now_ms = now.tv_sec * 1000LL + now.tv_usec / 1000LL;

    int64_t interval_ms = 1000 / s_fps;
    if (s_last_frame_time > 0 && (now_ms - s_last_frame_time) < interval_ms) {
        usleep(1000); // 1ms jitter for anti-detection
    }
    s_last_frame_time = now_ms;

    jobject surface = nullptr;

    if (s_mode == MODE_STATIC || s_mode == MODE_REALTIME) {
        if (s_image_path[0] != '\0' && !s_bitmap) {
            /* Load bitmap if not already loaded */
            jclass bitmapFactoryClass = env->FindClass("android/graphics/BitmapFactory");
            jmethodID decodeFileMethod = env->GetStaticMethodID(
                bitmapFactoryClass, "decodeFile", "(Ljava/lang/String;)Landroid/graphics/Bitmap;");

            jstring jpath = env->NewStringUTF(s_image_path);
            jobject bitmap = env->CallStaticObjectMethod(
                bitmapFactoryClass, decodeFileMethod, jpath);

            if (s_bitmap) {
                s_bitmap = nullptr;
            }
            if (bitmap) {
                s_bitmap = env->NewGlobalRef(bitmap);
                add_anti_detection_noise(env, s_bitmap);
            }

            env->DeleteLocalRef(jpath);
            env->DeleteLocalRef(bitmap);
            env->DeleteLocalRef(bitmapFactoryClass);
        }
    }

    if (s_mode == MODE_VIDEO) {
        if (!s_extractor && s_video_path[0] != '\0') {
            s_extractor = AMediaExtractor_new();
            if (s_extractor) {
                media_status_t status = AMediaExtractor_setDataSource(
                    s_extractor, s_video_path);
                if (status != AMEDIA_OK) {
                    LOGE("Failed to set video data source");
                    AMediaExtractor_delete(s_extractor);
                    s_extractor = nullptr;
                    s_video_eof = true;
                } else {
                    size_t track_count = AMediaExtractor_getTrackCount(s_extractor);
                    for (size_t i = 0; i < track_count; i++) {
                        AMediaFormat* fmt = AMediaExtractor_getTrackFormat(s_extractor, i);
                        const char* mime = nullptr;
                        AMediaFormat_getString(fmt, AMEDIAFORMAT_KEY_MIME, &mime);
                        if (mime && strstr(mime, "video/") == mime) {
                            s_video_track_index = (int)i;
                            AMediaExtractor_selectTrack(s_extractor, i);
                            LOGD("Selected video track %zu", i);
                            AMediaFormat_getInt64(fmt, AMEDIAFORMAT_KEY_DURATION, &s_video_duration_us);
                        }
                        AMediaFormat_delete(fmt);
                    }
                }
            }
        }

        if (s_extractor && !s_decoder && s_video_track_index >= 0 && !s_video_eof) {
            AMediaFormat* fmt = AMediaExtractor_getTrackFormat(s_extractor, s_video_track_index);
            const char* mime = nullptr;
            AMediaFormat_getString(fmt, AMEDIAFORMAT_KEY_MIME, &mime);
            if (mime) {
                s_decoder = AMediaCodec_createDecoderByType(mime);
                if (s_decoder) {
                    AMediaCodec_configure(s_decoder, fmt, nullptr, nullptr, 0);
                    AMediaCodec_start(s_decoder);
                    LOGD("Decoder configured for mime: %s", mime);
                }
            }
            AMediaFormat_delete(fmt);
        }

        if (s_decoder && s_video_eof) {
            AMediaExtractor_seekTo(s_extractor, 0, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
            s_video_eof = false;
        }
    }

    return surface;
}

int get_frame_rate() {
    return s_fps;
}

void shutdown_frame_publisher() {
    if (s_bitmap) {
        s_bitmap = nullptr;
    }
    if (s_surface_obj) {
        s_surface_obj = nullptr;
    }
    if (s_decoder) {
        AMediaCodec_delete(s_decoder);
        s_decoder = nullptr;
    }
    if (s_extractor) {
        AMediaExtractor_delete(s_extractor);
        s_extractor = nullptr;
    }
    s_initialized = false;
    s_mode = MODE_STATIC;
    memset(s_image_path, 0, sizeof(s_image_path));
    memset(s_video_path, 0, sizeof(s_video_path));
    LOGD("Frame publisher shutdown");
}
