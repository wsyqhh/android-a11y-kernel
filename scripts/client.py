#!/usr/bin/env python3
"""Small CLI client for android-a11y-kernel local API."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request


def request_json(method: str, url: str, token: str, payload: dict | None = None) -> dict:
    data = None
    headers = {"Authorization": f"Bearer {token}"}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url=url, method=method, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=8) as resp:
            body = resp.read().decode("utf-8")
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="ignore")
        raise RuntimeError(f"HTTP {e.code}: {detail}") from e
    except urllib.error.URLError as e:
        raise RuntimeError(f"Network error: {e}") from e


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Client for android-a11y-kernel API")
    parser.add_argument("--host", default=os.getenv("A11Y_HOST", "127.0.0.1"), help="API host")
    parser.add_argument("--port", type=int, default=int(os.getenv("A11Y_PORT", "7333")), help="API port")
    parser.add_argument(
        "--token",
        default=os.getenv("A11Y_TOKEN", "openclaw-dev-token"),
        help="Bearer token",
    )

    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("health", help="GET /health")
    sub.add_parser("screen", help="GET /screen")

    act = sub.add_parser("act", help="POST /act")
    act.add_argument("--action", required=True, choices=["tap", "type", "scroll", "back", "home", "wait", "done"])
    act.add_argument("--by", choices=["id", "text", "desc", "class"], help="Selector type")
    act.add_argument("--value", help="Selector value")
    act.add_argument("--text", help="Input text for type action")
    act.add_argument("--direction", choices=["forward", "backward"], help="Scroll direction")
    act.add_argument("--timeout-ms", type=int, help="Wait timeout milliseconds")
    act.add_argument("--fallback-x", type=int, help="Fallback tap x")
    act.add_argument("--fallback-y", type=int, help="Fallback tap y")

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    base = f"http://{args.host}:{args.port}"

    if args.command == "health":
        result = request_json("GET", f"{base}/health", token=args.token)
    elif args.command == "screen":
        result = request_json("GET", f"{base}/screen", token=args.token)
    else:
        payload: dict = {"action": args.action}
        if args.by and args.value:
            payload["selector"] = {"by": args.by, "value": args.value}
        if args.text is not None:
            payload["text"] = args.text
        if args.direction is not None:
            payload["direction"] = args.direction
        if args.timeout_ms is not None:
            payload["timeout_ms"] = args.timeout_ms
        if args.fallback_x is not None and args.fallback_y is not None:
            payload["fallback_coordinates"] = [args.fallback_x, args.fallback_y]

        result = request_json("POST", f"{base}/act", token=args.token, payload=payload)

    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as e:
        print(str(e), file=sys.stderr)
        raise SystemExit(1)
