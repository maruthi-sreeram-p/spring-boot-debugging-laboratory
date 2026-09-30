"""
Fires a burst of simultaneous borrow requests at the circulation desk.

The desk normally serves one person at a time. Self-service kiosks in four branches do not,
and neither does the mobile app. This reproduces that.

Usage:
    python scripts/concurrent_borrows.py --book 1 --members 1,2,3,4,6,7
    python scripts/concurrent_borrows.py --book 1 --members 2 --repeat 6

Requires: pip install requests
"""

import argparse
import concurrent.futures
import json
import sys

try:
    import requests
except ImportError:  # pragma: no cover
    sys.exit("pip install requests")

BASE = "http://localhost:8080"
AUTH = ("librarian@athenaeum.test", "Admin123!")


def borrow(book_id, member_id):
    try:
        response = requests.post(
            BASE + "/api/circulation/loans",
            auth=AUTH,
            json={"bookId": book_id, "memberId": member_id},
            timeout=20,
        )
    except requests.RequestException as error:
        return {"member": member_id, "status": "error", "detail": str(error)}

    body = {}
    try:
        body = response.json()
    except ValueError:
        pass

    return {
        "member": member_id,
        "status": response.status_code,
        "loan": body.get("id"),
        "copy": body.get("copyId"),
        "barcode": body.get("barcode"),
        "message": body.get("message"),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--book", type=int, required=True, help="book id to borrow")
    parser.add_argument("--members", default="1,2,3,4,6,7",
                        help="comma separated member ids, one request each")
    parser.add_argument("--repeat", type=int, default=1,
                        help="send each member's request this many times")
    args = parser.parse_args()

    requests_to_send = []
    for member in [int(value) for value in args.members.split(",")]:
        requests_to_send.extend([member] * args.repeat)

    print("firing {} simultaneous borrow requests for book {}".format(
        len(requests_to_send), args.book))

    with concurrent.futures.ThreadPoolExecutor(max_workers=len(requests_to_send)) as pool:
        futures = [pool.submit(borrow, args.book, member) for member in requests_to_send]
        results = [future.result() for future in futures]

    for result in sorted(results, key=lambda item: (item["member"], str(item.get("loan")))):
        print(json.dumps(result))

    issued = [result for result in results if result["status"] == 201]
    copies = [result["copy"] for result in issued]
    print()
    print("issued        :", len(issued))
    print("refused       :", len(results) - len(issued))
    print("distinct copies:", len(set(copies)))

    print()
    print("Now ask the database what it thinks:")
    print("  docker exec lab-mysql mysql -ulabuser -plabpass librarydb -e \\")
    print("    \"SELECT copy_id, COUNT(*) FROM loans WHERE status='ACTIVE' \\")
    print("     GROUP BY copy_id HAVING COUNT(*) > 1;\"")
    print("  docker exec lab-mysql mysql -ulabuser -plabpass librarydb -e \\")
    print("    \"SELECT m.id, m.active_loan_count, COUNT(l.id) AS real_loans \\")
    print("     FROM members m LEFT JOIN loans l ON l.member_id = m.id AND l.status='ACTIVE' \\")
    print("     GROUP BY m.id, m.active_loan_count;\"")


if __name__ == "__main__":
    main()
