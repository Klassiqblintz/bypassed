/*
 * KYC Bypasser Xposed Module (standalone JAR).
 * Works inside VirtualXposed (no root needed).
 *
 * SIMPLIFIED — uses the Xposed bridge directly for all hooks.
 * No reflection on Xposed itself.
 *
 * BUILD:
 *   javac -cp xposed-api-82.jar XposedInit.java
 *   jar cf kyc_bypasser_xposed.jar XposedInit.class
 *
 * INSTALL IN VIRTUALEXPOSED:
 *   1. Install VirtualXposed APK on your phone
 *   2. Open VirtualXposed → Add App → select this JAR as a module
 *   3. Open Xposed Framework in VirtualXposed → Modules → enable KYC Bypasser
 *   4. Reboot VirtualXposed (Settings → Reboot)
 *   5. In VirtualXposed, Add App → clone your target KYC app (e.g., PhonePe)
 *   6. Launch the cloned app from VirtualXposed
 *   7. Go to KYC/camera screen → your virtual camera is injected
 */

// Xposed API classes — provided by VirtualXposed at runtime.
// These imports resolve from the Xposed framework bundled in VirtualXposed.
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.List;

public class XposedInit implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam)
        throws Throwable
    {
        // Only hook target apps
        if (!isTargetApp(lpparam.packageName)) return;

        // Load config
        Config config = loadConfig();
        if (!config.active) return;

        ClassLoader cl = lpparam.classLoader;

        // ===== CAMERA2 HOOKS =====

        // 1. CameraManager.openCamera — replace camera ID
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraManager",
            cl, "openCamera",
            String.class,
            Class.forName(
                "android.hardware.camera2.CameraDevice$Callback"),
            java.util.concurrent.Executor.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.args[0] = "100"; // virtual camera ID
                }
            }
        );

        // 2. CameraDevice.createCaptureSession — replace first output
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraDevice",
            cl, "createCaptureSession",
            List.class,
            Class.forName(
                "android.hardware.camera2.CameraCaptureSession$Callback"),
            android.os.Handler.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param)
                    throws Throwable
                {
                    @SuppressWarnings("unchecked")
                    java.util.List<android.view.Surface> outputs =
                        (java.util.List<android.view.Surface>) param.args[0];
                    if (outputs != null && outputs.size() > 0) {
                        // Create virtual surface
                        android.graphics.SurfaceTexture st =
                            new android.graphics.SurfaceTexture(1);
                        st.setDefaultBufferSize(720, 1280);
                        android.view.Surface vs =
                            new android.view.Surface(st);

                        // Draw image if we have one
                        if (config.imagePath != null
                            && !config.imagePath.isEmpty())
                        {
                            drawImageOnSurface(vs, config.imagePath);
                        }

                        outputs.set(0, vs);
                    }
                }
            }
        );

        // 3. CameraCharacteristics.getInt — spoof metadata
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraCharacteristics",
            cl, "getInt",
            android.hardware.camera2.CameraCharacteristics.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param)
                    throws Throwable
                {
                    String key = param.args[0].toString();
                    if (key.contains("SUPPORTED_HARDWARE_LEVEL"))
                        param.setResult(3);
                    if (key.contains("FLASH_INFO_AVAILABLE"))
                        param.setResult(0);
                    if (key.contains("CONTROL_AE_MODE"))
                        param.setResult(3);
                    if (key.contains("CONTROL_AF_MODE"))
                        param.setResult(2);
                    if (key.contains("LENS_FOCAL_LENGTH"))
                        param.setResult(4);
                    if (key.contains("STATISTICS_INFO_MAX_REGIONS"))
                        param.setResult(10);
                }
            }
        );

        // 4. CameraCharacteristics.getFloat — spoof float metadata
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraCharacteristics",
            cl, "getFloat",
            android.hardware.camera2.CameraCharacteristics.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param)
                    throws Throwable
                {
                    String key = param.args[0].toString();
                    if (key.contains("SENSOR_INFO_PHYSICAL_SIZE"))
                        param.setResult(new float[]{0.008f, 0.006f});
                    if (key.contains("ROLLING_SHUTTER_TIMING"))
                        param.setResult(new float[]{0.001f, 0.001f});
                    if (key.contains("NOISE_PROFILE")) {
                        float[] np = new float[100];
                        for (int i = 0; i < 100; i++) np[i] = i / 100.0f;
                        param.setResult(np);
                    }
                }
            }
        );

        // ===== CAMERA1 HOOKS =====

        // 5. Camera.open() — return virtual camera
        XposedHelpers.findAndHookMethod(
            "android.hardware.Camera",
            cl, "open", int.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param)
                    throws Throwable
                {
                    param.setResult(
                        android.hardware.Camera.open());
                }
            }
        );

        // 6. Camera.setPreviewDisplay — use virtual surface
        XposedHelpers.findAndHookMethod(
            "android.hardware.Camera",
            cl, "setPreviewDisplay",
            android.view.SurfaceHolder.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param)
                    throws Throwable
                {
                    // Create virtual surface
                    android.graphics.SurfaceTexture st =
                        new android.graphics.SurfaceTexture(1);
                    android.view.Surface vs =
                        new android.view.Surface(st);

                    // Draw image
                    if (config.imagePath != null
                        && !config.imagePath.isEmpty())
                    {
                        drawImageOnSurface(vs, config.imagePath);
                    }

                    // Wrap in SurfaceHolder
                    android.view.SurfaceView sv =
                        new android.view.SurfaceView(null);
                    param.args[0] = sv.getHolder();
                }
            }
        );
    }

    // ===== CONFIG LOADER =====
    static class Config {
        boolean active = false;
        String mode = "static";
        String imagePath = "";
        String videoPath = "";
    }

    static Config loadConfig() {
        Config config = new Config();
        String[] paths = {
            "/storage/emulated/0/kyc_bypasser/config.json",
            "/data/adb/modules/kyc_bypasser/config.json"
        };

        for (String path : paths) {
            try {
                java.io.BufferedReader r =
                    new java.io.BufferedReader(
                        new java.io.FileReader(path));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null)
                    sb.append(line);
                r.close();

                String json = sb.toString();
                if (!json.contains("\"active\"")) continue;
                config.active = json.contains(
                    "\"active\" : true")
                    || json.contains("\"active\":true");

                int mi = json.indexOf("\"mode\"");
                if (mi >= 0) {
                    int s = json.indexOf("\"", mi + 7);
                    int e = json.indexOf("\"", s + 1);
                    if (s > 0 && e > s) config.mode =
                        json.substring(s + 1, e);
                }

                int ii = json.indexOf("\"image_path\"");
                if (ii >= 0) {
                    int s = json.indexOf("\"", ii + 11);
                    int e = json.indexOf("\"", s + 1);
                    if (s > 0 && e > s) config.imagePath =
                        json.substring(s + 1, e);
                }

                int vi = json.indexOf("\"video_path\"");
                if (vi >= 0) {
                    int s = json.indexOf("\"", vi + 11);
                    int e = json.indexOf("\"", s + 1);
                    if (s > 0 && e > s) config.videoPath =
                        json.substring(s + 1, e);
                }
                return config;
            } catch (Exception ignored) {}
        }
        return config;
    }

    // ===== TARGET CHECKER =====
    static boolean isTargetApp(String packageName) {
        String[] paths = {
            "/storage/emulated/0/kyc_bypasser/targets.txt",
            "/data/adb/modules/kyc_bypasser/targets.txt"
        };
        for (String path : paths) {
            try {
                java.io.BufferedReader r =
                    new java.io.BufferedReader(
                        new java.io.FileReader(path));
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (line.equals(packageName)) {
                        r.close();
                        return true;
                    }
                }
                r.close();
            } catch (Exception ignored) {}
        }
        return false;
    }

    // ===== IMAGE RENDERER =====
    static void drawImageOnSurface(
        android.view.Surface surface, String path)
    {
        try {
            android.graphics.Bitmap bitmap =
                android.graphics.BitmapFactory.decodeFile(path);
            if (bitmap == null) return;

            // Anti-detection noise (±1 per channel)
            if (bitmap.getConfig() ==
                android.graphics.Bitmap.Config.ARGB_8888)
            {
                android.graphics.Bitmap noisy =
                    bitmap.copy(
                        android.graphics.Bitmap.Config.ARGB_8888, true);
                int w = noisy.getWidth();
                int h = noisy.getHeight();
                int[] px = new int[w * h];
                noisy.getPixels(px, 0, w, 0, 0, w, h);
                for (int i = 0; i < px.length; i++) {
                    int p = px[i];
                    int r = ((p >> 16) & 0xFF);
                    int g = ((p >> 8) & 0xFF);
                    int b = (p & 0xFF);
                    r = Math.max(0, Math.min(255,
                        r + (int)(Math.random() * 3) - 1));
                    g = Math.max(0, Math.min(255,
                        g + (int)(Math.random() * 3) - 1));
                    b = Math.max(0, Math.min(255,
                        b + (int)(Math.random() * 3) - 1));
                    px[i] = (p & 0xFF000000) | (r << 16)
                        | (g << 8) | b;
                }
                noisy.setPixels(px, 0, w, 0, 0, w, h);
                bitmap.recycle();
                bitmap = noisy;
            }

            android.graphics.Canvas canvas =
                surface.lockCanvas(null);
            if (canvas != null) {
                canvas.drawBitmap(bitmap, null, null);
                surface.unlockCanvasAndPost(canvas);
            }
            bitmap.recycle();
        } catch (Exception ignored) {}
    }
}
