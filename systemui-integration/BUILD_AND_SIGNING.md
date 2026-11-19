# Lux Control Center — Build, Signing & Deployment Guide

## 1. Prerequisites
- **Android Studio**: Ladybug / Meerkat or newer (AGP 9.1.1, Kotlin 2.4.21, JDK 17+)
- **Android SDK**: Compile SDK 36 (Android 15/16 compatible), `minSdk = 24`, `targetSdk = 36`
- **Target Device**: itel City 200 (`C681L`, Android 15 / itel OS 15) or any Android 12–15 device.

---

## 2. Debug APK Build (Android Studio & CLI)
In Android Studio:
- Open the project root directory and wait for Gradle sync.
- Run **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

From CLI:
```bash
gradle :app:assembleDebug
```
Output APK:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 3. Running Unit & Robolectric Regression Tests
```bash
gradle :app:testDebugUnitTest
```

---

## 4. Generating a Release Keystore & Building a Signed Release APK

### Step A — Create an Upload/Release Keystore (one-time)
```bash
keytool -genkeypair -v \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

### Step B — Export Signing Environment Variables
The `app/build.gradle.kts` `release` signingConfig reads credentials securely from environment variables (never hardcode passwords in Gradle files):
```bash
export KEYSTORE_PATH="$(pwd)/my-upload-key.jks"
export STORE_PASSWORD="<your-keystore-password>"
export KEY_PASSWORD="<your-key-password>"
```

### Step C — Build Signed Release APK or App Bundle
```bash
gradle :app:assembleRelease
# Or for Play/distribution bundle:
gradle :app:bundleRelease
```
Signed APK output:
`app/build/outputs/apk/release/app-release.apk`

### Step D — Verify APK Signature
```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

---

## 5. Installing & Enabling on itel City 200 (C681L)

1. Enable **Developer Options → USB Debugging** on the itel City 200.
2. Install the signed APK:
   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```
3. *(Optional ADB Elevation — No Root)* Grant `WRITE_SECURE_SETTINGS`:
   ```bash
   adb shell pm grant com.aistudio.luxcontrol.vqxkpm android.permission.WRITE_SECURE_SETTINGS
   ```
4. Open **Lux Control → System** tab and grant:
   - **Display over other apps**
   - **Accessibility Service** (*Lux Control Edge & Screenshot*)
   - **Modify System Settings**
   - **Do Not Disturb Policy Access**
   - **Notification Access** (*Lux Control Media Bridge*)
   - **Runtime Telemetry Permissions** (`BLUETOOTH_CONNECT`, `READ_PHONE_STATE`, `POST_NOTIFICATIONS`, `RECORD_AUDIO`)
5. Open **Studio** tab and enable **Top-Right Corner Swipe Overlay**.
6. Note for **itel OS 15 Battery Optimization**: Exclude **Lux Control** under *Settings → Battery → App Power Management / Auto-start* so itel OS does not terminate `LuxEdgeGestureService` or `LuxControlAccessibilityService` in the background.

---

## 6. True AOSP 15 SystemUI Replacement (Unlocked Bootloader / Custom ROM Only)
1. Copy the unsigned release APK into AOSP source: `packages/apps/LuxControlCenter/LuxControlCenter-release-unsigned.apk`.
2. Copy `systemui-integration/aosp15/Android.bp` and `privapp-permissions-lux-control-center.xml` into `packages/apps/LuxControlCenter/`.
3. Add `LuxControlCenter` to `PRODUCT_PACKAGES` in your device makefile.
4. Integrate `systemui-integration/aosp15/LuxSplitShadeInterceptPatch.kt` into `frameworks/base/packages/SystemUI/src/com/android/systemui/shade/` and wire `maybeInterceptTopRightQuickSettings(...)` inside `NotificationShadeWindowView.onInterceptTouchEvent(...)`.
5. Rebuild SystemUI and system_ext images with `m SystemUI LuxControlCenter`.
