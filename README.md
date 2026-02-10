# android-a11y-kernel

Minimal Android Accessibility kernel for OpenClaw-style UI control.

## What it does

- Runs a local Ktor API server on `127.0.0.1:7333`
- Exposes on-demand UI snapshot: `GET /screen`
- Executes actions: `POST /act`
- Uses AccessibilityService (no continuous polling loop)

## Endpoints

- `GET /health`
- `GET /screen`
- `POST /act`

Auth header for `/screen` and `/act`:

`Authorization: Bearer openclaw-dev-token`

Change token in `app/build.gradle.kts` via `LOCAL_API_TOKEN`.

## Build debug APK

```bash
./gradlew assembleDebug
```

Output APK:

`app/build/outputs/apk/debug/app-debug.apk`

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then enable the app Accessibility Service in Android system settings.

## Quick test

```bash
TOKEN="openclaw-dev-token"

curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:7333/screen

curl -X POST http://127.0.0.1:7333/act \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"action":"tap","selector":{"by":"text","value":"发送"}}'
```
