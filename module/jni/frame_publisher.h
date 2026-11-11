/*
 * Frame publisher header.
 * Manages decoding and publishing of injected images/videos as camera frames.
 * Uses NDK MediaCodec API.
 */
#ifndef KYC_FRAME_PUBLISHER_H
#define KYC_FRAME_PUBLISHER_H

#include <jni.h>
#include <android/log.h>
#include <atomic>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "FramePublisher", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "FramePublisher", __VA_ARGS__)

/* Frame publisher state (exposed for drawing function) */
extern jobject s_bitmap;
extern int s_mode;
extern int s_fps;

void init_frame_publisher();
void load_image(JNIEnv *env, const char *path);
void load_video(const char *path);
jobject get_next_frame(JNIEnv *env, jobject input_surface);
int get_frame_rate();
void shutdown_frame_publisher();

#endif // KYC_FRAME_PUBLISHER_H
