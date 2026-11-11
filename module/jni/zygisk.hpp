/*
 * Minimal Zygisk API header for module development.
 * Based on Zygisk API v5 (Magisk 26+).
 */
#ifndef ZYGISK_HPP
#define ZYGISK_HPP

#include <jni.h>
#include <string>
#include <vector>
#include <sys/types.h>

namespace zygisk {

enum class Option {
    DLCLOSE_MODULE_LIBRARY = 0,
    WAKEUP_APP_PROCESS = 1,
};

class Api {
public:
    void hookJniNativeMethods(JNIEnv *env, const char *class_name,
                               JNINativeMethod *methods, int count) {
        // Hook native methods for the given class
    }
    void pltHookCommit() {
        // Commit PLT hooks
    }
    void setOption(Option opt) {
        // Set module option
    }
};

struct AppSpecializeArgs {
    const char *nice_name = nullptr;
    const char *package_name = nullptr;
    int uid = -1;
    int gid = -1;
    int *gids = nullptr;
    int ngids = 0;
    int sdk_version = 0;
    // ... other fields omitted for minimal header
};

class ModuleBase {
public:
    virtual void onLoad(Api *api, JNIEnv *env) {}
    virtual void preAppSpecialize(AppSpecializeArgs *args) {}
    virtual void postAppSpecialize(const AppSpecializeArgs *args) {}
};

} // namespace zygisk

#define REGISTER_ZYGISK_MODULE(ModuleClass) \
    static zygisk::ModuleBase *_register_ ## ModuleClass ## _instance = \
        new ModuleClass();

#endif // ZYGISK_HPP
