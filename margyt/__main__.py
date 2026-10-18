"""TokkiYou's command line.

    python3 -m margyt path/to/tiktok.apk
    python3 -m margyt --badge supporter 7551880794956989495
"""

from __future__ import annotations

import argparse
import os
import sys

from .build import Build
from .grant import grant
from .toolchain import Toolchain

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        prog="tokkiyou",
        description="Build TokkiYou from an official TikTok apk.",
    )
    parser.add_argument("apk", nargs="?",
                        help="a universal TikTok apk (type APK, not BUNDLE)")
    parser.add_argument("--badge", metavar="ID",
                        help="give accounts this badge from badges.json instead of "
                             "building: the ids follow, and the file is pushed")
    parser.add_argument("uids", nargs="*", metavar="UID",
                        help="the TikTok account ids to give it to")
    parser.add_argument("--no-push", action="store_true",
                        help="with --badge: edit badges.json but do not push it")
    parser.add_argument("-o", "--out", default=os.path.join(ROOT, "build", "tokkiyou.apk"),
                        help="where the finished apk goes")
    parser.add_argument("--work", default=os.path.join(ROOT, "work"),
                        help="scratch space for the dex files being rewritten")
    parser.add_argument("--tools", default=os.path.join(ROOT, "tools"),
                        help="where the downloaded tools are kept")
    parser.add_argument("--test", action="store_true",
                        help="a build for the people who paid for it: their account id "
                             "faintly on screen, and the mod's settings closed to "
                             "anyone without the supporter badge")
    parser.add_argument("--keystore", help="sign with this keystore instead of a debug key")
    parser.add_argument("--keystore-alias", default="androiddebugkey",
                        help="key alias (default: androiddebugkey)")
    parser.add_argument("--keystore-password",
                        default=os.environ.get("MARGYT_KEYSTORE_PASSWORD", "android"),
                        help="store password (default is Android's debug-key password; "
                             "or set MARGYT_KEYSTORE_PASSWORD)")
    parser.add_argument("--key-password",
                        default=os.environ.get("MARGYT_KEY_PASSWORD", "android"),
                        help="key password (default is Android's debug-key password; "
                             "or set MARGYT_KEY_PASSWORD)")
    parser.add_argument("--accent", metavar="RRGGBB",
                        help="bake this colour in where TikTok's pink is a picture rather "
                             "than code: vector icons, colour resources, selectors")
    args = parser.parse_args(argv)

    if args.badge:
        # the apk is positional and greedy, so with --badge the first account
        # id lands in it -- it is an account id, not an apk, and belongs here
        uids = ([args.apk] if args.apk else []) + args.uids
        if not uids:
            parser.error("--badge wants at least one account id after it")
        print(grant(ROOT, args.badge, uids, push=not args.no_push))
        return 0

    if not args.apk:
        parser.error("a TikTok apk to build from, or --badge to hand one out")
    if not os.path.exists(args.apk):
        parser.error("no apk at %s" % args.apk)

    accent = None
    if args.accent:
        try:
            accent = 0xFF000000 | int(args.accent.lstrip("#"), 16)
        except ValueError:
            parser.error("--accent wants six hex digits, like 8DD1B0")

    tools = Toolchain(args.tools)
    build = Build(args.apk, args.out, ROOT, tools, args.work, args.keystore, accent,
                  args.keystore_alias, args.keystore_password, args.key_password,
                  test=args.test)
    try:
        build.run()
    except Exception as error:  # a build failure is a message, not a traceback
        print("\n\033[1;31mBuild failed:\033[0m %s" % error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
