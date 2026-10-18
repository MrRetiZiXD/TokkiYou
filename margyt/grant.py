"""Give somebody a badge.

    python3 -m margyt --badge supporter 7551880794956989495 6845853735340753926

The badges live in `badges.json` in the repository and the app re-reads that
file every five minutes, so a badge granted here is on the phone of everybody
running the mod within five minutes and without anybody updating anything.

This edits the file, commits it and pushes it. `--no-push` stops at the edit.
"""

from __future__ import annotations

import json
import os
import subprocess
from collections import OrderedDict
from typing import List


def grant(root: str, badge_id: str, uids: List[str], push: bool = True) -> str:
    path = os.path.join(root, "badges.json")
    with open(path, encoding="utf-8") as handle:
        data = json.load(handle, object_pairs_hook=OrderedDict)

    badges = data.get("badges", [])
    for badge in badges:
        if badge.get("id") == badge_id:
            break
    else:
        known = ", ".join(b.get("id", "?") for b in badges)
        raise SystemExit("no badge called %r. There is: %s" % (badge_id, known))

    users = badge.setdefault("users", [])
    added = [uid for uid in uids if uid not in users]
    already = [uid for uid in uids if uid in users]
    users.extend(added)

    if not added:
        return "nothing to do: %s already has %s" % (", ".join(already), badge_id)

    with open(path, "w", encoding="utf-8") as handle:
        handle.write(json.dumps(data, indent=2, ensure_ascii=False) + "\n")

    said = "%s -> %s" % (badge_id, ", ".join(added))
    if already:
        said += " (%s already had it)" % ", ".join(already)
    if not push:
        return said + "\nbadges.json is edited but not pushed"

    message = "Give %s the %s badge" % (", ".join(added), badge_id)
    for command in (["git", "add", "badges.json"],
                    ["git", "commit", "-m", message],
                    ["git", "push", "origin", "HEAD"]):
        result = subprocess.run(command, cwd=root, capture_output=True, text=True)
        if result.returncode != 0:
            return said + "\npushing failed at `%s`:\n%s" % (
                " ".join(command), result.stderr.strip())
    return said + "\npushed -- everyone's phone has it within five minutes"
