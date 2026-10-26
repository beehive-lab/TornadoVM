#!/usr/bin/env python3

#
# Copyright (c) 2013-2025, APT Group, Department of Computer Science,
# The University of Manchester.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""
Detect a JDK 21 and a JDK 25 on this machine and build every release SDK for
the current platform with them.

This is a thin wrapper around scripts/build-release-sdks.py, which owns the
per-platform build matrix (macOS: opencl, metal; Linux x64: opencl, cuda, full;
Linux aarch64: cuda; Windows: opencl, cuda), the worktree checkout and the
jdk21/jdk22plus filename stamping. This script only finds the two JDK homes and
passes them as --jdk21-home / --jdk22plus-home, so it works with any vendor and
install location rather than only sdkman Temurin (which is all the release
script looks for), and on Windows without typing the paths.

JDK homes are searched, in order of preference:
  1. JDK21_HOME / JDK25_HOME (or JDK22PLUS_HOME) environment variables
  2. --jdk21-home / --jdk22plus-home given to this script (used as-is)
  3. JAVA_HOME and the `java` on PATH
  4. sdkman (~/.sdkman/candidates/java), /usr/libexec/java_home on macOS
  5. Common install roots: /opt/jdks, /opt/jenkins/jdks, /opt/java, /usr/lib/jvm,
     /Library/Java/JavaVirtualMachines, ~/Library/Java/JavaVirtualMachines,
     C:\\jdks, C:\\Program Files\\{Java,Eclipse Adoptium,Microsoft,Zulu,...}

