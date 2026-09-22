#!/usr/bin/env python3
"""
Fires a burst of concurrent deposits at one account and reports what happened.

The teller front end retries, mobile clients double-submit and the batch importer runs
several workers, so the same account regularly receives simultaneous credits. This script
reproduces that shape of load against a local instance.

Usage:
    python scripts/concurrent_deposits.py --account 1 --count 50 --amount 100.00

Defaults assume the seeded account MB-0000-1001 (id 1) owned by devika.rao@example.com.
"""

import argparse
import base64
import json
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal


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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--user", default="devika.rao@example.com")
    parser.add_argument("--password", default="Password123!")
    parser.add_argument("--account", type=int, default=1)
    parser.add_argument("--count", type=int, default=50)
    parser.add_argument("--amount", default="100.00")
    parser.add_argument("--threads", type=int, default=16)
    args = parser.parse_args()

    auth = f"{args.user}:{args.password}"

    status, account = call(args.base, auth, "GET", f"/api/accounts/{args.account}")
    if status != 200:
        raise SystemExit(f"could not read account {args.account}: HTTP {status} {account}")

    before = Decimal(str(account["balance"]))
    print(f"account       {account['accountNumber']}")
    print(f"balance before {before}")

    def deposit(_):
        return call(args.base, auth, "POST",
                    f"/api/accounts/{args.account}/deposits",
                    {"amount": args.amount, "description": "load test credit"})[0]

    with ThreadPoolExecutor(max_workers=args.threads) as pool:
        statuses = list(pool.map(deposit, range(args.count)))

    accepted = sum(1 for s in statuses if s == 201)
    rejected = len(statuses) - accepted

    status, account = call(args.base, auth, "GET", f"/api/accounts/{args.account}")
    after = Decimal(str(account["balance"]))

    expected = before + Decimal(args.amount) * accepted

    print(f"requests      {len(statuses)} sent, {accepted} accepted, {rejected} rejected")
    print(f"balance after  {after}")
    print(f"expected       {expected}")
    print(f"difference     {after - expected}")
    if after != expected:
        print("MISMATCH: the balance does not account for every accepted deposit")
    else:
        print("balance matches the accepted deposits")


if __name__ == "__main__":
    main()
