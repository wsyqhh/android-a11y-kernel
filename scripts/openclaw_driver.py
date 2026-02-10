#!/usr/bin/env python3
"""On-device driver wrapper for OpenClaw -> android-a11y-kernel.

This module is intended to run on the Android device (e.g. Termux),
calling the local API directly at 127.0.0.1:7333.
"""

from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, Optional


@dataclass
class DriverConfig:
    host: str = os.getenv("A11Y_HOST", "127.0.0.1")
    port: int = int(os.getenv("A11Y_PORT", "7333"))
    token: str = os.getenv("A11Y_TOKEN", "openclaw-dev-token")
    timeout_s: int = 8
    retries: int = 2


class AndroidA11yDriver:
    def __init__(self, config: Optional[DriverConfig] = None):
        self.config = config or DriverConfig()
        self.base = f"http://{self.config.host}:{self.config.port}"

    def health(self) -> Dict[str, Any]:
        return self._request("GET", "/health", auth=False)

    def capabilities(self) -> Dict[str, Any]:
        return self._request("GET", "/capabilities")

    def screen(self) -> Dict[str, Any]:
        return self._request("GET", "/screen")

    def act(self, action: Dict[str, Any]) -> Dict[str, Any]:
        return self._request("POST", "/act", payload=action)

    def step(self, action: Dict[str, Any], verify_text: Optional[str] = None, wait_after_ms: int = 120) -> Dict[str, Any]:
        result = self.act(action)
        if wait_after_ms > 0:
            time.sleep(wait_after_ms / 1000)
        if verify_text:
            snap = self.screen()
            ok = _screen_has_text(snap, verify_text)
            result["verify_ok"] = ok
            result["verify_text"] = verify_text
        return result

    def _request(self, method: str, path: str, payload: Optional[Dict[str, Any]] = None, auth: bool = True) -> Dict[str, Any]:
        headers = {}
        if auth:
            headers["Authorization"] = f"Bearer {self.config.token}"
        data = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")

        url = f"{self.base}{path}"
        last_err: Optional[Exception] = None
        for i in range(self.config.retries + 1):
            req = urllib.request.Request(url=url, method=method, data=data, headers=headers)
            try:
                with urllib.request.urlopen(req, timeout=self.config.timeout_s) as resp:
                    body = resp.read().decode("utf-8")
                    return json.loads(body) if body else {}
            except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
                last_err = exc
                if i < self.config.retries:
                    time.sleep(0.2 * (i + 1))
                    continue
                raise RuntimeError(f"request failed {method} {path}: {exc}") from exc

        raise RuntimeError(f"request failed {method} {path}: {last_err}")


def _screen_has_text(screen_payload: Dict[str, Any], text: str) -> bool:
    elements = screen_payload.get("elements", [])
    t = text.lower()
    for item in elements:
        txt = (item.get("text") or "").lower()
        desc = (item.get("content_desc") or "").lower()
        if t in txt or t in desc:
            return True
    return False


if __name__ == "__main__":
    driver = AndroidA11yDriver()
    print(json.dumps(driver.health(), ensure_ascii=False, indent=2))
    print(json.dumps(driver.capabilities(), ensure_ascii=False, indent=2))
