#!/usr/bin/env python3
"""
Fires a burst of orders for a single SKU and checks the stock arithmetic afterwards.

Production traffic is not one order at a time. This script reproduces the shape of a
flash sale: many orders for the same item, arriving together, processed by however many
consumer threads the pipeline has.

Usage:
    python scripts/burst_orders.py --sku RS-WIDGET-01 --count 40 --quantity 1
"""

import argparse
import base64
import json
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor


def call(base, auth, method, path, body=None):
    url = f"{base}{path}"
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Authorization", "Basic " + base64.b64encode(auth.encode()).decode())
    if data is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, json.loads(response.read().decode() or "{}")
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read().decode() or "{}")


def stock_for(base, auth, sku):
    _, rows = call(base, auth, "GET", "/api/platform/stock")
    for row in rows:
        if row["sku"] == sku:
            return row
    raise SystemExit(f"unknown sku {sku}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--user", default="orders@riverstone.test")
    parser.add_argument("--password", default="Password123!")
    parser.add_argument("--ops-user", default="platform@riverstone.test")
    parser.add_argument("--ops-password", default="Admin123!")
    parser.add_argument("--sku", default="RS-WIDGET-01")
    parser.add_argument("--count", type=int, default=40)
    parser.add_argument("--quantity", type=int, default=1)
    parser.add_argument("--threads", type=int, default=16)
    parser.add_argument("--settle", type=int, default=10, help="seconds to wait for the pipeline to drain")
    args = parser.parse_args()

    auth = f"{args.user}:{args.password}"
    ops = f"{args.ops_user}:{args.ops_password}"

    before = stock_for(args.base, ops, args.sku)
    print(f"sku            {args.sku}")
    print(f"available before {before['available']}  reserved before {before['reserved']}")

    def place(_):
        return call(args.base, auth, "POST", "/api/orders",
                    {"customerRef": "CUST-BURST", "sku": args.sku, "quantity": args.quantity})[0]

    with ThreadPoolExecutor(max_workers=args.threads) as pool:
        statuses = list(pool.map(place, range(args.count)))

    accepted = sum(1 for status in statuses if status == 202)
    print(f"orders         {len(statuses)} sent, {accepted} accepted")

    print(f"waiting {args.settle}s for the pipeline to drain...")
    time.sleep(args.settle)

    after = stock_for(args.base, ops, args.sku)
    expected_available = before["available"] - accepted * args.quantity
    expected_reserved = before["reserved"] + accepted * args.quantity

    print(f"available after  {after['available']}  expected {expected_available}")
    print(f"reserved after   {after['reserved']}  expected {expected_reserved}")

    drift = after["available"] - expected_available
    if drift != 0:
        print(f"MISMATCH: stock is out by {drift} units")
    else:
        print("stock arithmetic is consistent")


if __name__ == "__main__":
    main()
