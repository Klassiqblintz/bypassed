/*
 * Frame publisher header.
 * Manages decoding and publishing of injected images/videos as camera frames.
 */
#ifndef KYC_FRAME_PUBLISHER_H
#define KYC_FRAME_PUBLISHER_H

#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <media/MediaExtractor.h>
#include <media/MediaCodec.h>
#include <gui/Surface.h>
#include <ui/GraphicBuffer.h>
#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "FramePublisher", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "FramePublisher", __VA_ARGS__)

void init_frame_publisher();
void load_image(const char *path);
void load_video(const char *path);
jobject get_next_frame(JNIEnv *env, jobject input_surface);
int get_frame_rate();
void shutdown_frame_publisher();

#endif // KYC_FRAME_PUBLISHER_H
