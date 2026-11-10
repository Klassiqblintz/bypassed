/*
 * Metadata spoofing header.
 * Fakes CameraCharacteristics to make the virtual camera look like real hardware.
 */
#ifndef KYC_METADATA_SPOOF_H
#define KYC_METADATA_SPOOF_H

#include <jni.h>

void register_metadata_hooks(void *api_ptr, JNIEnv *env);
int get_spoofed_int(jobject key);
jfloatArray get_spoofed_float(jobject key);

#endif // KYC_METADATA_SPOOF_H
