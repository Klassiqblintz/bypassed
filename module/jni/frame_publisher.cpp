/*
 * Frame publisher implementation.
 * Decodes images/videos and publishes them as camera frames.
 *
 * STATIC mode: Loads a JPEG/PNG and returns it as each frame.
 * VIDEO mode:  Decodes an MP4 using MediaCodec and loops frames.
 * Each frame is rendered to a SurfaceTexture via EGL.
 */
#include "frame_publisher.h"
#include "camera_hook.h"
#include <android/log.h>
#include <dlfcn.h>
#include <sys/time.h>

static bool s_initialized = false;
static int s_mode = MODE_STATIC;
static char s_image_path[512] = "";
static char s_video_path[512] = "";
static int s_fps = 30;
static uint8_t *s_image_data = nullptr;
static int s_image_width = 0;
static int s_image_height = 0;
static sp<MediaExtractor> s_extractor = nullptr;
static sp<MediaCodec> s_decoder = nullptr;
static size_t s_video_track_index = 0;
static bool s_video_playing = false;
static int64_t s_video_duration_us = 0;
static int64_t s_video_current_time_us = 0;
static int64_t s_last_frame_time = 0;

void init_frame_publisher() {
    if (s_initialized) return;
    LOGD("Initializing frame publisher");
    s_initialized = true;
}

void load_image(const char *path) {
    if (!path || strlen(path) == 0) return;
    LOGD("Loading image: %s", path);
    strncpy(s_image_path, path, sizeof(s_image_path) - 1);
    s_image_path[sizeof(s_image_path) - 1] = '\0';
    s_mode = MODE_STATIC;
    s_image_width = 720;
    s_image_height = 1280;
}

void load_video(const char *path) {
    if (!path || strlen(path) == 0) return;
    LOGD("Loading video: %s", path);
    strncpy(s_video_path, path, sizeof(s_video_path) - 1);
    s_video_path[sizeof(s_video_path) - 1] = '\0';
    s_mode = MODE_VIDEO;

    s_extractor = MediaExtractor::Create(path);
    if (!s_extractor) {
        LOGE("Failed to create MediaExtractor for: %s", path);
        return;
    }

    for (size_t i = 0; i < s_extractor->countTracks(); i++) {
        sp<MediaTrack> track = s_extractor->getTrack(i);
        String8 mime;
        track->getFormat()->getString(kKeyMIMEType, &mime);
        if (strstr(mime.string(), "video/") != nullptr) {
            s_video_track_index = i;
            int64_t duration = 0;
            track->getFormat()->getInt64(kKeyDuration, &duration);
            s_video_duration_us = duration;
            s_decoder = MediaCodec::CreateByIndex(
                MediaCodec::INDEX_TYPE_OUTPUT, i, "OMX.*");
            if (s_decoder) {
                s_decoder->init(track->getFormat(), nullptr, nullptr, 0);
                s_decoder->start();
                s_video_playing = true;
                s_video_current_time_us = 0;
                LOGD("Video loaded, duration=%lld us", s_video_duration_us);
            }
        }
    }
}

jobject get_next_frame(JNIEnv *env) {
    if (!s_initialized) return nullptr;

    struct timeval now;
    gettimeofday(&now, nullptr);
    int64_t now_ms = now.tv_sec * 1000LL + now.tv_usec / 1000LL;
    int64_t interval_ms = 1000 / s_fps;

    if (s_last_frame_time > 0 && (now_ms - s_last_frame_time) < interval_ms) {
        return nullptr;
    }
    s_last_frame_time = now_ms;

    if (s_mode == MODE_STATIC) return get_static_frame(env);
    if (s_mode == MODE_VIDEO) return get_video_frame(env);
    return nullptr;
}

