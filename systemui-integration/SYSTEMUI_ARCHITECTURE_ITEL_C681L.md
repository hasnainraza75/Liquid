# itel City 200 (C681L) — Android 15 OEM SystemUI Architecture & Replacement Audit

## 1. Target Hardware & Firmware Profile
| Attribute | Verified Value |
| --- | --- |
| **Device** | itel City 200 (Model `C681L`) |
| **Chipset / SoC** | Unisoc T7250 / Spreadtrum `UMS9230E` (12 nm: 2× Cortex-A75 @ 1.8 GHz + 6× Cortex-A55 @ 1.6 GHz) |
| **GPU** | ARM Mali-G57 MP1 (~850 MHz) |
| **Display** | 6.78" HD+ IPS LCD (720 × 1576, 120 Hz) |
| **Operating System** | Android 15 (`SDK 35`) — **itel OS 15** (Transsion OEM fork) |
| **Stock Firmware Format** | Signed Unisoc `.pac` packages (e.g., `C681L-15.1.2.165SP09`, `C681L-15.1.2.175SP05`, `C681L-15.1.2.200SP01`) |

---

## 2. Why an Ordinary APK Cannot Replace Stock `com.android.systemui`

1. **Platform Signature Enforcement**:
   - The native notification shade & Quick Settings panel reside in `/system_ext/priv-app/SystemUI/SystemUI.apk` (`package="com.android.systemui"`), signed with **Transsion / itel's private OEM platform release key**.
   - Android's `PackageManagerService` refuses to install, upgrade, or override `com.android.systemui` using any third-party signing key.
2. **Locked Bootloader & Android Verified Boot (AVB 2.0) on Unisoc `C681L`**:
   - The itel City 200 ships with a locked bootloader and signed `vbmeta` / `dm-verity` partitions.
   - Modern Unisoc Android 15 builds disable unauthenticated `fastboot oem unlock` commands and require authorized factory tokens; there is no official OEM unlock portal or official LineageOS/AOSP device tree for `C681L`.
3. **Window Z-Order & Input Routing (`TYPE_STATUS_BAR` vs Third-Party Overlays)**:
   - On stock unrooted Android 15, the native status bar window (`WindowManager.LayoutParams.TYPE_STATUS_BAR`) runs in `com.android.systemui` above standard `TYPE_APPLICATION_OVERLAY` windows.
   - Therefore, an unprivileged APK **cannot delete or suppress** the OEM Quick Settings shade inside `SystemUI.apk`.

---

## 3. Supported Integration Tiers

### Tier 1 — Stock Locked `itel City 200 C681L` (Zero Root / Working Today)
- Install the signed **Lux Control** APK (`com.aistudio.luxcontrol.vqxkpm`).
- Grant:
  1. **Display over other apps** (`SYSTEM_ALERT_WINDOW`)
  2. **Accessibility Service** (`LuxControlAccessibilityService` — hosts an elevated `TYPE_ACCESSIBILITY_OVERLAY` top-right swipe strip and executes real native OS screenshots via `GLOBAL_ACTION_TAKE_SCREENSHOT`)
  3. **Modify System Settings** (`WRITE_SETTINGS` — direct Brightness & Auto-Rotate control)
  4. **Do Not Disturb Policy Access** (direct DND toggling)
  5. **Notification Access** (`LuxMediaNotificationListener` — real live MediaSession track metadata, album art & transport controls)
- Swiping down from the calibrated **top-right corner zone** launches `LuxOverlayShadeActivity` with real Android 12–15 hardware cross-window blur (`WindowManager.LayoutParams.FLAG_BLUR_BEHIND` + `Window.setBackgroundBlurRadius()`).
- **Important GPU Note (Mali-G57 MP1 / itel OS 15)**: If itel OS 15 disables cross-window blur under Battery Saver or `ro.surface_flinger.supports_background_blur=0`, `WindowManager.isCrossWindowBlurEnabled` reports `false` cleanly and Lux Control automatically falls back to multi-layer translucent refractive glass without crashing.

### Tier 2 — Stock `C681L` + ADB Elevated Grant (No Root, No Bootloader Unlock)
Run over USB debugging to grant `WRITE_SECURE_SETTINGS`:
```bash
adb shell pm grant com.aistudio.luxcontrol.vqxkpm android.permission.WRITE_SECURE_SETTINGS
```

### Tier 3 — True SystemUI Replacement (Unlocked Bootloader + AOSP 15 / GSI or OEM Build)
True replacement of the native Android 15 Quick Settings panel at `TYPE_NOTIFICATION_SHADE` requires either:
- **OEM-signed SystemUI build** signed with Transsion's platform key, OR
- **Unlocked bootloader running an AOSP 15 / Treble GSI ROM** built with the included integration files in `systemui-integration/aosp15/`:
  - `Android.bp` — Soong privileged app / SystemUI module definition (`certificate: "platform"`, `system_ext_specific: true`).
  - `privapp-permissions-lux-control-center.xml` — Privileged permission allowlist (`STATUS_BAR_SERVICE`, `TETHER_PRIVILEGED`, `NETWORK_SETTINGS`, `MODIFY_PHONE_STATE`, `DEVICE_POWER`, `CONTROL_DISPLAY_BRIGHTNESS`).
  - `LuxSplitShadeInterceptPatch.kt` — SystemUI shade gesture router intercepting top-right downward swipes (`x >= displayWidth * 0.65f`) directly inside `frameworks/base/packages/SystemUI`.
