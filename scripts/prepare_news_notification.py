#!/usr/bin/env python3
import json
import sys
from pathlib import Path


current_path = Path(sys.argv[1])
previous_path = Path(sys.argv[2])
message_path = Path(sys.argv[3])
output_path = Path(sys.argv[4])

with current_path.open(encoding="utf-8") as file:
    current = json.load(file)
try:
    with previous_path.open(encoding="utf-8") as file:
        previous = json.load(file)
except (FileNotFoundError, json.JSONDecodeError):
    previous = {"items": []}

previous_ids = {item.get("id") for item in previous.get("items", [])}
new_items = [item for item in current.get("items", []) if item.get("id") not in previous_ids]

# A first commit or a merge can introduce several items at once. Notifying the newest keeps
# the workflow running instead of failing the whole job; the caller decides whether the
# ambiguity matters.
if len(new_items) > 1:
    print(
        f"{len(new_items)} news items were added at once; notifying the newest only.",
        file=sys.stderr,
    )
new_items.sort(key=lambda item: item.get("publishedAt", ""), reverse=True)

should_send = bool(new_items and new_items[0].get("notify", True))
with output_path.open("a", encoding="utf-8") as output:
    output.write(f"should_send={'true' if should_send else 'false'}\n")
if should_send:
    message_path.write_text(json.dumps(new_items[0], ensure_ascii=False), encoding="utf-8")
    print(f"{new_items[0].get('title', '')} {new_items[0].get('summary', '')}".strip())
