"""The four tools the build needs, fetched once and kept in `tools/`.

Every URL here is pinned to a version and points at Google's Maven, Maven
Central or a release asset -- no "latest" lookups through an API that rate
limits, and no surprise when a tool changes under the build.
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from typing import Optional

# apktool's cli jar is used for one thing only: it carries smali and baksmali,
# assembler and disassembler, in a single jar. The apktool part -- decoding and
# rebuilding resources -- is exactly what this build does not do.
SMALI_JAR = (
    "https://repo1.maven.org/maven2/org/apktool/apktool-cli/3.0.3/apktool-cli-3.0.3.jar"
)
R8_JAR = "https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.7.18/r8-8.7.18.jar"
PLATFORM_ZIP = "https://dl.google.com/android/repository/platform-35_r02.zip"
PLATFORM_MEMBER = "android-35/android.jar"
SIGNER_JAR = (
    "https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/"
    "uber-apk-signer-1.3.0.jar"
)


# The deflate the patcher app repeats on a phone: the same code, so the same
# bytes. See tokkiyou/java/Deflate.java.
JZLIB_JAR = "https://repo1.maven.org/maven2/com/jcraft/jzlib/1.1.3/jzlib-1.1.3.jar"


class Toolchain:
    def __init__(self, directory: str, quiet: bool = False):
        self.directory = directory
        self.quiet = quiet
        os.makedirs(directory, exist_ok=True)

    def _say(self, message: str) -> None:
        if not self.quiet:
            print(message)

    def _fetch(self, url: str, name: str) -> str:
        path = os.path.join(self.directory, name)
        if os.path.exists(path):
            return path
        self._say("  fetching %s" % name)
        temporary = path + ".part"
        with urllib.request.urlopen(url) as response, open(temporary, "wb") as out:
            shutil.copyfileobj(response, out)
        os.replace(temporary, path)
        return path

    @property
    def smali(self) -> str:
        return self._fetch(SMALI_JAR, "smali.jar")

    @property
    def r8(self) -> str:
        return self._fetch(R8_JAR, "r8.jar")

    @property
    def signer(self) -> str:
        return self._fetch(SIGNER_JAR, "uber-apk-signer.jar")

    @property
    def android_jar(self) -> str:
        path = os.path.join(self.directory, "android.jar")
        if os.path.exists(path):
            return path
        archive = self._fetch(PLATFORM_ZIP, "platform.zip")
        self._say("  unpacking android.jar")
        with zipfile.ZipFile(archive) as zf, open(path, "wb") as out:
            shutil.copyfileobj(zf.open(PLATFORM_MEMBER), out)
        os.remove(archive)
        return path

    @property
    def jzlib(self) -> str:
        return self._fetch(JZLIB_JAR, "jzlib.jar")

    def deflate_many(self, blobs: list) -> list:
        """Deflate each of `blobs` the way the patcher app will, in one go.

        One start of a JVM for all of them: there are a couple of hundred, and
        a start each would be most of a minute.
        """
        if not blobs:
            return []
        helper = os.path.join(self.directory, "deflate")
        source = os.path.join(os.path.dirname(os.path.abspath(__file__)), "java", "Deflate.java")
        stamp = os.path.join(helper, "Deflate.class")
        if not os.path.exists(stamp) or os.path.getmtime(stamp) < os.path.getmtime(source):
            os.makedirs(helper, exist_ok=True)
            _run(["javac", "-nowarn", "-cp", self.jzlib, "-d", helper, source])
        with tempfile.TemporaryDirectory(prefix="tokkiyou-deflate-") as room:
            for at, blob in enumerate(blobs):
                with open(os.path.join(room, "%d.in" % at), "wb") as handle:
                    handle.write(blob)
            _run([_java(), "-Xmx2g", "-cp", helper + os.pathsep + self.jzlib, "Deflate", room])
            out = []
            for at in range(len(blobs)):
                with open(os.path.join(room, "%d.out" % at), "rb") as handle:
                    out.append(handle.read())
            return out

    # ------------------------------------------------------------ using them

    def framework_constant(self, field: str) -> int:
        """Read a constant out of android.jar rather than writing it down.

        Framework resource ids never change, but writing one into the source
        makes it a number nobody can check. javap can just read it.
        """
        result = subprocess.run(
            ["javap", "-cp", self.android_jar, "-constants", "android.R$style"],
            capture_output=True,
            text=True,
        )
        for line in result.stdout.splitlines():
            if (" " + field + " = ") in line:
                return int(line.rsplit("=", 1)[1].strip().rstrip(";"))
        raise RuntimeError("android.jar has no android.R.style.%s" % field)

    def check_attribute_ids(self, ids: dict) -> None:
        """Check the framework attribute ids against android.jar.

        Every id the manifest is written with is a public one that has not
        changed since the day it shipped -- but a table of bare numbers is a
        table nobody can check, and one wrong digit writes an attribute that
        means something else entirely.
        """
        result = subprocess.run(
            ["javap", "-cp", self.android_jar, "-constants", "android.R$attr"],
            capture_output=True,
            text=True,
        )
        known = {}
        for line in result.stdout.splitlines():
            if " = " not in line or not line.strip().endswith(";"):
                continue
            name = line.split()[-3]
            known[name] = int(line.rsplit("=", 1)[1].strip().rstrip(";"))
        for name, value in ids.items():
            if name in known and known[name] != value:
                raise RuntimeError(
                    "android:%s is 0x%08x in android.jar, not 0x%08x"
                    % (name, known[name], value)
                )

    def compile_dex(self, sources_dir: str, out_dir: str, min_api: int,
                    stubs_dir: Optional[str] = None) -> str:
        """javac then d8: the mod's own classes, as one dex.

        `stubs_dir` holds TikTok's own classes in outline, so the mod can be
        written against the ones it is rewritten into. They are compiled to
        build against and then handed to d8 as classpath rather than input:
        the real ones are in the apk, and a second copy under the same name
        would be a different class as far as the runtime is concerned.
        """
        classes = os.path.join(out_dir, "classes")
        stubs = os.path.join(out_dir, "stubs")
        dex = os.path.join(out_dir, "dex")
        for path in (classes, stubs, dex):
            shutil.rmtree(path, ignore_errors=True)
            os.makedirs(path)

        classpath = self.android_jar
        if stubs_dir:
            stub_sources = _java_under(stubs_dir)
            if stub_sources:
                _run(["javac", "-nowarn", "-Xlint:-options", "-encoding", "UTF-8",
                      "-cp", self.android_jar, "--release", "17", "-d", stubs]
                     + stub_sources)
                classpath = self.android_jar + os.pathsep + stubs

        sources = _java_under(sources_dir)
        if not sources:
            raise RuntimeError("no java sources under %s" % sources_dir)

        _run(
            ["javac", "-nowarn", "-Xlint:-options", "-encoding", "UTF-8",
             "-cp", classpath, "--release", "17", "-d", classes] + sources
        )
        class_files = _classes_under(classes)
        # One archive avoids Windows' command-line limit as the mod grows.
        # Only mod classes go in; host stubs remain on D8's classpath.
        program = os.path.join(out_dir, "mod-classes.jar")
        with zipfile.ZipFile(program, "w", zipfile.ZIP_DEFLATED) as archive:
            for path in class_files:
                archive.write(path, os.path.relpath(path, classes).replace(os.sep, "/"))
        command = [_java(), "-cp", self.r8, "com.android.tools.r8.D8", "--release",
                   "--min-api", str(min_api), "--lib", self.android_jar]
        if stubs_dir and _classes_under(stubs):
            command += ["--classpath", stubs]
        _run(command + ["--output", dex, program])
        return os.path.join(dex, "classes.dex")

    def sign(self, apk_path: str, key: Optional[str] = None,
             alias: str = "androiddebugkey", store_password: str = "android",
             key_password: str = "android") -> None:
        """Align and sign in place, handling PKCS12 debug stores as well as JKS."""
        # its zipalign pass runs before signing, which is where alignment has
        # to be settled: the signature block goes in after every entry is placed
        command = [_java(), "-jar", self.signer, "-a", apk_path, "--allowResign", "--overwrite"]
        if not key:
            _run(command)
            return

        # uber-apk-signer 1.3 requires an alias for custom stores and its
        # embedded apksigner reads JKS. Current Android debug.keystore files
        # are commonly PKCS12, so detect the source format and convert to a
        # temporary JKS instead of silently leaving an unsigned APK behind.
        keytool = _keytool()
        probe = subprocess.run(
            [keytool, "-list", "-keystore", key, "-storepass", store_password],
            capture_output=True, text=True,
        )
        details = probe.stdout + probe.stderr
        if probe.returncode != 0:
            raise RuntimeError("keytool could not read the supplied keystore: " + details.strip())
        match = re.search(r"Keystore type:\s*([A-Za-z0-9_-]+)", details)
        if not match:
            raise RuntimeError("keytool did not report the keystore type")
        store_type = match.group(1).upper()

        with tempfile.TemporaryDirectory(prefix="tokkiyou-sign-") as temp:
            signer_key = key
            if store_type != "JKS":
                signer_key = os.path.join(temp, "signing.jks")
                _run([
                    keytool, "-importkeystore", "-srckeystore", key,
                    "-srcstoretype", store_type, "-srcstorepass", store_password,
                    "-srcalias", alias, "-destkeystore", signer_key,
                    "-deststoretype", "JKS", "-deststorepass", store_password,
                    "-destalias", alias, "-destkeypass", key_password, "-noprompt",
                ])
            command += [
                "--ks", signer_key, "--ksAlias", alias,
                "--ksPass", store_password, "--ksKeyPass", key_password,
            ]
            _run(command)


def _java_under(root: str) -> list:
    out = []
    for dirpath, _dirs, files in os.walk(root):
        out += [os.path.join(dirpath, f) for f in files if f.endswith(".java")]
    return sorted(out)


def _classes_under(root: str) -> list:
    out = []
    for dirpath, _dirs, files in os.walk(root):
        out += [os.path.join(dirpath, f) for f in files if f.endswith(".class")]
    return sorted(out)


def _java() -> str:
    """The runtime next to javac, rather than an older java earlier in PATH."""
    javac = shutil.which("javac")
    if javac:
        name = "java.exe" if os.name == "nt" else "java"
        beside = os.path.join(os.path.dirname(os.path.abspath(javac)), name)
        if os.path.isfile(beside):
            return beside
    return shutil.which("java") or "java"


def _keytool() -> str:
    """Find keytool beside the JDK used by the build."""
    javac = shutil.which("javac")
    if javac:
        name = "keytool.exe" if os.name == "nt" else "keytool"
        beside = os.path.join(os.path.dirname(os.path.abspath(javac)), name)
        if os.path.isfile(beside):
            return beside
    found = shutil.which("keytool")
    if not found:
        raise RuntimeError("keytool is required to sign with the supplied keystore")
    return found


def _run(command: list) -> None:
    environment = dict(os.environ)
    # the wrapper's own JVM options print a banner into every tool's output
    environment.pop("JAVA_TOOL_OPTIONS", None)
    result = subprocess.run(command, capture_output=True, text=True, env=environment)
    if result.returncode != 0:
        sys.stderr.write(result.stdout + result.stderr)
        raise RuntimeError("%s failed" % os.path.basename(command[0]))
