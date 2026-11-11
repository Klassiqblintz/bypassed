/*
 * Zygisk module entry point.
 * Loaded by Zygisk when each app process starts.
 * Registers camera hooks for target apps.
 */
#include "zygisk.hpp"
#include "camera_hook.h"
#include "metadata_spoof.h"
#include "frame_publisher.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <regex>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "KYC_BYPASS", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "KYC_BYPASS", __VA_ARGS__)

class KycBypassModule : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
        LOGD("KYC Bypasser module loaded");
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        const char *process = env->GetStringUTFChars((jstring)args->nice_name, nullptr);

        if (is_target_app(process)) {
            LOGD("Target app detected: %s - registering hooks", process);
            load_config();

            // CRITICAL FIX: Copy instance config to global g_config
            g_config = config;
            g_config_loaded = true;
            LOGD("Global config set: active=%d, mode=%d, fps=%d",
                 g_config.active, g_config.mode, g_config.fps);

            // CRITICAL FIX: Load image or video into frame_publisher state
            // This was the #2 bug - s_bitmap was never populated
            if (config.image_path[0] != '\0') {
                if (config.mode == MODE_STATIC || config.mode == MODE_REALTIME) {
                    load_image(env, config.image_path);
                }
            }
            if (config.video_path[0] != '\0') {
                if (config.mode == MODE_VIDEO) {
                    load_video(config.video_path);
                }
            }

            register_camera_hooks(api, env);
            register_plt_hooks(api, env);
            register_metadata_hooks(api, env);
        } else {
            LOGD("Non-target app: %s", process);
        }

        env->ReleaseStringUTFChars((jstring)args->nice_name, process);
        api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *args) override {
        LOGD("Module being unloaded");
    }

private:
    zygisk::Api *api;
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
            size_t n = fread(buffer + total, 1, sizeof(buffer - total - 1), f);
            total += n;
        }
        buffer[total] = '\0';
        fclose(f);

        // Use regex for reliable JSON parsing instead of strstr
        std::string json(buffer);

        std::regex active_re("\"active\"\\s*:\\s*(true|false)");
        std::smatch m;
        if (std::regex_search(json, m, active_re)) {
            config.active = (m[1] == "true");
        }

        std::regex mode_re(R"DELIM("mode"\s*:\s*"([^"]+)")DELIM");
        if (std::regex_search(json, m, mode_re)) {
            std::string mode_str = m[1];
            if (mode_str == "static") config.mode = MODE_STATIC;
            else if (mode_str == "video") config.mode = MODE_VIDEO;
            else if (mode_str == "realtime") config.mode = MODE_REALTIME;
        }

        std::regex img_re(R"DELIM("image_path"\s*:\s*"([^"]+)")DELIM");
        if (std::regex_search(json, m, img_re)) {
            std::string img = m[1];
            strncpy(config.image_path, img.c_str(), sizeof(config.image_path) - 1);
        }

        std::regex vid_re(R"DELIM("video_path"\s*:\s*"([^"]+)")DELIM");
        if (std::regex_search(json, m, vid_re)) {
            std::string vid = m[1];
            strncpy(config.video_path, vid.c_str(), sizeof(config.video_path) - 1);
        }

        std::regex fps_re("\"fps\"\\s*:\\s*(\\d+)");
        if (std::regex_search(json, m, fps_re)) {
            config.fps = std::stoi(m[1]);
            if (config.fps < 1) config.fps = 30;
        }

        std::regex hide_re("\"hide_module\"\\s*:\\s*(true|false)");
        if (std::regex_search(json, m, hide_re)) {
            config.hide_module = (m[1] == "true");
        }

        config_loaded = true;
        LOGD("Config loaded: active=%d, mode=%d, fps=%d, img=%s, vid=%s",
             config.active, config.mode, config.fps,
             config.image_path, config.video_path);
        return true;
    }
};

REGISTER_ZYGISK_MODULE(KycBypassModule)
