/*
 * Zygisk module entry point.
 * Loaded by Zygisk when each app process starts.
 * Registers camera hooks for target apps.
 */
#include "zygisk.hpp"
#include "camera_hook.h"
#include "metadata_spoof.h"
#include "frame_publisher.h"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "KYC_BYPASS", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "KYC_BYPASS", __VA_ARGS__)

class KycBypassModule : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
        LOGD("KYC Bypasser module loaded");
    }

    void preAppSpecialize(AppSpecializeArgs *args) override {
        const char *process = env->GetStringUTFChars(args->nice_name, nullptr);

        if (is_target_app(process)) {
            LOGD("Target app detected: %s - registering hooks", process);
            load_config();
            register_camera_hooks(api, env);
            register_plt_hooks(api, env);
            register_metadata_hooks(api, env);
        } else {
            LOGD("Non-target app: %s", process);
        }

        env->ReleaseStringUTFChars(args->nice_name, process);
        api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
    }

    void postAppSpecialize(const AppSpecializeArgs *args) override {
        LOGD("Module being unloaded");
    }

private:
    Api *api;
    JNIEnv *env;
    bool config_loaded = false;
    Config config;

    bool is_target_app(const char *package_name) {
        FILE *f = fopen("/data/adb/modules/kyc_bypasser/targets.txt", "r");
        if (!f) return false;

        char line[256];
        bool found = false;
        while (fgets(line, sizeof(line), f)) {
            line[strcspn(line, "\n\r")] = 0;
            if (line[0] == '#' || line[0] == '\0') continue;
            if (strcmp(line, package_name) == 0) {
                found = true;
                break;
            }
        }
        fclose(f);
        return found;
    }

    bool load_config() {
        if (config_loaded) return true;

        FILE *f = fopen("/data/adb/modules/kyc_bypasser/config.json", "r");
        if (!f) {
            LOGE("Cannot open config.json");
            return false;
        }

        char buffer[4096];
        size_t total = 0;
        while (!feof(f)) {
            size_t n = fread(buffer + total, 1, sizeof(buffer) - total - 1, f);
            total += n;
        }
        buffer[total] = '\0';
        fclose(f);

        if (strstr(buffer, "\"active\": true")) {
            config.active = true;
        } else {
            config.active = false;
        }

        char *mode_pos = strstr(buffer, "\"mode\":");
        if (mode_pos) {
            if (strstr(mode_pos, "\"static\"")) {
                config.mode = MODE_STATIC;
            } else if (strstr(mode_pos, "\"video\"")) {
                config.mode = MODE_VIDEO;
            } else if (strstr(mode_pos, "\"realtime\"")) {
                config.mode = MODE_REALTIME;
            }
        }

        char *img_pos = strstr(buffer, "\"image_path\":");
        if (img_pos) {
            char *start = strchr(img_pos + 14, '"');
            if (start) {
                char *end = strchr(start + 1, '"');
                if (end) {
                    int len = end - start - 1;
                    strncpy(config.image_path, start + 1, len);
                    config.image_path[len] = '\0';
                }
            }
        }

        char *vid_pos = strstr(buffer, "\"video_path\":");
        if (vid_pos) {
            char *start = strchr(vid_pos + 13, '"');
            if (start) {
                char *end = strchr(start + 1, '"');
                if (end) {
                    int len = end - start - 1;
                    strncpy(config.video_path, start + 1, len);
                    config.video_path[len] = '\0';
                }
            }
        }

        char *fps_pos = strstr(buffer, "\"fps\":");
        if (fps_pos) {
            config.fps = atoi(fps_pos + 6);
            if (config.fps < 1) config.fps = 30;
        }

        config_loaded = true;
        LOGD("Config loaded: active=%d, mode=%d, fps=%d",
             config.active, config.mode, config.fps);
        return true;
    }
};

REGISTER_ZYGISK_MODULE(KycBypassModule)
