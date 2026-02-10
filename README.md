# android-a11y-kernel

Minimal Android Accessibility kernel for OpenClaw-style UI control.

This build is optimized for **on-device OpenClaw** usage (no desktop forwarding required).

## What it does

- Runs a local Ktor API server on `127.0.0.1:7333`
- Exposes on-demand UI snapshot: `GET /screen`
- Executes actions: `POST /act`
- Supports root fallback executor for stubborn UI actions
- Uses AccessibilityService (no continuous polling loop)

## Endpoints

- `GET /health`
- `GET /capabilities`
- `GET /screen`
- `POST /act`

Auth header for `/screen` and `/act`:

`Authorization: Bearer openclaw-dev-token`

Change token in `app/build.gradle.kts` via `LOCAL_API_TOKEN`.

## Build debug APK

```bash
./gradlew assembleDebug
```

If `./gradlew` is missing on your machine, open this project in Android Studio and run `Build > Build APK(s)` for debug output.

Output APK:

`app/build/outputs/apk/debug/app-debug.apk`

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then enable the app Accessibility Service in Android system settings.

## On-device mode (recommended)

Run OpenClaw/client on the same phone (e.g. Termux). Call directly:

`http://127.0.0.1:7333`

No `adb forward` needed in this mode.

## Connect from desktop (optional)

The API listens on phone localhost. Use adb forward so your desktop can call it:

```bash
adb forward tcp:7333 tcp:7333
```

## Quick test

```bash
TOKEN="openclaw-dev-token"

curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:7333/screen

curl -X POST http://127.0.0.1:7333/act \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"action":"tap","selector":{"by":"text","value":"发送"}}'

curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:7333/capabilities
```

## Python client helper

`scripts/client.py` is a tiny helper so you do not need to hand-write curl.

```bash
python3 scripts/client.py health
python3 scripts/client.py capabilities
python3 scripts/client.py screen
python3 scripts/client.py act --action tap --by text --value "发送"
python3 scripts/client.py act --action type --by id --value "prompt_input" --text "a snow mountain"
python3 scripts/client.py act --action launch_app --package com.google.android.apps.bard
python3 scripts/client.py act --action keyevent --keycode 66
python3 scripts/client.py act --action swipe --from-x 500 --from-y 1600 --to-x 500 --to-y 600 --duration-ms 260
```

## OpenClaw integration (on-device)

Use `scripts/openclaw_driver.py` as your thin adapter in OpenClaw runtime:

```python
from scripts.openclaw_driver import AndroidA11yDriver

driver = AndroidA11yDriver()
screen = driver.screen()
action = {
  "action": "tap",
  "selector": {"by": "text", "value": "发送"},
  "fallback_coordinates": [540, 1800]
}
result = driver.act(action)
```

The driver already uses `127.0.0.1:7333` by default, matching phone-local OpenClaw.
