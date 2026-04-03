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
Build TornadoVM release SDKs for the current platform.

Given a base version (e.g. v4.0.0), the script checks out the corresponding
JDK-specific release tags (v4.0.0-jdk21, v4.0.0-jdk25) into temporary git
worktrees, builds the relevant SDKs in each worktree, collects the archives,
and cleans up — without touching the current working branch.

SDKs built per platform:
  - macOS   : opencl, metal  (JDK 21 and JDK 25, via sdkman Temurin)
  - Linux   : opencl, ptx, spirv, full  (JDK 21 and JDK 25, via sdkman Temurin)
  - Windows : opencl, ptx, spirv, full  (JDK 21 and JDK 25, --jdkXX-home required)

Usage:
  python3 scripts/build-release-sdks.py --version v4.0.0
  python3 scripts/build-release-sdks.py --version v4.0.0 --output-dir /path/to/output

  # Windows (both JDK paths required up front):
  python scripts\\build-release-sdks.py --version v4.0.0 ^
      --jdk21-home "C:\\Path\\To\\jdk-21" ^
      --jdk25-home "C:\\Path\\To\\jdk-25"

Must be run from the TornadoVM repository root.
"""

import argparse
import glob
import os
import platform
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


# ---------------------------------------------------------------------------
# ANSI helpers
# ---------------------------------------------------------------------------

RESET  = "\033[0m"
BOLD   = "\033[1m"
GREEN  = "\033[92m"
YELLOW = "\033[93m"
CYAN   = "\033[96m"
RED    = "\033[91m"


def _print(prefix_color, prefix, msg):
    print(f"{prefix_color}{prefix}{RESET} {msg}")

def info(msg):  _print(CYAN,   "[INFO]",  msg)
def ok(msg):    _print(GREEN,  "[OK]",    msg)
def warn(msg):  _print(YELLOW, "[WARN]",  msg)
def error(msg): _print(RED,    "[ERROR]", msg)

def section(title):
    width = 70
    print()
    print("=" * width)
    print(f"{BOLD}{title}{RESET}".center(width + len(BOLD) + len(RESET)))
    print("=" * width)


# ---------------------------------------------------------------------------
# Platform detection
# ---------------------------------------------------------------------------

def detect_platform():
    """Return one of 'macos', 'linux', 'windows'."""
    system = platform.system().lower()
    if system == "darwin":
        return "macos"
    elif system == "linux":
        return "linux"
    elif system == "windows":
        return "windows"
    else:
        error(f"Unsupported platform: {system}")
        sys.exit(1)


# ---------------------------------------------------------------------------
# JDK discovery
# ---------------------------------------------------------------------------

def _find_sdkman_temurin_jdk(major_version):
    """
    Locate the newest installed Temurin JDK for *major_version* under
    ~/.sdkman/candidates/java/.  Returns the full path to the JDK home.
    """
    sdkman_java_dir = Path.home() / ".sdkman" / "candidates" / "java"
    if not sdkman_java_dir.is_dir():
        error(
            f"sdkman candidates directory not found at {sdkman_java_dir}.\n"
            "  Install sdkman: https://sdkman.io  then:\n"
            f"    sdk install java <{major_version}.x.y>-tem"
        )
        sys.exit(1)

    candidates = [
        entry
        for entry in sdkman_java_dir.iterdir()
        if entry.is_dir()
        and entry.name.startswith(f"{major_version}.")
        and entry.name.endswith("-tem")
    ]

    if not candidates:
        error(
            f"No Temurin JDK {major_version} found in sdkman.\n"
            f"  Install with: sdk install java <{major_version}.x.y>-tem"
        )
        sys.exit(1)

    candidates.sort(key=lambda p: p.name, reverse=True)
    chosen = candidates[0]
    info(f"JDK {major_version}: using {chosen.name}  ({chosen})")
    return str(chosen)


def resolve_jdk_home(major_version, override):
    """
    Return the JAVA_HOME path for *major_version*.

    - If *override* is given (--jdk21-home / --jdk25-home), use it.
    - Otherwise on macOS/Linux look up sdkman.
    - On Windows an override is mandatory; exits with instructions if missing.
    """
    if override:
        override = str(override)
        if not os.path.isdir(override):
            error(f"Provided JDK path does not exist: {override}")
            sys.exit(1)
        info(f"JDK {major_version}: using override  ({override})")
        return override

    if detect_platform() == "windows":
        error(
            f"On Windows you must supply the JDK {major_version} path explicitly.\n"
            f"  Add:  --jdk{major_version}-home \"C:\\\\Path\\\\To\\\\jdk-{major_version}\""
        )
        sys.exit(1)

    return _find_sdkman_temurin_jdk(major_version)


# ---------------------------------------------------------------------------
# Build matrix
# ---------------------------------------------------------------------------

# Backends to build per platform.  Each entry becomes BACKEND=<value> in make.
# On macOS/Linux the Makefile target is:  make sdk BACKEND=<value>
# On Windows it is:  nmake /f Makefile.mak sdk BACKEND=<value>
# opencl+ptx+spirv is labelled "full" automatically by bin/compile.
BUILDS = {
    "macos": [
        "opencl",
        "metal",
    ],
    "linux": [
        "opencl",
        "ptx",
        "spirv",
        "opencl,ptx,spirv",
    ],
    "windows": [
        "opencl",
        "ptx",
        "spirv",
        "opencl,ptx,spirv",
    ],
}

# Map major JDK version → --jdk argument (used only on Windows where make is unavailable)
JDK_ARG = {21: "jdk21", 25: "jdk25"}


# ---------------------------------------------------------------------------
# Git tag / worktree helpers
# ---------------------------------------------------------------------------

def tag_exists(tag):
    """Return True if *tag* exists in the local or remote repository."""
    result = subprocess.run(
        ["git", "rev-parse", "--verify", f"refs/tags/{tag}"],
        capture_output=True,
    )
    return result.returncode == 0


def fetch_tag(tag):
    """Fetch *tag* from origin if it is not already present locally."""
    if tag_exists(tag):
        return
    info(f"Tag {tag} not found locally — fetching from origin...")
    subprocess.run(["git", "fetch", "origin", f"refs/tags/{tag}:refs/tags/{tag}"], check=True)


def add_worktree(tag, worktree_path):
    """Create a detached git worktree at *worktree_path* checked out to *tag*."""
    subprocess.run(
        ["git", "worktree", "add", "--detach", worktree_path, tag],
        check=True,
    )
    ok(f"Worktree created at {worktree_path}  (tag: {tag})")


def remove_worktree(worktree_path):
    """Remove the git worktree and delete its directory."""
    subprocess.run(
        ["git", "worktree", "remove", "--force", worktree_path],
        capture_output=True,
    )
    shutil.rmtree(worktree_path, ignore_errors=True)
    info(f"Worktree removed: {worktree_path}")


# ---------------------------------------------------------------------------
# Build execution
# ---------------------------------------------------------------------------

def clean_graal_jars(worktree_path):
    """
    Remove all files inside graalJars/ in *worktree_path* before a build.

    GraalVM JARs are version-specific and tied to the JDK being built.
    pull_graal_jars.py skips files that already exist, so stale JARs from a
    previous build (e.g. JDK 25 jars left when switching to JDK 21) would be
    silently reused and cause version mismatches.  Clearing the directory
    forces a fresh download for every build.
    """
    graal_jars_dir = os.path.join(worktree_path, "graalJars")
    if not os.path.isdir(graal_jars_dir):
        return
    removed = 0
    for entry in os.scandir(graal_jars_dir):
        if entry.is_file():
            os.remove(entry.path)
            removed += 1
    if removed:
        info(f"Removed {removed} stale file(s) from graalJars/")


def build_sdk(worktree_path, jdk_home, jdk_arg, backends, label):
    """
    Build a single SDK variant inside *worktree_path*.

    On macOS and Linux:  make sdk BACKEND=<backends>
    On Windows:          nmake /f Makefile.mak sdk BACKEND=<backends>
                         (the Makefile does not encode the JDK; we call
                          bin/compile directly so we can pass --jdk explicitly)

    The Makefile in each worktree already has the correct --jdk hardcoded for
    its tag, so we do not need to pass jdk_arg on macOS/Linux.
    """
    clean_graal_jars(worktree_path)

    env = os.environ.copy()
    env["JAVA_HOME"] = jdk_home
    # Prepend the build JDK's bin/ to PATH so that any script that runs `java`
    # (e.g. gen-tornado-argfile-template.py's is_graalvm() check) sees the
    # correct JDK rather than whatever is current in the shell.
    jdk_bin = os.path.join(jdk_home, "bin")
    env["PATH"] = jdk_bin + os.pathsep + env.get("PATH", "")

    if os.name == "nt":
        cmd = [
            "nmake", "/f", "Makefile.mak", "sdk", f"BACKEND={backends}",
        ]
    else:
        cmd = ["make", "sdk", f"BACKEND={backends}"]

    info(f"Building [{label}]  BACKEND={backends}  JAVA_HOME={jdk_home}")
    print(f"  Command: {' '.join(cmd)}")

    result = subprocess.run(cmd, env=env, cwd=worktree_path)
    if result.returncode != 0:
        error(f"Build FAILED for BACKEND={backends}")
        return False

    ok(f"Build succeeded: {label}")
    return True


# ---------------------------------------------------------------------------
# Archive collection
# ---------------------------------------------------------------------------

def _ensure_jdk_in_name(dest_path, jdk_arg):
    """
    Rename *dest_path* to include *jdk_arg* (e.g. 'jdk21') if it is not
    already present in the filename.

    The jdk25 branch encodes the JDK in its pom.xml version string
    (e.g. '4.0.0-jdk25'), so those archives are named correctly by Maven.
    The jdk21 branch uses a plain version ('4.0.0'), so its archives lack the
    JDK identifier.  This function inserts it right after the version number:

      tornadovm-4.0.0-opencl-mac-aarch64.tar.gz
        → tornadovm-4.0.0-jdk21-opencl-mac-aarch64.tar.gz
    """
    basename = os.path.basename(dest_path)
    if jdk_arg in basename:
        return dest_path

    new_basename = re.sub(
        r"^(tornadovm-[^-]+-)",
        lambda m: f"{m.group(1)}{jdk_arg}-",
        basename,
    )
    if new_basename == basename:
        return dest_path  # pattern didn't match; leave unchanged

    new_path = os.path.join(os.path.dirname(dest_path), new_basename)
    os.rename(dest_path, new_path)
    info(f"Renamed: {basename} → {new_basename}")
    return new_path


def collect_archives(worktree_path, output_dir, jdk_arg):
    """
    Move all .tar.gz and .zip archives from <worktree>/dist/ into *output_dir*,
    renaming them to include *jdk_arg* if the JDK identifier is not already
    present in the filename.
    Returns a list of destination paths.
    """
    dist_dir = os.path.join(worktree_path, "dist")
    collected = []
    for pattern in ("*.tar.gz", "*.zip"):
        for archive in glob.glob(os.path.join(dist_dir, pattern)):
            dest = os.path.join(output_dir, os.path.basename(archive))
            shutil.move(archive, dest)
            dest = _ensure_jdk_in_name(dest, jdk_arg)
            ok(f"Saved: {os.path.basename(dest)}")
            collected.append(dest)
    return collected


# ---------------------------------------------------------------------------
# SDK validation
# ---------------------------------------------------------------------------

def validate_sdk(archive_path, jdk_home):
    """
    Extract a .zip SDK archive to a temporary directory and run three smoke tests:

      1. tornado --devices      — confirms the runtime can enumerate devices
      2. tornado --version      — confirms the version string is readable
      3. java @tornado-argfile  — runs MatrixVectorRowMajor as a minimal kernel test

    The tornado-argfile.template shipped in the archive uses ${TORNADOVM_HOME}
    placeholders.  This function expands them against the extraction directory
    before invoking java.

    Returns True if all three checks pass, False otherwise.
    """
    basename = os.path.basename(archive_path)
    info(f"Validating: {basename}")

    with tempfile.TemporaryDirectory(prefix="tornadovm-validate-") as tmpdir:
        with zipfile.ZipFile(archive_path) as zf:
            zf.extractall(tmpdir)

        # The archive contains a single top-level tornadovm-* directory
        sdk_dirs = [
            os.path.join(tmpdir, e)
            for e in os.listdir(tmpdir)
            if e.startswith("tornadovm-") and os.path.isdir(os.path.join(tmpdir, e))
        ]
        if not sdk_dirs:
            warn(f"  No SDK directory found inside {basename} — skipping")
            return False
        tornado_home = sdk_dirs[0]

        # Expand tornado-argfile.template → tornado-argfile using the temp path
        template_path = os.path.join(tornado_home, "tornado-argfile.template")
        argfile_path  = os.path.join(tornado_home, "tornado-argfile")
        if not os.path.isfile(template_path):
            warn(f"  tornado-argfile.template not found in {basename} — skipping")
            return False
        with open(template_path) as f:
            content = f.read()
        with open(argfile_path, "w") as f:
            f.write(content.replace("${TORNADOVM_HOME}", tornado_home))

        # Locate the examples JAR (name includes version, e.g. tornado-examples-4.0.0.jar)
        examples_jars = glob.glob(
            os.path.join(tornado_home, "share", "java", "tornado", "tornado-examples-*.jar")
        )
        if not examples_jars:
            warn(f"  tornado-examples JAR not found in {basename} — skipping")
            return False
        examples_jar = examples_jars[0]

        env = os.environ.copy()
        env["JAVA_HOME"]      = jdk_home
        env["TORNADOVM_HOME"] = tornado_home
        env["PATH"]           = os.path.join(tornado_home, "bin") + os.pathsep + env.get("PATH", "")

        java_cmd       = os.path.join(jdk_home, "bin", "java")
        tornado_script = os.path.join(tornado_home, "bin", "tornado")

        # Make the tornado script executable (zip does not preserve permissions)
        if os.name != "nt":
            os.chmod(tornado_script, 0o755)

        checks = [
            (
                [tornado_script, "--devices"],
                "tornado --devices",
            ),
            (
                [tornado_script, "--version"],
                "tornado --version",
            ),
            (
                [java_cmd, f"@{argfile_path}", "-cp", examples_jar,
                 "uk.ac.manchester.tornado.examples.compute.MatrixVectorRowMajor"],
                "MatrixVectorRowMajor",
            ),
        ]

        passed = True
        for cmd, name in checks:
            result = subprocess.run(cmd, env=env, capture_output=True, text=True)
            if result.returncode != 0:
                error(f"  [{name}] FAILED")
                output = (result.stdout + result.stderr).strip()
                if output:
                    print(output[-1500:])
                passed = False
            else:
                ok(f"  [{name}] passed")

        return passed


# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------

def parse_args():
    parser = argparse.ArgumentParser(
        description="Build TornadoVM release SDKs for the current platform.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--version",
        required=True,
        metavar="VERSION",
        help=(
            "Base release version tag (e.g. v4.0.0).  "
            "The script checks out <version>-jdk21 and <version>-jdk25."
        ),
    )
    parser.add_argument(
        "--output-dir",
        default="release-sdks",
        metavar="DIR",
        help="Root directory where SDK archives are saved (default: release-sdks/)",
    )
    parser.add_argument(
        "--jdk21-home",
        metavar="PATH",
        default=None,
        help=(
            "Path to JDK 21 home.  "
            "Required on Windows; overrides sdkman auto-detection on macOS/Linux."
        ),
    )
    parser.add_argument(
        "--jdk25-home",
        metavar="PATH",
        default=None,
        help=(
            "Path to JDK 25 home.  "
            "Required on Windows; overrides sdkman auto-detection on macOS/Linux."
        ),
    )
    return parser.parse_args()


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    args = parse_args()

    # Must be run from the TornadoVM repo root
    if not os.path.isfile(os.path.join("bin", "compile")):
        error(
            "bin/compile not found.  "
            "Run this script from the TornadoVM repository root."
        )
        sys.exit(1)

    current_platform = detect_platform()
    arch = platform.machine().lower().replace("x86_64", "amd64")

    section(f"TornadoVM Release SDK Builder  {args.version}")
    info(f"Platform : {current_platform}-{arch}")
    info(f"Version  : {args.version}")

    output_dir = os.path.join(
        args.output_dir, args.version, f"{current_platform}-{arch}"
    )
    os.makedirs(output_dir, exist_ok=True)
    info(f"Output   : {os.path.abspath(output_dir)}")

    # Build the tag name and JDK home map for each JDK version
    jdk_configs = [
        (21, f"{args.version}-jdk21", args.jdk21_home),
        (25, f"{args.version}-jdk25", args.jdk25_home),
    ]

    # Validate tags and resolve JDK homes before starting any build
    validated = []
    for major, tag, jdk_override in jdk_configs:
        info(f"Checking tag {tag} ...")
        fetch_tag(tag)
        if not tag_exists(tag):
            warn(f"Tag {tag} not found — skipping JDK {major} builds.")
            continue
        jdk_home = resolve_jdk_home(major, jdk_override)
        validated.append((major, tag, jdk_home))

    if not validated:
        error("No valid tags found. Nothing to build.")
        sys.exit(1)

    backends_for_platform = BUILDS[current_platform]
    results = []  # list of (label, success)

    repo_root = os.getcwd()

    for jdk_major, tag, jdk_home in validated:
        jdk_arg = JDK_ARG[jdk_major]

        section(f"JDK {jdk_major}  (tag: {tag})")

        worktree_path = tempfile.mkdtemp(prefix=f"tornadovm-{tag}-")
        try:
            add_worktree(tag, worktree_path)

            for backends in backends_for_platform:
                # Label mirrors bin/compile naming: opencl,ptx,spirv → full
                if set(backends.split(",")) == {"opencl", "ptx", "spirv"}:
                    backend_label = "full"
                else:
                    backend_label = backends
                label = f"{tag}-{backend_label}"
                success = build_sdk(worktree_path, jdk_home, jdk_arg, backends, label)
                results.append((label, success))

                if success:
                    newly_collected = collect_archives(worktree_path, output_dir, jdk_arg)
                    if not newly_collected:
                        warn("No archives found in dist/ after build — check build output.")
        finally:
            # Always clean up the worktree, even if a build fails
            os.chdir(repo_root)
            remove_worktree(worktree_path)

    # ------------------------------------------------------------------
    # Validate every collected .zip archive
    # ------------------------------------------------------------------
    # Map jdk_arg string (e.g. 'jdk21') → jdk_home for archive name lookup
    jdk_home_by_arg = {JDK_ARG[major]: jdk_home for major, _tag, jdk_home in validated}

    section("Validating SDKs")
    val_results = []
    for archive in sorted(glob.glob(os.path.join(output_dir, "*.zip"))):
        basename = os.path.basename(archive)
        jdk_home = next(
            (home for arg, home in jdk_home_by_arg.items() if arg in basename),
            None,
        )
        if not jdk_home:
            warn(f"Cannot determine JDK for {basename} — skipping validation")
            continue
        success = validate_sdk(archive, jdk_home)
        val_results.append((basename, success))

    # ------------------------------------------------------------------
    # Summary
    # ------------------------------------------------------------------
    section("Build Summary")
    successes = [lbl for lbl, s in results if s]
    failures  = [lbl for lbl, s in results if not s]

    for lbl in successes:
        ok(f"  {lbl}")
    for lbl in failures:
        error(f"  {lbl}")

    section("Validation Summary")
    val_passed  = [n for n, s in val_results if s]
    val_failed  = [n for n, s in val_results if not s]

    for name in val_passed:
        ok(f"  {name}")
    for name in val_failed:
        error(f"  {name}")

    print()
    info(f"SDK archives saved to: {os.path.abspath(output_dir)}")

    if failures or val_failed:
        if failures:
            warn(f"{len(failures)} build(s) failed.")
        if val_failed:
            warn(f"{len(val_failed)} validation(s) failed.")
        sys.exit(1)
    else:
        ok(f"All {len(successes)} build(s) and {len(val_passed)} validation(s) passed.")


if __name__ == "__main__":
    main()
