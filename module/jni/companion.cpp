/*
 * Zygisk companion process handler.
 * Runs in the root companion daemon process.
 * Handles IPC from the module running in each app's process.
 */
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <android/log.h>
#include <pthread.h>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "Companion", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "Companion", __VA_ARGS__)

static pthread_mutex_t config_mutex = PTHREAD_MUTEX_INITIALIZER;
static bool g_active = false;
static char g_image_path[512] = "";
static char g_video_path[512] = "";

/**
 * Root companion handler function.
 * Each connection gets its own thread.
 */
static void *companion_thread(void *arg) {
    int client_fd = *(int *)arg;
    free(arg);

    char buffer[1024];
    ssize_t n = read(client_fd, buffer, sizeof(buffer) - 1);
    if (n <= 0) { close(client_fd); return nullptr; }
    buffer[n] = '\0';

    pthread_mutex_lock(&config_mutex);

    if (strncmp(buffer, "ACTIVE", 6) == 0) {
        g_active = true;
        LOGD("Module activated");
    } else if (strncmp(buffer, "INACTIVE", 8) == 0) {
        g_active = false;
        LOGD("Module deactivated");
    } else if (strncmp(buffer, "IMAGE:", 6) == 0) {
        strncpy(g_image_path, buffer + 6, sizeof(g_image_path) - 1);
        LOGD("Image path set: %s", g_image_path);
    } else if (strncmp(buffer, "VIDEO:", 6) == 0) {
        strncpy(g_video_path, buffer + 6, sizeof(g_video_path) - 1);
        LOGD("Video path set: %s", g_video_path);
    } else if (strncmp(buffer, "STATUS", 6) == 0) {
        char response[512];
        snprintf(response, sizeof(response),
            "STATUS:active=%d,image=%s,video=%s",
            g_active, g_image_path, g_video_path);
        write(client_fd, response, strlen(response));
        LOGD("Status sent");
    }

    pthread_mutex_unlock(&config_mutex);
    close(client_fd);
    return nullptr;
}

void companion_handler(int client_fd) {
    pthread_t t;
    int *arg = (int *)malloc(sizeof(int));
    *arg = client_fd;
    pthread_create(&t, nullptr, companion_thread, arg);
    pthread_detach(t);
}

REGISTER_ZYGISK_COMPANION(companion_handler)
