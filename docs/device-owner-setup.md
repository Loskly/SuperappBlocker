# Appbllocker — Device Owner setup

## Prerequisites

- APK installed on device/emulator
- USB debugging enabled
- No Google account on profile (recommended)
- No existing Device Owner on device

## Command

```bash
adb shell dpm set-device-owner com.ecosentinel.appblocker/.admin.AppBlockerDeviceAdminReceiver
```

Windows full path example:

```powershell
C:\Users\vbrta\AppData\Local\Android\Sdk\platform-tools\adb.exe shell dpm set-device-owner com.ecosentinel.appblocker/.admin.AppBlockerDeviceAdminReceiver
```

## After provisioning

1. Open Appbllocker
2. Grant Usage Access
3. Tap "Apply Device Owner policies"
4. Tap "Start monitoring"

## Remove Device Owner (testing only)

Factory reset removes Device Owner. On emulator: Wipe Data from AVD manager.
