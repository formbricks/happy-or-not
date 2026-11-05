#!/usr/bin/env python3
"""A stand-in for the Formbricks Feedback Records API, used by CI to check what the kiosk sends.

It implements just enough of `/api/v3/feedbackRecords` to exercise the app:
  POST  validates the record like Formbricks does (API key, dataset, required members,
        value member matching field_type) and answers 201, 409 for a duplicate
        (tenant_id, submission_id, field_id), or 400/401/403.
  GET   answers 200 with an empty page (used by the app's "Test connection").

Every accepted record is appended as one JSON line to the output file.

Usage: mock-formbricks.py <records.jsonl> [port] [api-key] [dataset-id]
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

OUT = sys.argv[1]
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8787
API_KEY = sys.argv[3] if len(sys.argv) > 3 else "test-api-key"
DATASET = sys.argv[4] if len(sys.argv) > 4 else "cmdemodataset0000000000001"
PATH = "/api/v3/feedbackRecords"

REQUIRED = ["tenant_id", "source_type", "field_id", "field_type", "submission_id"]
VALUE_MEMBER = {
    "text": "value_text",
    "categorical": "value_text",
    "nps": "value_number",
    "csat": "value_number",
    "ces": "value_number",
    "rating": "value_number",
    "number": "value_number",
    "boolean": "value_boolean",
    "date": "value_date",
}
ALLOWED = set(REQUIRED) | {
    "source_id", "source_name", "field_label", "field_group_id", "field_group_label", "language",
    "user_id", "collected_at", "value_text", "value_id", "value_number", "value_boolean",
    "value_date", "metadata",
}
seen = set()


class Handler(BaseHTTPRequestHandler):
    def reply(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def authorized(self):
        if self.headers.get("x-api-key") != API_KEY:
            self.reply(401, {"title": "Unauthorized", "detail": "invalid API key"})
            return False
        return True

    def do_GET(self):
        url = urlparse(self.path)
        if url.path != PATH or not self.authorized():
            return None if url.path == PATH else self.reply(404, {"title": "Not found"})
        if parse_qs(url.query).get("tenant_id") != [DATASET]:
            return self.reply(403, {"title": "Forbidden"})
        self.reply(200, {"data": [], "nextCursor": None})

    def do_POST(self):
        if urlparse(self.path).path != PATH:
            return self.reply(404, {"title": "Not found"})
        if not self.authorized():
            return None
        record = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")

        problems = [f"missing {k}" for k in REQUIRED if not record.get(k)]
        problems += [f"unknown member {k}" for k in record if k not in ALLOWED]
        member = VALUE_MEMBER.get(record.get("field_type"))
        if member is None:
            problems.append(f"bad field_type {record.get('field_type')!r}")
        elif record.get(member) is None:
            problems.append(f"{record.get('field_type')} needs {member}")
        if problems:
            print("REJECTED", problems, record, flush=True)
            return self.reply(400, {"title": "Bad Request", "detail": "; ".join(problems)})
        if record["tenant_id"] != DATASET:
            return self.reply(403, {"title": "Forbidden"})

        key = (record["tenant_id"], record["submission_id"], record["field_id"])
        if key in seen:
            return self.reply(409, {"title": "Conflict", "detail": "duplicate record"})
        seen.add(key)
        with open(OUT, "a") as f:
            f.write(json.dumps(record) + "\n")
        print("ACCEPTED", json.dumps(record), flush=True)
        self.reply(201, {"id": f"rec{len(seen)}", **record})

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    open(OUT, "w").close()
    print(f"mock Formbricks on :{PORT}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
