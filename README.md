# KYC Bypasser — Android Camera Hooking App

A complete Android app + Zygisk module for bypassing KYC verification by hooking the camera API and injecting virtual images/videos.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  KYC BYPAsser APP (APK)                                     │
│  - Target app selector                                      │
│  - Image / Video picker                                     │
│  - Active/inactive toggle                                   │
│  - Liveness mode selector                                   │
│  - Status dashboard                                         │
└──────────────────┬──────────────────────────────────────────┘
                   │ (Unix socket IPC)
┌──────────────────▼──────────────────────────────────────────┐
│  ZYGISK MODULE (C++ Native)                                 │
│  - Camera2 API hooks (CameraManager, ImageReader)           │
│  - Camera1 API hooks (Camera.open, setPreviewSurface)       │
│  - PLT hooks (libcamera_client.so)                          │
│  - Frame publisher (MediaCodec + EGL + ANativeWindow)       │
│  - Metadata spoofing (CameraCharacteristics)                │
│  - Companion process (root IPC)                             │
└─────────────────────────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│  TARGET APP (e.g. banking/crypto app)                       │
│  - Opens camera → gets injected image/video                 │
│  - Liveness check → gets looping video                      │
│  - Thinks it's a real camera                                │
└─────────────────────────────────────────────────────────────┘
```

## Features

- **Camera2 API Hooking** — Intercepts CameraManager.openCamera, ImageReader frames, CaptureSession surfaces
- **Camera1 API Hooking** — Intercepts Camera.open, setPreviewSurface for older apps
- **PLT Hooks** — Hooks native camera libraries (libcamera_client.so) for NDK-based apps
- **Static Image Injection** — Replace camera feed with any image from gallery
- **Video Loop Injection** — Replace camera feed with a looping video for liveness checks
- **Liveness Detection Bypass** — Auto-detects liveness prompts and switches frames
- **Metadata Spoofing** — Fakes CameraCharacteristics (sensor size, focal length, noise)
- **Per-App Targeting** — Choose which apps to inject into
- **Real-time Mode** — Optional face swap via MediaPipe (future)
- **Hidden from Detection** — Hides module package name, spoofed build fingerprint
- **Play Integrity Pass** — Compatible with Play Integrity Fix module

## Prerequisites

- Android device (arm64)
- Root access (Magisk / KernelSU)
- Zygisk enabled (Magisk 24+) or Zygisk Next
- Android SDK + NDK (for building)
- JDK 17+

## Build

```bash
cd kyc_bypasser
./gradlew assembleDebug
```

## Install

1. Install the APK on your device
2. Push the module to `/data/adb/modules/kyc_bypasser/`
3. Enable the module in Magisk/Zygisk manager
4. Open the app, select a target app and media file
5. Enable injection and open the target app

## Project Structure

```
kyc_bypasser/
├── app/                          # Android app (Kotlin + Compose)
│   ├── src/main/java/com/kycbypasser/app/
│   │   ├── MainActivity.kt       # Main screen
│   │   ├── ui/                   # Compose UI
│   │   └── service/              # IPC service
│   ├── src/main/res/             # Resources
│   └── build.gradle.kts          # App build config
├── module/                       # Zygisk module (C++)
│   ├── module.json               # Module metadata
│   ├── root/                     # Files pushed to module root
│   │   ├── config.json           # Active status, media paths
│   │   ├── targets.txt           # Target package names
│   │   └── injected_image.jpg    # Default injected image
│   ├── jni/                      # Native code
│   │   ├── CMakeLists.txt        # CMake config
│   │   ├── zygisk.hpp            # Zygisk API header
│   │   ├── entry.cpp             # Module entry point
│   │   ├── camera_hook.h         # Header
│   │   ├── camera_hook.cpp       # All hooks
│   │   ├── frame_publisher.h     # Header
│   │   ├── frame_publisher.cpp   # Frame rendering
│   │   ├── metadata_spoof.h      # Header
│   │   ├── metadata_spoof.cpp    # CameraChar acteristics spoof
│   │   ├── companion.cpp         # Root companion handler
│   │   └── utils.cpp             # Helpers
│   └── Android.mk                # Alternative build (optional)
├── gradle/                       # Gradle wrapper
├── build.gradle.kts              # Root build config
├── settings.gradle.kts           # Gradle settings
└── gradle.properties             # Gradle properties
```

## How It Works

### 1. Module Loading
- Zygisk loads the module when each app process starts
- The module checks if the app is in the targets list
- If yes, it registers camera hooks

### 2. Camera2 Hooks
- `CameraManager.openCamera()` — replaces camera ID with virtual one
- `ImageReader.acquireNextImage()` — returns injected frames
- `CameraDevice.createCaptureSession()` — replaces output surfaces

### 3. Frame Injection
- Static images: decoded via BitmapFactory, rendered to Surface via EGL
- Videos: decoded via MediaExtractor + MediaCodec, looped

### 4. Metadata Spoofing
- CameraCharacteristics return realistic values
- Sensor size, focal length, noise model, rolling shutter all faked

## License

MIT
