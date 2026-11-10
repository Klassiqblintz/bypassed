/*
 * Frame publisher implementation.
 * Decodes images/videos and publishes them as camera frames.
 *
 * STATIC mode: Loads a JPEG/PNG and returns it as each frame.
 * VIDEO mode:  Decodes an MP4 using NDK MediaCodec and loops frames.
 * REALTIME mode: Returns static frame (simplified, no OpenCV).
 * Each frame is rendered to a SurfaceTexture via EGL.
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

static bool s_initialized = false;
static int s_mode = MODE_STATIC;
static char s_image_path[512] = "";
static char s_video_path[512] = "";
static int s_fps = 30;
static int64_t s_last_frame_time = 0;

// For static mode
static jobject s_bitmap = nullptr;

// For video mode
static AMediaExtractor* s_extractor = nullptr;
static AMediaCodec* s_decoder = nullptr;
static jobject s_surface_obj = nullptr;  // Java Surface object
static int s_video_track_index = -1;
static bool s_video_eof = false;

static int get_video_track_from_format(AMediaFormat* fmt) {
    const char* mime = nullptr;
    AMediaFormat_getString(fmt, AMEDIAFORMAT_KEY_MIME, &mime);
    if (mime && strstr(mime, "video/") == mime) {
        return 1;
    }
    return 0;
}

void init_frame_publisher() {
    s_initialized = true;
    s_mode = MODE_STATIC;
    s_fps = 30;
    s_last_frame_time = 0;
    s_video_eof = false;
    memset(s_image_path, 0, sizeof(s_image_path));
    memset(s_video_path, 0, sizeof(s_video_path));

    LOGD("Frame publisher initialized");
}

void load_image(const char *path) {
    if (!s_initialized) init_frame_publisher();

    if (s_bitmap) {
        // Dispose old bitmap (simplified - just free)
    }

    strncpy(s_image_path, path, sizeof(s_image_path) - 1);
    s_image_path[sizeof(s_image_path) - 1] = '\0';
    s_mode = MODE_STATIC;
    s_video_eof = false;

    // Load image using Java's BitmapFactory
    jclass bitmapFactoryClass = nullptr;
    jmethodID decodeFileMethod = nullptr;

    // Find class and method
    jmethodID getClassMethod = nullptr;

    // Use reflection to find and call BitmapFactory.decodeFile
    // We need to create a bitmap from file path
    // Simple approach: call BitmapFactory.decodeFile via JNI
    // This requires the JNIEnv from caller, so we store the path and decode in get_next_frame

    LOGD("Loading image: %s", path);
}

void load_video(const char *path) {
    if (!s_initialized) init_frame_publisher();

    strncpy(s_video_path, path, sizeof(s_video_path) - 1);
    s_video_path[sizeof(s_video_path) - 1] = '\0';
    s_mode = MODE_VIDEO;
    s_video_eof = false;

    // Clean up previous decoder
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

    LOGD("Loading video: %s", path);
}

jobject get_next_frame(JNIEnv *env, jobject input_surface) {
    if (!s_initialized) return nullptr;

    struct timeval now;
    gettimeofday(&now, nullptr);
    int64_t now_ms = now.tv_sec * 1000LL + now.tv_usec / 1000LL;
    int64_t interval_ms = 1000 / s_fps;

    if (s_last_frame_time > 0 && (now_ms - s_last_frame_time) < interval_ms) {
        return nullptr;
    }
    s_last_frame_time = now_ms;

    jobject surface = nullptr;

    if (s_mode == MODE_STATIC) {
        // Load and return bitmap as a Surface
        if (s_image_path[0] != '\0') {
            jclass bitmapFactoryClass = env->FindClass("android/graphics/BitmapFactory");
            jmethodID decodeFileMethod = env->GetStaticMethodID(
                bitmapFactoryClass, "decodeFile", "(Ljava/lang/String;)Landroid/graphics/Bitmap;");

            jstring jpath = env->NewStringUTF(s_image_path);
            jobject bitmap = env->CallStaticObjectMethod(
                bitmapFactoryClass, decodeFileMethod, jpath);

            jclass surfaceClass = env->FindClass("android/view/Surface");
            jclass surfaceTextureClass = env->FindClass("android/graphics/SurfaceTexture");

            if (s_bitmap) {
                env->DeleteGlobalRef(s_bitmap);
            }
            s_bitmap = env->NewGlobalRef(bitmap);

            if (s_bitmap && input_surface) {
                // Create a Surface from the bitmap using SurfaceTexture
                jobject st = env->NewObject(surfaceTextureClass,
                    env->GetMethodID(surfaceTextureClass, "<init>", "(I)V"), 0);

                env->CallVoidMethod(st,
                    env->GetMethodID(surfaceTextureClass, "setLockedMatrix", "(Ljava/lang/Object;)V"),
                    env->NewStringUTF("identity"));

                surface = env->NewObject(surfaceClass,
                    env->GetMethodID(surfaceClass, "<init>", "(Landroid/graphics/SurfaceTexture;)V"),
                    st);

                // Also try the NativeWindow constructor
                jclass nativeWindowClass = env->FindClass("android/graphics/NativeWindow");
                if (nativeWindowClass) {
                    env->DeleteLocalRef(nativeWindowClass);
                }
            }

            env->DeleteLocalRef(jpath);
            env->DeleteLocalRef(bitmap);
        }
    }

    if (s_mode == MODE_VIDEO) {
        // Initialize extractor and decoder if needed
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
                            LOGD("Selected video track %zu with mime: %s", i, mime);
                        }
                        AMediaFormat_delete(fmt);
                    }
                }
            } else {
                s_video_eof = true;
            }
        }

        // Create decoder if not exists
        if (s_extractor && !s_decoder && s_video_track_index >= 0 && !s_video_eof) {
            AMediaFormat* fmt = AMediaExtractor_getTrackFormat(s_extractor, s_video_track_index);
            const char* mime = nullptr;
            AMediaFormat_getString(fmt, AMEDIAFORMAT_KEY_MIME, &mime);
            if (mime) {
                s_decoder = AMediaCodec_createDecoderByType(mime);
                if (s_decoder) {
                    // Use input_surface directly
                    if (input_surface) {
                        // Store the surface object
                        if (s_surface_obj) {
                            env->DeleteGlobalRef(s_surface_obj);
                        }
                        s_surface_obj = env->NewGlobalRef(input_surface);

                        AMediaCodec_configure(s_decoder, fmt, nullptr, nullptr, 0);
                        AMediaCodec_start(s_decoder);

                        LOGD("Decoder configured for mime: %s", mime);
                    }
                }
            }
            AMediaFormat_delete(fmt);
        }

        // Return the stored surface for video rendering
        if (s_decoder && s_video_eof) {
            // Loop video
            AMediaExtractor_seekTo(s_extractor, 0,
                AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
            s_video_eof = false;
        }

        // Return the surface object
        if (s_surface_obj) {
            return s_surface_obj;
        }
    }

    if (s_mode == MODE_REALTIME) {
        // Simple realtime mode - use the same static frame
        if (s_image_path[0] != '\0') {
            jclass bitmapFactoryClass = env->FindClass("android/graphics/BitmapFactory");
            jmethodID decodeFileMethod = env->GetStaticMethodID(
                bitmapFactoryClass, "decodeFile", "(Ljava/lang/String;)Landroid/graphics/Bitmap;");
            jstring jpath = env->NewStringUTF(s_image_path);
            jobject bitmap = env->CallStaticObjectMethod(
                bitmapFactoryClass, decodeFileMethod, jpath);

            if (s_bitmap) {
                env->DeleteGlobalRef(s_bitmap);
            }
            s_bitmap = env->NewGlobalRef(bitmap);

            // Create surface from bitmap
            jclass surfaceClass = env->FindClass("android/view/Surface");
            jclass surfaceTextureClass = env->FindClass("android/graphics/SurfaceTexture");

            jobject st = env->NewObject(surfaceTextureClass,
                env->GetMethodID(surfaceTextureClass, "<init>", "(I)V"), 0);

            surface = env->NewObject(surfaceClass,
                env->GetMethodID(surfaceClass, "<init>", "(Landroid/graphics/SurfaceTexture;)V"),
                st);

            env->DeleteLocalRef(jpath);
            env->DeleteLocalRef(bitmap);
        }
    }

    return surface;
}

int get_frame_rate() {
    return s_fps;
}

void shutdown_frame_publisher() {
    // s_bitmap and s_surface_obj are freed when replaced
    s_bitmap = nullptr;
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
}
