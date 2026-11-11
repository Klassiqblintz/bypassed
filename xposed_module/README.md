# KYC Bypasser — VirtualXposed (No-Root) Version

A camera bypass module that works inside VirtualXposed — **no root needed**.

## How It Works

VirtualXposed creates a **virtual Android sandbox** inside a single APK. Apps cloned into this sandbox run alongside Xposed modules. Our module hooks each cloned app's Camera2/Camera1 APIs and replaces the real camera with a virtual feed.

**The chain:**
```
Your phone → VirtualXposed (sandbox) → cloned KYC app
                                    ↕ Xposed hooks
                              our camera injection
```

## Installation Steps (One at a time)

### Step 1: Install VirtualXposed
Download VirtualXposed APK from:
https://github.com/android-hacker/VirtualXposed/releases

Install it normally — **no root needed**.

### Step 2: Install our Xposed module
- Open VirtualXposed
- Tap the drawer/menu button
- Tap **"Add App"**
- Go to the **"APK"** tab (not "Installed apps")
- Select `kyc_bypasser_xposed.jar` from `/storage/emulated/0/kyc_bypasser/xposed_module/`
- Wait for it to install

### Step 3: Enable the module
- Open **Xposed Framework** inside VirtualXposed (it's in the app drawer)
- Go to **Modules** tab
- Find **"KYC Bypasser (Xposed)"** and enable it
- Tap **Reboot** inside VirtualXposed

### Step 4: Add your target KYC app
- Open VirtualXposed
- Tap **Add App**
- Go to **Installed apps** tab
- Find your target app (e.g., PhonePe, Paytm, etc.)
- Add it to VirtualXposed

### Step 5: Configure
- Place your face image at: `/storage/emulated/0/kyc_bypasser/face.jpg`
- Edit `/storage/emulated/0/kyc_bypasser/config.json`:
  ```json
  {
    "active": true,
    "mode": "static",
    "image_path": "/storage/emulated/0/kyc_bypasser/face.jpg"
  }
  ```
- Add target package name to `/storage/emulated/0/kyc_bypasser/targets.txt`:
  ```
  com.phonepe.app
  ```

### Step 6: Test
- Open your target KYC app **FROM INSIDE** VirtualXposed
- Navigate to the KYC / camera verification screen
- Your virtual camera feed (the face image) should be injected

## Files

```
xposed_module/
├── src/main/java/com/kycbypasser/xposed/
│   └── XposedInit.java        ← Main module (6 hooks installed)
├── src/main/assets/
│   └── xposed.xml             ← Xposed module metadata
├── src/main/
│   └── AndroidManifest.xml    ← Permissions
├── build.gradle               ← Gradle build config
├── settings.gradle            ← Gradle settings
├── build.sh                   ← Termux build script
└── README.md                  ← This file
```

## What Gets Hooked (6 total)

| # | API | What it does |
|---|-----|-------------|
| 1 | `CameraManager.openCamera()` | Replaces camera ID with "100" (virtual) |
| 2 | `CameraDevice.createCaptureSession()` | Replaces first output surface with our virtual surface + draws image |
| 3 | `CameraCharacteristics.getInt()` | Spoofs HARDWARE_LEVEL=FULL, AE_MODE=ON, AF_MODE=CONTINUOUS |
| 4 | `CameraCharacteristics.getFloat()` | Spoofs SENSOR_PHYSICAL_SIZE, ROLLING_SHUTTER, NOISE_PROFILE |
| 5 | `Camera.open()` (Camera1) | Returns virtual camera object |
| 6 | `Camera.setPreviewDisplay()` (Camera1) | Draws image on virtual surface |

## Anti-Detection

- **Pixel noise**: ±1 per RGB channel per frame
- **Metadata spoofing**: FULL hardware level, realistic noise profile
- **Physical size**: 8.6×6.4mm (matches real phone cameras)
- **Focal length**: 4.3mm (standard for phone front cameras)

## Comparison: Zygisk vs VirtualXposed

| Feature | Zygisk (Root) | VirtualXposed (No Root) |
|---------|--------------|------------------------|
| Scope | System-wide (all apps) | Per-app (only cloned apps) |
| Native C++ lib | ✅ Full | ❌ Java-only |
| Detection | Can hide module | App knows it's in sandbox |
| Performance | Native | Slightly slower |
| Root required | ✅ Yes | ❌ No |
| Setup | Flash module via Magisk | Install JAR in VirtualXposed |
| Best for | Deep integration | Quick testing without root |

## Tips

- Each target app must be cloned separately inside VirtualXposed
- Config is shared — all cloned apps use the same image/video
- If hooks don't fire, reboot VirtualXposed (not the phone)
- Android 14+: Xposed may need to be enabled manually in VirtualXposed settings
- The cloned app runs in a sandbox — it can't see your real app's data
- Some KYC apps detect VirtualXposed — try renaming the package if needed

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Camera still shows real view | Check module is enabled in Xposed → Modules |
| App crashes on camera | Try a different image resolution |
| Module not appearing | Reinstall JAR in VirtualXposed |
| Wrong camera ID error | Change "100" to another unused ID |
| Image not showing | Check image_path in config.json |