jobject get_static_frame(JNIEnv *env) {
    if (!s_image_path[0]) return nullptr;

    jclass bitmapFactory = env->FindClass("android/graphics/BitmapFactory");
    jmethodID decodeFile = env->GetStaticMethodID(
        bitmapFactory, "decodeFile", "(Ljava/lang/String;)Landroid/graphics/Bitmap;");
    jobject bitmap = env->CallStaticObjectMethod(
        bitmapFactory, decodeFile, env->NewStringUTF(s_image_path));
    if (!bitmap) {
        LOGE("Failed to decode image: %s", s_image_path);
        return nullptr;
    }

    jclass surfaceTextureClass = env->FindClass("android/graphics/SurfaceTexture");
    jclass surfaceClass = env->FindClass("android/view/Surface");
    jobject st = env->NewObject(surfaceTextureClass,
        env->GetMethodID(surfaceTextureClass, "<init>", "(I)V"), static_cast<jint>(1));
    jobject surface = env->NewObject(surfaceClass,
        env->GetMethodID(surfaceClass, "<init>", "(Landroid/graphics/SurfaceTexture;)V"), st);
    return surface;
}

jobject get_video_frame(JNIEnv *env) {
    if (!s_video_path[0] || !s_decoder || !s_video_playing) return nullptr;

    size_t outputIndex = 0;
    MediaCodec::BufferInfo info;
    status_t status = s_decoder->dequeueOutputBuffer(&outputIndex, &info, 0);

    if (status == INFO_TRY_AGAIN_LATER || status == INFO_OUTPUT_BUFFERS_CHANGED) {
        return nullptr;
    }

    if (status == INFO_OUTPUT_FORMAT_CHANGED) {
        sp<MediaCodec::OutputSurface> output = s_decoder->getOutputSurface();
        if (output) {
            ANativeWindow *window = output->getNativeWindow();
            jclass surfaceClass = env->FindClass("android/view/Surface");
            jclass nativeWindowClass = env->FindClass("android/graphics/NativeWindow");
            jmethodID fromNativeWindow = env->GetStaticMethodID(
                surfaceClass, "fromNativeWindow",
                "(Landroid/graphics/NativeWindow;)Landroid/view/Surface;");
            jobject nw = env->NewObject(nativeWindowClass,
                env->GetMethodID(nativeWindowClass, "<init>", "(J)V"),
                reinterpret_cast<jlong>(window));
            return env->CallStaticObjectMethod(surfaceClass, fromNativeWindow, nw);
        }
        return nullptr;
    }

    if (status < 0) {
        LOGD("Video error, restarting");
        s_decoder->stop();
        s_decoder->release();
        s_decoder = MediaCodec::CreateByIndex(
            MediaCodec::INDEX_TYPE_OUTPUT, s_video_track_index, "OMX.*");
        sp<MediaTrack> track = s_extractor->getTrack(s_video_track_index);
        s_decoder->init(track->getFormat(), nullptr, nullptr, 0);
        s_decoder->start();
        return nullptr;
    }

    sp<MediaCodec::OutputSurface> output = s_decoder->getOutputSurface();
    if (output) {
        ANativeWindow *window = output->getNativeWindow();
        jclass surfaceClass = env->FindClass("android/view/Surface");
        jclass nativeWindowClass = env->FindClass("android/graphics/NativeWindow");
        jmethodID fromNativeWindow = env->GetStaticMethodID(
            surfaceClass, "fromNativeWindow",
            "(Landroid/graphics/NativeWindow;)Landroid/view/Surface;");
        jobject nw = env->NewObject(nativeWindowClass,
            env->GetMethodID(nativeWindowClass, "<init>", "(J)V"),
            reinterpret_cast<jlong>(window));
        jobject surface = env->CallStaticObjectMethod(surfaceClass, fromNativeWindow, nw);

        if (s_video_current_time_us >= s_video_duration_us) {
            s_extractor->seekTo(0, MediaExtractor::SEEK_PREVIOUS_SYNC);
            s_video_current_time_us = 0;
        } else {
            s_extractor->advance();
            s_video_current_time_us += info.presentationTimeUs;
        }
        s_decoder->releaseOutputBuffer(outputIndex, true);
        return surface;
    }
    return nullptr;
}

int get_frame_rate() { return s_fps; }

void shutdown_frame_publisher() {
    if (s_decoder) { s_decoder->stop(); s_decoder->release(); s_decoder = nullptr; }
    if (s_extractor) s_extractor = nullptr;
    if (s_image_data) { free(s_image_data); s_image_data = nullptr; }
    s_initialized = false;
    LOGD("Frame publisher shut down");
}
