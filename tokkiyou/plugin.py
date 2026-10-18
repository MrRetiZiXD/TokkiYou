"""Pack a folder into a plugin the mod can install.

    python3 -m tokkiyou.plugin examples/hello

The folder holds a `manifest.json`, the plugin's java under `java/`, and an
optional `icon.png`. Out comes an `.mtp` -- a zip with the manifest, the icon
and one `classes.dex`.

The plugin is compiled against `android.jar` and against the mod itself, which
is compiled here from `inject/java` and then left out of the dex: those classes
are already in the app, and a second copy in the plugin's own dex would be a
different class with the same name -- the plugin would be handed a
`PluginContext` it does not recognise.

The toolchain is the one the build downloads, so the first run of either fetches
it and the other finds it there.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

from .toolchain import Toolchain

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# PluginContext calls into the mod -- Plugins.addRow, Popup.show, Screen.offer,
# Net.bytes -- and those classes are in the patched apk, not in a plugin. They
# are compiled here so the plugin has something to be compiled against, and
# never reach its dex: the plugin is dexed with them as a classpath.
MOD_SOURCES = os.path.join(ROOT, "inject", "java")
TIKTOK_STUBS = os.path.join(ROOT, "inject", "stubs")

REQUIRED = ("id", "name", "version", "author", "entry")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        prog="tokkiyou.plugin",
        description="Pack a folder into an .mtp tokkiyou plugin.",
    )
    parser.add_argument("folder", help="the plugin's folder: manifest.json, java/, icon.png")
    parser.add_argument("-o", "--out", help="where the .mtp goes")
    parser.add_argument("--tools", default=os.path.join(ROOT, "tools"))
    args = parser.parse_args(argv)

    folder = os.path.abspath(args.folder)
    manifest_path = os.path.join(folder, "manifest.json")
    if not os.path.isfile(manifest_path):
        parser.error("no manifest.json in %s" % folder)

    with open(manifest_path, encoding="utf-8") as handle:
        manifest = json.load(handle)
    missing = [field for field in REQUIRED if not manifest.get(field)]
    if missing:
        parser.error("manifest.json has no %s" % ", ".join(missing))

    sources = os.path.join(folder, "java")
    if not os.path.isdir(sources):
        parser.error("no java/ in %s" % folder)

    out = args.out or os.path.join(folder, "%s.mtp" % manifest["id"])
    tools = Toolchain(args.tools)

    room = tempfile.mkdtemp(prefix="mtp-")
    try:
        api = os.path.join(room, "api")
        classes = os.path.join(room, "classes")
        dex = os.path.join(room, "dex")
        for path in (api, classes, dex):
            os.makedirs(path)

        # The api is two classes, and what they call is half the mod. That half
        # used to be written out again by hand as declarations for this to
        # compile against, and fell behind the first time the api grew -- after
        # which no plugin could be built at all. So the mod itself is compiled
        # here, the way the build compiles it, and the plugin is compiled
        # against that. None of it goes into the plugin's dex.
        print("==> Compiling the mod, for the plugin to be compiled against")
        stubs = os.path.join(room, "stubs")
        os.makedirs(stubs)
        _javac(tools.android_jar, None, stubs, _java_files(TIKTOK_STUBS))
        _javac(tools.android_jar, stubs, api, _java_files(MOD_SOURCES))

        print("==> Compiling %s" % manifest["name"])
        files = _java_files(sources)
        if not files:
            parser.error("no .java under %s" % sources)
        _javac(tools.android_jar, api, classes, files)

        entry = os.path.join(classes, *manifest["entry"].split(".")) + ".class"
        if not os.path.isfile(entry):
            parser.error("the manifest's entry, %s, is not among the compiled classes"
                         % manifest["entry"])

        print("==> One dex, without the api")
        compiled = []
        for dirpath, _dirs, names in os.walk(classes):
            compiled += [os.path.join(dirpath, n) for n in names if n.endswith(".class")]
        _run(["java", "-cp", tools.r8, "com.android.tools.r8.D8", "--release",
              "--min-api", "23", "--lib", tools.android_jar,
              "--classpath", api, "--output", dex] + compiled)

        print("==> Packing %s" % os.path.basename(out))
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
            zf.write(manifest_path, "manifest.json")
            zf.write(os.path.join(dex, "classes.dex"), "classes.dex")
            icon = os.path.join(folder, "icon.png")
            if os.path.isfile(icon):
                zf.write(icon, "icon.png")
            else:
                print("    no icon.png, so the list will draw a dot instead")
        print("    %s, %d bytes" % (out, os.path.getsize(out)))
        return 0
    finally:
        shutil.rmtree(room, ignore_errors=True)


def _java_files(root: str):
    out = []
    for dirpath, _dirs, names in os.walk(root):
        out += [os.path.join(dirpath, n) for n in names if n.endswith(".java")]
    return sorted(out)


def _javac(android_jar: str, extra_classpath, out_dir: str, files) -> None:
    classpath = android_jar if not extra_classpath else \
        android_jar + os.pathsep + extra_classpath
    # -proc:none: a plugin's folder is somebody else's, and compiling it must
    # not run anything that came with it
    _run(["javac", "-nowarn", "-Xlint:-options", "-proc:none", "-encoding", "UTF-8",
          "-cp", classpath, "--release", "17", "-d", out_dir] + list(files))


def _run(command: list) -> None:
    environment = dict(os.environ)
    environment.pop("JAVA_TOOL_OPTIONS", None)
    result = subprocess.run(command, capture_output=True, text=True, env=environment)
    if result.returncode != 0:
        sys.stderr.write(result.stdout + result.stderr)
        raise SystemExit("%s failed" % os.path.basename(command[0]))


if __name__ == "__main__":
    sys.exit(main())