A candidate counts only if it is a full JDK (has bin/javac) whose `release` file
reports the wanted major. Among several, Temurin is preferred (matching the
release script's sdkman lookup), then the newest patch.

Usage (from the TornadoVM repository root):
  python3 scripts/build-platform-sdks.py                 # version from pom.xml, e.g. v8.0.0
  python3 scripts/build-platform-sdks.py --version v8.0.0 --output-dir /opt/release-archives
  python3 scripts/build-platform-sdks.py --detect-only   # just print the JDKs it would use
  python3 scripts/build-platform-sdks.py --draft-release-test true   # tag not created yet: build origin/master

Any option not listed here (e.g. --skip-windows-executables) is passed through
to build-release-sdks.py.
"""

import argparse
import glob
import os
import platform
import re
import shutil
import subprocess
import sys
from pathlib import Path

RESET  = "\033[0m"
GREEN  = "\033[92m"
YELLOW = "\033[93m"
CYAN   = "\033[96m"
RED    = "\033[91m"


def info(msg):  print(f"{CYAN}[INFO]{RESET}  {msg}", flush=True)
def ok(msg):    print(f"{GREEN}[OK]{RESET}    {msg}", flush=True)
def warn(msg):  print(f"{YELLOW}[WARN]{RESET}  {msg}", flush=True)
def error(msg): print(f"{RED}[ERROR]{RESET} {msg}", file=sys.stderr, flush=True)


IS_WINDOWS = platform.system() == "Windows"
IS_MACOS = platform.system() == "Darwin"

# Wanted JDK major per release-script profile. jdk22plus accepts any JDK >= 22 and
# produces an identical artifact; 25 is the pinned build JDK the CI workflows use.
PROFILES = {
    "jdk21": 21,
    "jdk22plus": 25,
}
ENV_VARS = {
    "jdk21": ["JDK21_HOME"],
    "jdk22plus": ["JDK25_HOME", "JDK22PLUS_HOME"],
}


# ---------------------------------------------------------------------------
# JDK discovery
# ---------------------------------------------------------------------------

def _read_release(home):
    """Return the key/value pairs of <home>/release, or {} if it is missing."""
    props = {}
    try:
        with open(os.path.join(home, "release"), encoding="utf-8", errors="replace") as f:
            for line in f:
                key, sep, value = line.partition("=")
                if sep:
                    props[key.strip()] = value.strip().strip('"')
    except OSError:
        pass
    return props


def _version_tuple(version):
    """'21.0.3' / '25' / '27-ea' → (21, 0, 3) / (25,) / (27,) for sorting."""
    return tuple(int(n) for n in re.findall(r"\d+", version.split("-")[0].split("+")[0]))


def _is_jdk(home):
    javac = "javac.exe" if IS_WINDOWS else "javac"
    return os.path.isfile(os.path.join(home, "bin", javac))


def _describe(home):
    """Return (major, version, implementor) for a JDK home, or None if it is not a JDK."""
    if not _is_jdk(home):
        return None
    props = _read_release(home)
    version = props.get("JAVA_VERSION", "")
    numbers = _version_tuple(version) if version else ()
    if not numbers:
        return None
    return numbers[0], version, props.get("IMPLEMENTOR", "")


def _normalise(path):
    """Map a JDK path (a home, a macOS bundle, or a bin/java) to its JAVA_HOME."""
    path = os.path.realpath(path)
    if os.path.isfile(path):  # .../bin/java
        path = os.path.dirname(os.path.dirname(path))
    bundle_home = os.path.join(path, "Contents", "Home")
    if os.path.isdir(bundle_home):
        path = bundle_home
    return path


def _search_roots():
    home = Path.home()
    roots = [
        home / ".sdkman" / "candidates" / "java",
        home / ".jdks",  # IntelliJ downloads
        home / ".gradle" / "jdks",
        Path("/opt/jdks"),
        Path("/opt/jenkins/jdks"),
        Path("/opt/java"),
        Path("/usr/lib/jvm"),
        Path("/usr/java"),
        Path("/Library/Java/JavaVirtualMachines"),
        home / "Library" / "Java" / "JavaVirtualMachines",
    ]
    if IS_WINDOWS:
        roots.append(Path("C:/jdks"))
        for env in ("ProgramFiles", "ProgramW6432", "ProgramFiles(x86)"):
            base = os.environ.get(env)
            if base:
                for vendor in ("Java", "Eclipse Adoptium", "Microsoft", "Zulu", "Amazon Corretto",
                               "BellSoft", "OpenJDK", "GraalVM"):
                    roots.append(Path(base) / vendor)
    return [r for r in roots if r.is_dir()]


def _candidates():
    """Yield every path that might be a JDK home, most preferred source first."""
    for env in ("JAVA_HOME",):
        if os.environ.get(env):
            yield os.environ[env]
    java_on_path = shutil.which("java")
    if java_on_path:
        yield java_on_path
    if IS_MACOS and os.path.exists("/usr/libexec/java_home"):
        for major in PROFILES.values():
            try:
                out = subprocess.run(["/usr/libexec/java_home", "-v", str(major)],
                                     capture_output=True, text=True, timeout=10)
                if out.returncode == 0 and out.stdout.strip():
                    yield out.stdout.strip()
            except (OSError, subprocess.SubprocessError):
                pass
    for root in _search_roots():
        # One level (sdkman, /usr/lib/jvm) and two levels (/opt/jenkins/jdks/graal-23.1.0/jdk-21.0.3).
        for entry in sorted(glob.glob(str(root / "*"))) + sorted(glob.glob(str(root / "*" / "*"))):
            if os.path.isdir(entry) and os.path.basename(entry) != "current":
                yield entry


def discover_jdks():
    """Return {major: [(version, implementor, home), ...]} for every JDK found."""
    found = {}
    seen = set()
    for raw in _candidates():
        home = _normalise(raw)
        if home in seen:
            continue
        seen.add(home)
        described = _describe(home)
        if described:
            major, version, implementor = described
            found.setdefault(major, []).append((version, implementor, home))
    return found


def _rank(entry):
    version, implementor, home = entry
    temurin = "adoptium" in implementor.lower() or home.endswith("-tem") or "temurin" in home.lower()
    early_access = "ea" in version.lower() or ".ea." in home
    return (not early_access, temurin, _version_tuple(version))


def pick_jdk(profile, major, found, override):
    """Return the JDK home to build *profile* with, or None."""
    for env in ENV_VARS[profile]:
        value = os.environ.get(env)
        if value:
            override = override or value
            info(f"{profile}: taking {env}={value}")
            break
    if override:
        home = _normalise(override)
        described = _describe(home)
        if not described:
            error(f"{profile}: {override} is not a JDK (no bin/javac or no 'release' file).")
            return None
        if described[0] != major and not (profile == "jdk22plus" and described[0] >= 22):
            error(f"{profile}: {override} is JDK {described[1]}, but JDK {major} is required.")
            return None
        return home

    choices = sorted(found.get(major, []), key=_rank, reverse=True)
    if not choices:
        return None
    version, implementor, home = choices[0]
    return home


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def pom_version():
    try:
        with open("pom.xml", encoding="utf-8") as f:
            match = re.search(r"<artifactId>tornado</artifactId>\s*<version>([^<]+)</version>", f.read())
        return f"v{match.group(1).strip()}" if match else None
    except OSError:
        return None


def parse_args():
    parser = argparse.ArgumentParser(
        description="Detect JDK 21 and JDK 25 and build every TornadoVM SDK for this platform.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--version", default=None, metavar="VERSION",
                        help="Release tag to build (default: v<version> from pom.xml).")
    parser.add_argument("--output-dir", default="release-sdks", metavar="DIR",
                        help="Root directory for the archives (default: release-sdks/).")
    parser.add_argument("--jdk21-home", default=None, metavar="PATH",
                        help="Use this JDK 21 instead of detecting one.")
    parser.add_argument("--jdk22plus-home", "--jdk25-home", dest="jdk22plus_home", default=None, metavar="PATH",
                        help="Use this JDK (>= 22, normally 25) instead of detecting one.")
    parser.add_argument("--detect-only", action="store_true",
                        help="Print the detected JDKs and the command, then exit without building.")
    return parser.parse_known_args()


def main():
    args, passthrough = parse_args()

    if not os.path.isfile(os.path.join("bin", "compile")):
        error("bin/compile not found. Run this script from the TornadoVM repository root.")
        sys.exit(1)

    version = args.version or pom_version()
    if not version:
        error("Could not read the version from pom.xml; pass --version vX.Y.Z.")
        sys.exit(1)

    info("Searching for JDKs ...")
    found = discover_jdks()
    for major in sorted(found):
        for v, implementor, home in sorted(found[major], key=_rank, reverse=True):
            print(f"          JDK {major:<3} {v:<14} {implementor or '?':<28} {home}")

    homes = {}
    overrides = {"jdk21": args.jdk21_home, "jdk22plus": args.jdk22plus_home}
    for profile, major in PROFILES.items():
        explicit = overrides[profile] or any(os.environ.get(env) for env in ENV_VARS[profile])
        home = pick_jdk(profile, major, found, overrides[profile])
        if not home and explicit:
            sys.exit(1)  # a JDK the user named is wrong: stop rather than build a partial set
        if home:
            ok(f"{profile:<9} → {home}")
            homes[profile] = home
        else:
            warn(f"{profile:<9} → no JDK {major} found; its SDKs will be skipped. "
                 f"Set {ENV_VARS[profile][0]} or pass --{profile}-home.")

    if not homes:
        error("Neither a JDK 21 nor a JDK 25 was found. Nothing to build.")
        sys.exit(1)

    script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "build-release-sdks.py")
    cmd = [sys.executable, script, "--version", version, "--output-dir", args.output_dir]
    for profile, home in homes.items():
        cmd += [f"--{profile}-home", home]
    cmd += passthrough

    info("Running: " + " ".join(f'"{c}"' if " " in c else c for c in cmd))
    if args.detect_only:
        return 0
    return subprocess.call(cmd)


if __name__ == "__main__":
    sys.exit(main())
