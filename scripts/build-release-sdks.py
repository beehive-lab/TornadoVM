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

Given a base version (e.g. v4.0.0), the script checks out that ONE release tag
into a single temporary git worktree, builds the relevant SDKs for both JDK
build profiles (jdk21, jdk22plus) from that same worktree (--jdk21-home /
--jdk22plus-home, same branch — there are no more per-JDK divergent
branches/tags), collects the archives, and cleans up — without touching the
current working branch.

jdk21 and jdk22plus are the only release-facing Maven profiles (see
bin/compile's SDK_JDK_CONTRACT / pom.xml): jdk21 is --enable-preview and
pinned to exactly JDK 21; jdk22plus floors at JDK 22, carries no preview
features, and is built ONCE to run unmodified on every JDK from 22 upwards
(22, 23, 24, 25, 26, 27, ...). There is no per-major-JDK build past 21 — do
not add --jdk25-home/--jdk26-home/--jdk27-home back.

The committed pom.xml version in the tag carries no JDK suffix (e.g. "4.0.0",
not "4.0.0-jdk21"); each produced archive gets the correct profile token
stamped into its filename post-build (see _set_jdk_in_name), since otherwise
the two profiles' archives for a given backend would collide on the same
filename.

SDKs built per platform (jdk21 + jdk22plus profiles x backend, via sdkman
Temurin or an explicit --jdk21-home/--jdk22plus-home override):
  - macOS   : opencl, metal
  - Linux   : opencl, cuda, full
  - Windows : opencl, cuda   (--jdk21-home/--jdk22plus-home required)

"full" means opencl+cuda combined into a single archive.

A profile whose JDK home can't be resolved (not installed via sdkman, or an
invalid --jdkXX-home) is skipped with a warning rather than aborting the
whole run.

For jdk22plus specifically, ANY JDK >= 22 produces a byte-identical artifact
(pom.xml compiles it with `--release 22`, which pins the floor regardless of
the build JDK), so which one you point --jdk22plus-home at doesn't affect
correctness. JDK 25 is used by default via sdkman auto-detection purely to
match the build JDK build-test-platform.yml already pins for reproducibility
— pass an explicit --jdk22plus-home to build with a different JDK >= 22.

Usage:
  python3 scripts/build-release-sdks.py --version v4.0.0
  python3 scripts/build-release-sdks.py --version v4.0.0 --output-dir /path/to/output

  # Windows (JDK paths required up front for both profiles):
  python scripts\\build-release-sdks.py --version v4.0.0 ^
      --jdk21-home "C:\\Path\\To\\jdk-21" ^
      --jdk22plus-home "C:\\Path\\To\\jdk-25"

  # Restricted Windows machines that block running unsigned executables
  # (e.g. corporate-managed runners) — skip building/running the .exe wrappers:
  python scripts\\build-release-sdks.py --version v4.0.0 ^
      --jdk21-home "C:\\Path\\To\\jdk-21" ^
      --jdk22plus-home "C:\\Path\\To\\jdk-25" ^
      --skip-windows-executables

  # Draft release (test): --version's tag v4.0.0 was never created (see
  # --draft-release-test above) — build from origin/master instead, still
  # labeling output archives "v4.0.0":
  python3 scripts/build-release-sdks.py --version v4.0.0 --draft-release-test true

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

def _find_sdkman_temurin_jdk(major_version, profile):
    """
    Locate the newest installed Temurin JDK for *major_version* under
    ~/.sdkman/candidates/java/.  Returns the full path to the JDK home, or
    None (with a warning) if sdkman or that JDK isn't installed.

    *profile* (e.g. 'jdk21', 'jdk22plus') is only used in messaging — the
    lookup itself is purely by major_version.
    """
    sdkman_java_dir = Path.home() / ".sdkman" / "candidates" / "java"
    if not sdkman_java_dir.is_dir():
        warn(
            f"sdkman candidates directory not found at {sdkman_java_dir} — "
            f"skipping {profile}.\n"
            "  Install sdkman: https://sdkman.io  then:\n"
            f"    sdk install java <{major_version}.x.y>-tem"
        )
        return None

    candidates = [
        entry
        for entry in sdkman_java_dir.iterdir()
        if entry.is_dir()
        and entry.name.startswith(f"{major_version}.")
        and entry.name.endswith("-tem")
    ]

    if not candidates:
        warn(
            f"No Temurin JDK {major_version} found in sdkman — skipping {profile}.\n"
            f"  Install with: sdk install java <{major_version}.x.y>-tem"
        )
        return None

    candidates.sort(key=lambda p: p.name, reverse=True)
    chosen = candidates[0]
    info(f"{profile}: using {chosen.name}  ({chosen})")
    return str(chosen)


def resolve_jdk_home(profile, build_major, override):
    """
    Return the JAVA_HOME path to build *profile* with, or None (with a
    warning) if it can't be resolved — the caller skips that profile rather
    than aborting the whole run.

    *build_major* is the JDK major version used to (a) auto-locate a JDK via
    sdkman on macOS/Linux and (b) name the JDK in Windows messaging. For
    'jdk21' this is the exact required major (jdk21 is --enable-preview and
    only loads on JDK 21). For 'jdk22plus' it is just the pinned, reproducible
    build JDK (25) — any JDK >= 22 builds an equally valid jdk22plus SDK
    (see module docstring), so pass --jdk22plus-home to use a different one.

    - If *override* is given (--jdk21-home / --jdk22plus-home), use it.
    - Otherwise on macOS/Linux look up sdkman for build_major.
    - On Windows an override is mandatory; skipped with instructions if missing.
    """
    if override:
        override = str(override)
        if not os.path.isdir(override):
            warn(f"Provided {profile} path does not exist: {override} — skipping.")
            return None
        info(f"{profile}: using override  ({override})")
        return override

    if detect_platform() == "windows":
        warn(
            f"On Windows you must supply the {profile} JDK path explicitly — skipping.\n"
            f"  Add:  --{profile}-home \"C:\\\\Path\\\\To\\\\jdk-{build_major}\""
        )
        return None

    return _find_sdkman_temurin_jdk(build_major, profile)


# ---------------------------------------------------------------------------
# Build matrix
# ---------------------------------------------------------------------------

# Backends to build per platform.  Each entry becomes BACKEND=<value> in make.
# On macOS/Linux the Makefile target is:  make sdk BACKEND=<value>
# On Windows it is:  nmake /f Makefile.mak sdk BACKEND=<value>
# opencl+cuda is labelled "full" automatically by bin/compile.
BUILDS = {
    "macos": [
        "opencl",
        "metal",
    ],
    "linux": [
        "opencl",
        "cuda",
        "opencl,cuda",
    ],
    "windows": [
        "opencl",
        "cuda",
    ],
}

# The two release-facing JDK build profiles (bin/compile's SDK_JDK_CONTRACT /
# pom.xml). Order here is the build order. Value is the JDK major version used
# to auto-locate a build JDK via sdkman and to name it in Windows messaging —
# for jdk22plus this is just the pinned reproducible build JDK, not a
# requirement: any JDK >= 22 produces an identical jdk22plus artifact.
JDK_PROFILES = {
    "jdk21": 21,
    "jdk22plus": 25,
}


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
    """
    Fetch *tag* from origin if it is not already present locally.

    Fails soft: if the tag does not exist on origin either, the fetch returns
    non-zero and we leave it to the caller's tag_exists() check to emit the
    documented "tag not found — skipping" warning and move on to the next tag.
    """
    if tag_exists(tag):
        return
    info(f"Tag {tag} not found locally — fetching from origin...")
    result = subprocess.run(
        ["git", "fetch", "origin", f"refs/tags/{tag}:refs/tags/{tag}"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        stderr = result.stderr.strip()
        warn(f"Could not fetch tag {tag} from origin{': ' + stderr if stderr else ''}")


def fetch_branch(branch):
    """
    Fetch *branch* from origin into refs/remotes/origin/<branch>, for
    --draft-release-test's fallback to master when no release tag exists yet.

    Unlike fetch_tag this is NOT soft-fail: draft-test mode has nothing else
    to build from, so a fetch failure here aborts the run immediately with a
    clear error rather than falling through to a confusing worktree-add error.
    """
    info(f"Fetching {branch} from origin...")
    result = subprocess.run(
        ["git", "fetch", "origin", f"{branch}:refs/remotes/origin/{branch}"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        stderr = result.stderr.strip()
        error(f"Could not fetch {branch} from origin{': ' + stderr if stderr else ''}")
        sys.exit(1)


def add_worktree(ref, worktree_path):
    """Create a detached git worktree at *worktree_path* checked out to *ref*
    (a tag for a real release build, or 'origin/master' for --draft-release-test)."""
    subprocess.run(
        ["git", "worktree", "add", "--detach", worktree_path, ref],
        check=True,
    )
    ok(f"Worktree created at {worktree_path}  (ref: {ref})")


def remove_worktree(worktree_path):
    """Remove the git worktree and delete its directory."""
    subprocess.run(
        ["git", "worktree", "remove", "--force", worktree_path],
        capture_output=True,
    )
    shutil.rmtree(worktree_path, ignore_errors=True)
    info(f"Worktree removed: {worktree_path}")


# ---------------------------------------------------------------------------
# Pre-flight checks
# ---------------------------------------------------------------------------

def check_pyinstaller():
    """
    Verify that pyinstaller is on PATH (Windows only).

    The Windows SDK build calls PyInstaller inside bin/compile to produce
    tornado.exe, tornado-test.exe, and tornado-benchmarks.exe.  The underlying
    call uses os.system() which silently ignores failure, so if pyinstaller is
    missing the build completes without any .exe files and CMD users cannot run
    the SDK.  Failing early here avoids a silent bad build.
    """
    if shutil.which("pyinstaller") is None:
        error(
            "pyinstaller not found on PATH.\n"
            "  The Windows SDK build requires PyInstaller to produce tornado.exe.\n"
            "  Install it with:  pip install pyinstaller"
        )
        sys.exit(1)
    info("pyinstaller: found")


def patch_worktree_fix_pyinstaller(worktree_path):
    """
    Patch runPyInstaller() in the worktree's config_utils.py so PyInstaller is
    invoked without chdir'ing into <tornado_home>/bin first.

    The tagged config_utils.py does:
        path = os.path.join(tornadoSDKPath, "bin")
        os.chdir(path)
        ...
        os.system("pyinstaller " + s + " --onefile")
        ...
        os.chdir(currentDirectory)

    Since our release SDK build output paths contain a 'dist' segment
    (dist/tornadovm-X.Y.Z-<backend>-windows-amd64/tornadovm-X.Y.Z-<backend>/bin),
    PyInstaller's own safety check detects what looks like one of its previous
    output trees and refuses to run:

        ERROR: Do not run pyinstaller from <path>\\dist\\...\\bin.

    This patch replaces the function body so PyInstaller runs from the
    original working directory, with explicit --distpath/--workpath/--specpath
    arguments, avoiding the chdir entirely.

    The worktree is a throw-away detached checkout of a release tag, so
    editing its config_utils.py here does not touch the repository or the
    published tag itself.
    """
    config_utils_path = os.path.join(worktree_path, "bin", "config_utils.py")
    if not os.path.isfile(config_utils_path):
        warn("config_utils.py not found in worktree — cannot patch runPyInstaller.")
        return

    with open(config_utils_path, "r", encoding="utf-8") as f:
        src = f.read()
    original = src

    # v5.1.0-jdk25 tag shape: chdir's into <sdk>/bin before invoking PyInstaller
    # and chdir's back afterwards — the classic "run me from inside dist/"
    # trigger.
    old_func_pattern_chdir = re.compile(
        r"def runPyInstaller\(currentDirectory, tornadoSDKPath\):.*?"
        r"os\.chdir\(currentDirectory\)\n",
        re.DOTALL,
    )

    # v5.1.0-jdk21 tag shape: already avoids the chdir and already passes
    # explicit --distpath/--workpath/--specpath via os.system(), but never
    # overrides the *process* cwd — so it still inherits the worktree root
    # (created under the OS temp dir), which trips the same PyInstaller safety
    # check for a different reason. This variant has no trailing
    # os.chdir(...) and instead ends with the shutil.rmtree(work_dir, ...)
    # cleanup line.
    old_func_pattern_system = re.compile(
        r"def runPyInstaller\(currentDirectory, tornadoSDKPath\):.*?"
        r"shutil\.rmtree\(work_dir, ignore_errors=True\)\n",
        re.DOTALL,
    )

    new_func = '''def runPyInstaller(currentDirectory, tornadoSDKPath):
    import subprocess, tempfile

    bin_dir = os.path.join(tornadoSDKPath, "bin")
    work_dir = tempfile.mkdtemp(prefix="pyinstaller-build-")
    repo_root = os.environ.get("TORNADO_BUILD_REPO_ROOT", currentDirectory)

    scripts = ["tornado.py", "tornado-test", "tornado-benchmarks.py"]
    failed = []
    for s in scripts:
        print("creating " + s + " binary ....  "),
        script_path = os.path.join(bin_dir, s)
        result = subprocess.run(
            ["pyinstaller", script_path, "--onefile",
             "--distpath", bin_dir, "--workpath", work_dir, "--specpath", work_dir],
            cwd=repo_root,
        )
        if result.returncode != 0:
            print(f"[ERROR] PyInstaller failed for {s} (exit code {result.returncode})")
            failed.append(s)
        else:
            print("ok ")

    shutil.rmtree(work_dir, ignore_errors=True)
    if failed:
        raise RuntimeError(f"PyInstaller failed for: {\', \'.join(failed)}")
'''

    src, n = old_func_pattern_chdir.subn(new_func, src)
    if n == 0:
        src, n = old_func_pattern_system.subn(new_func, src)

    if n == 0:
        warn(
            "config_utils.py in the worktree did not match either known "
            "runPyInstaller pattern — the tag layout may have changed; "
            "nothing was patched."
        )
        return

    with open(config_utils_path, "w", encoding="utf-8") as f:
        f.write(src)
    ok("Patched worktree config_utils.py to fix PyInstaller dist-path check.")


def patch_worktree_fix_cutlass(worktree_path):
    """
    Patch tornado-drivers/cutlass-jni/src/main/cpp/CMakeLists.txt in the
    worktree so its CUTLASS fetch uses git sparse-checkout instead of a plain
    FetchContent git clone.

    The tagged CMakeLists.txt does:
        include(FetchContent)
        FetchContent_Declare(cutlass GIT_REPOSITORY ... GIT_TAG v3.5.1 GIT_SHALLOW TRUE)
        FetchContent_GetProperties(cutlass)
        if(NOT cutlass_POPULATED)
            FetchContent_Populate(cutlass)
        endif()

    This checks out CUTLASS's entire tree, including its docs/ folder —
    thousands of Doxygen-generated HTML files with very long, template-mangled
    names. On Windows, the combined path (worktree + build dir + docs/<long
    name>.html) exceeds the 260-char MAX_PATH, and git's checkout of those
    files fails ("unable to create file docs/...: Filename too long") even
    with core.longpaths=true set — confirmed in CI, this git-for-windows
    install doesn't fully honor that flag for the checkout inside git clone.

    v5.1.0-jdk21 and v5.1.0-jdk25 are already-published, immutable release
    tags, so the fix (landed on develop/ci branches) can't reach them by
    editing the source tree directly — same reason runPyInstaller is patched
    above instead of just fixed on the tag. This rewrites the fetch to use
    git sparse-checkout (cone mode), pulling only include/ and
    tools/util/include/ — the two subtrees the build actually uses — so
    docs/ is never fetched at all, on any platform, and the worktree is a
    throw-away detached checkout, so editing its CMakeLists.txt here does not
    touch the repository or the published tag itself.
    """
    cmake_path = os.path.join(
        worktree_path, "tornado-drivers", "cutlass-jni", "src", "main", "cpp", "CMakeLists.txt"
    )
    if not os.path.isfile(cmake_path):
        warn("cutlass-jni/CMakeLists.txt not found in worktree — cannot patch CUTLASS fetch.")
        return

    with open(cmake_path, "r", encoding="utf-8") as f:
        src = f.read()
    original = src

    old_fetch_pattern = re.compile(
        r"include\(FetchContent\)\s*"
        r"FetchContent_Declare\(\s*cutlass.*?\)\s*"
        r"FetchContent_GetProperties\(cutlass\)\s*"
        r"if\(NOT cutlass_POPULATED\)\s*"
        r"FetchContent_Populate\(cutlass\)\s*"
        r"endif\(\)\n",
        re.DOTALL,
    )

    new_fetch = '''set(CUTLASS_SOURCE_DIR "${CMAKE_BINARY_DIR}/_deps/cutlass-src")
if(NOT EXISTS "${CUTLASS_SOURCE_DIR}/include/cutlass/cutlass.h")
    find_package(Git REQUIRED)
    file(REMOVE_RECURSE "${CUTLASS_SOURCE_DIR}")
    file(MAKE_DIRECTORY "${CUTLASS_SOURCE_DIR}")

    macro(cutlass_git)
        execute_process(
            COMMAND ${GIT_EXECUTABLE} ${ARGN}
            WORKING_DIRECTORY "${CUTLASS_SOURCE_DIR}"
            RESULT_VARIABLE _cutlass_git_rc
        )
        if(NOT _cutlass_git_rc EQUAL 0)
            message(FATAL_ERROR "CUTLASS sparse checkout failed: git ${ARGN}")
        endif()
    endmacro()

    cutlass_git(init -q)
    cutlass_git(remote add origin https://github.com/NVIDIA/cutlass.git)
    cutlass_git(sparse-checkout init --cone)
    cutlass_git(sparse-checkout set include tools/util/include)
    cutlass_git(fetch --depth 1 origin v3.5.1)
    cutlass_git(checkout FETCH_HEAD)
endif()
set(cutlass_SOURCE_DIR "${CUTLASS_SOURCE_DIR}")
'''

    src, n = old_fetch_pattern.subn(new_fetch, src)

    if n == 0:
        warn(
            "cutlass-jni/CMakeLists.txt in the worktree did not match the "
            "expected FetchContent pattern — the tag layout may have "
            "changed; nothing was patched."
        )
        return

    with open(cmake_path, "w", encoding="utf-8") as f:
        f.write(src)
    ok("Patched worktree cutlass-jni/CMakeLists.txt to sparse-checkout CUTLASS (avoids Windows MAX_PATH).")


def patch_worktree_skip_executables(worktree_path):
    """
    Neutralise the places where the tagged bin/compile launches native
    executables, for restricted Windows machines that block running unsigned
    binaries (and abort the build when they are invoked):

      * cutils.runPyInstaller(...)  — produces tornado.exe / tornado-test.exe /
                                      tornado-benchmarks.exe via pyinstaller.exe
      * subprocess.run([... "zello_world"])  — Level Zero sanity probe used by
                                               the SPIR-V backend build

    The worktree is a throw-away detached checkout of a release tag, so editing
    its bin/compile here does not touch the repository or the tag.  When this is
    active the produced Windows SDK ships the .py launchers instead of .exe
    wrappers.
    """
    compile_path = os.path.join(worktree_path, "bin", "compile")
    if not os.path.isfile(compile_path):
        warn("bin/compile not found in worktree — cannot skip Windows executables.")
        return

    with open(compile_path, "r", encoding="utf-8") as f:
        src = f.read()
    original = src

    # Both substitutions are anchored to start/end of line (re.MULTILINE) and
    # capture leading indentation, so the replacement is always a syntactically
    # valid single statement — even if the tagged bin/compile changes the
    # arguments of the call.  If the call ever stops being a standalone line
    # (e.g. wrapped in an assignment or condition), the regex simply will not
    # match and the warn() branch below fires.

    # Skip PyInstaller (tornado.exe & friends)
    src = re.sub(
        r"^([ \t]*)cutils\.runPyInstaller\([^\n]*\)[ \t]*$",
        r'\1print("[INFO] Skipping PyInstaller — --skip-windows-executables")',
        src,
        flags=re.MULTILINE,
    )
    # Skip the zello_world Level Zero probe, keeping the surrounding logic
    src = re.sub(
        r"^([ \t]*)subprocess\.run\(\[os\.path\.join\(level_zero_lib,[^\n]*zello_world[^\n]*$",
        r'\1print("[INFO] Skipping zello_world probe — --skip-windows-executables")',
        src,
        flags=re.MULTILINE,
    )

    if src == original:
        warn(
            "bin/compile in the worktree did not match the expected patterns — "
            "the tag layout may have changed; nothing was patched."
        )
        return

    with open(compile_path, "w", encoding="utf-8") as f:
        f.write(src)
    ok("Patched worktree bin/compile to skip Windows executables (PyInstaller, zello_world).")


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


def build_sdk(worktree_path, jdk_home, jdk_arg, backends, label, repo_root):
    """
    Build a single SDK variant inside *worktree_path*.

    On macOS and Linux:  make sdk-<jdk_arg> BACKEND=<backends>
    On Windows:          nmake /f Makefile.mak sdk-<jdk_arg> BACKEND=<backends>

    The sdk-jdk21 / sdk-jdk22plus targets pin the Maven profile directly
    rather than going through the generic `sdk` target's JDK-autodetection
    (which on Unix parses JAVA_HOME's `java -version` and on Windows just
    defaults to jdk21 with no autodetection at all — silently building jdk21
    twice instead of ever reaching jdk22plus). Pinning the target explicitly
    makes profile selection correct on both platforms and independent of
    JAVA_HOME parsing.
    """
    clean_graal_jars(worktree_path)

    env = os.environ.copy()
    env["JAVA_HOME"] = jdk_home
    env["TORNADO_BUILD_REPO_ROOT"] = repo_root
    # Prepend the build JDK's bin/ to PATH so that any script that runs `java`
    # (e.g. gen-tornado-argfile-template.py's is_graalvm() check) sees the
    # correct JDK rather than whatever is current in the shell.
    jdk_bin = os.path.join(jdk_home, "bin")
    env["PATH"] = jdk_bin + os.pathsep + env.get("PATH", "")

    target = f"sdk-{jdk_arg}"
    if os.name == "nt":
        cmd = [
            "nmake", "/f", "Makefile.mak", target, f"BACKEND={backends}",
        ]
    else:
        cmd = ["make", target, f"BACKEND={backends}"]

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

_JDK_TOKEN_RE = re.compile(r"-jdk\d+(?:plus)?(?=-|\.)")


def _set_jdk_in_name(dest_path, jdk_arg):
    """
    Rename *dest_path* so its filename carries exactly *jdk_arg* (e.g. 'jdk21'
    or 'jdk22plus') as its JDK identifier — stripping any other jdk token
    already present and inserting the correct one, not just inserting when
    absent.

    Under the unified single-tag model both profiles are built from the same
    worktree/commit, whose committed pom.xml version carries no JDK suffix at
    all (e.g. "4.0.0"), so bin/compile's archive names never contain a JDK
    identifier on their own — this always inserts one. The strip step makes
    it robust even if that ever changes (e.g. a stray "-jdk21" baked into a
    dev version string):

      tornadovm-4.0.0-opencl-mac-aarch64.tar.gz
        → tornadovm-4.0.0-jdk22plus-opencl-mac-aarch64.tar.gz
      tornadovm-4.0.0-jdk21-dev-opencl-mac-aarch64.tar.gz  (built with --jdk jdk22plus)
        → tornadovm-4.0.0-jdk22plus-dev-opencl-mac-aarch64.tar.gz
    """
    basename = os.path.basename(dest_path)

    if _JDK_TOKEN_RE.search(basename):
        new_basename = _JDK_TOKEN_RE.sub(f"-{jdk_arg}", basename, count=1)
    else:
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
    stamping *jdk_arg* into each filename as its JDK identifier (see
    _set_jdk_in_name — every archive gets this, not just ones missing it,
    since both profiles build from the same un-suffixed source version).
    Returns a list of destination paths.
    """
    dist_dir = os.path.join(worktree_path, "dist")
    collected = []
    for pattern in ("*.tar.gz", "*.zip"):
        for archive in glob.glob(os.path.join(dist_dir, pattern)):
            dest = os.path.join(output_dir, os.path.basename(archive))
            shutil.move(archive, dest)
            dest = _set_jdk_in_name(dest, jdk_arg)
            ok(f"Saved: {os.path.basename(dest)}")
            collected.append(dest)
    return collected


# ---------------------------------------------------------------------------
# SDK validation
# ---------------------------------------------------------------------------

def validate_sdk(archive_path, jdk_home, skip_windows_executables=False, test_hybrid_api=False):
    """
    Extract a .zip SDK archive to a temporary directory and run three smoke tests:

      1. tornado --devices      — confirms the runtime can enumerate devices
      2. tornado --version      — confirms the version string is readable
      3. java @tornado-argfile  — runs MatrixVectorRowMajor as a minimal kernel test

    The tornado-argfile.template shipped in the archive uses ${TORNADOVM_HOME}
    placeholders.  This function expands them against the extraction directory
    before invoking java.

    If test_hybrid_api is True (pass this only for cuda/full-backend archives),
    three more checks run via tornado-test: TestCuBlas, TestCuDnn, TestCuFft
    (the hybrid library-task API — cuBLAS, cuDNN, cuFFT called from Java). Each
    of these self-skips as [UNSUPPORTED] rather than failing when the default
    device isn't CUDA or the native library (e.g. libtornado-cublas) isn't
    loadable — TornadoHelper counts TornadoVMCUDANotSupported separately from
    real failures. Their pass/fail is NOT read from the subprocess return code:
    `tornado-test -V <FQCN>` always exits 0 regardless of outcome (see
    _passed_by_tornado_test_summary below for why) - it is read from the JVM's
    own "Test ran: N, Failed: M, Unsupported: K" summary line instead. So this
    is safe to run even on a validation host without the NVIDIA math libraries
    installed: it degrades to confirming "doesn't crash" there rather than
    "hybrid API actually works" — a real exercise of the code path needs a
    host that also has cuBLAS/cuDNN/cuFFT provisioned, not just a CUDA GPU.

    Returns True if all checks pass (or safely self-skip), False otherwise.
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

        java_cmd = os.path.join(jdk_home, "bin", "java")

        # On Windows the SDK normally ships bin\tornado.exe (compiled by PyInstaller).
        # On Unix it ships bin/tornado (a shell script that needs +x after zip extraction).
        # With --skip-windows-executables there is no tornado.exe, so the tornado
        # CLI smoke checks are skipped and only the java @argfile kernel test runs.
        tornado_cmd = None
        if os.name == "nt":
            if skip_windows_executables:
                warn(
                    "  Skipping tornado.exe smoke checks (--skip-windows-executables); "
                    "running only the java @tornado-argfile kernel test."
                )
            else:
                tornado_exe = os.path.join(tornado_home, "bin", "tornado.exe")
                if os.path.isfile(tornado_exe):
                    tornado_cmd = [tornado_exe]
                else:
                    error(
                        f"  tornado.exe not found in {basename}.\n"
                        "  The Windows SDK build should have run PyInstaller to produce it.\n"
                        "  Ensure pyinstaller is installed (pip install pyinstaller) and rebuild,\n"
                        "  or pass --skip-windows-executables if this is intentional."
                    )
                    return False
        else:
            tornado_script = os.path.join(tornado_home, "bin", "tornado")
            os.chmod(tornado_script, 0o755)
            tornado_cmd = [tornado_script]

        # Same resolution as tornado_cmd above, for the hybrid-API checks below.
        tornado_test_cmd = None
        if test_hybrid_api:
            if os.name == "nt":
                if not skip_windows_executables:
                    tornado_test_exe = os.path.join(tornado_home, "bin", "tornado-test.exe")
                    if os.path.isfile(tornado_test_exe):
                        tornado_test_cmd = [tornado_test_exe]
                    else:
                        error(f"  tornado-test.exe not found in {basename} — cannot run hybrid API checks.")
                        return False
            else:
                tornado_test_script = os.path.join(tornado_home, "bin", "tornado-test")
                os.chmod(tornado_test_script, 0o755)
                tornado_test_cmd = [tornado_test_script]

        def _passed_by_returncode(result):
            return result.returncode == 0

        def _passed_by_tornado_test_summary(result):
            """`tornado-test -V <FQCN>` (single test class) takes a different code path
            from a whole-suite run: runSingleCommand (tornado-assembly/src/bin/tornado-test)
            prints the subprocess's raw output and returns without ever calling
            processStats or touching __TEST_NOT_PASSED__, and never inspects the
            subprocess's own return code either - so the wrapper's process exit code is
            always 0 here, pass or fail. (main()'s sys.exit(1) is gated solely on
            __TEST_NOT_PASSED__, which only runTestTheWorld's path ever sets.) The JVM
            side still prints an authoritative "Test ran: N, Failed: M, Unsupported: K"
            summary even for a single class in verbose mode (TornadoHelper.printResult) -
            parse that instead of trusting the process exit code.
            """
            output = result.stdout + result.stderr
            match = re.search(r"Failed:\s*(\d+)", output)
            if not match:
                return False  # crashed, or produced no recognizable output, before finishing
            return int(match.group(1)) == 0

        checks = []
        if tornado_cmd is not None:
            checks.append(([*tornado_cmd, "--devices"], "tornado --devices", _passed_by_returncode))
            checks.append(([*tornado_cmd, "--version"],  "tornado --version", _passed_by_returncode))
        checks.append(
            (
                [java_cmd, f"@{argfile_path}", "-cp", examples_jar,
                 "uk.ac.manchester.tornado.examples.compute.MatrixVectorRowMajor"],
                "MatrixVectorRowMajor",
                _passed_by_returncode,
            )
        )
        if tornado_test_cmd is not None:
            for test_class in (
                "uk.ac.manchester.tornado.unittests.cublas.TestCuBlas",
                "uk.ac.manchester.tornado.unittests.cudnn.TestCuDnn",
                "uk.ac.manchester.tornado.unittests.cufft.TestCuFft",
            ):
                checks.append(
                    ([*tornado_test_cmd, "-V", test_class], f"hybrid-api {test_class.rsplit('.', 1)[-1]}",
                     _passed_by_tornado_test_summary)
                )

        passed = True
        for cmd, name, is_passed in checks:
            result = subprocess.run(cmd, env=env, capture_output=True, text=True)
            if is_passed(result):
                ok(f"  [{name}] passed")
            else:
                error(f"  [{name}] FAILED")
                output = (result.stdout + result.stderr).strip()
                if output:
                    print(output[-1500:])
                passed = False

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
            "Release version tag (e.g. v4.0.0).  "
            "The script checks out this ONE tag and builds every available JDK from it."
        ),
    )
    parser.add_argument(
        "--output-dir",
        default="release-sdks",
        metavar="DIR",
        help="Root directory where SDK archives are saved (default: release-sdks/)",
    )
    parser.add_argument(
        "--draft-release-test",
        default="false",
        choices=["true", "false"],
        metavar="{true,false}",
        help=(
            "Pass 'true' when --version's tag is intentionally not created yet — "
            "2-finalize-release.yml's draft-test path skips tag/Release creation "
            "entirely, so build-release-sdks.yml dispatches this script with "
            "draft_release_test=true and the tag genuinely does not exist. In that "
            "case this builds from origin/master instead of requiring the tag "
            "(mirroring deploy-maven-central.yml's own draft-test checkout "
            "fallback), while archives are still labeled with --version as usual. "
            "Default 'false' requires the tag to exist, as normal."
        ),
    )
    parser.add_argument(
        "--jdk21-home",
        metavar="PATH",
        default=None,
        help=(
            "Path to a JDK 21 home, used to build the jdk21 profile "
            "(--enable-preview, pinned to exactly JDK 21).  "
            "Required on Windows; overrides sdkman auto-detection on macOS/Linux."
        ),
    )
    parser.add_argument(
        "--jdk22plus-home",
        metavar="PATH",
        default=None,
        help=(
            "Path to a JDK >= 22 home, used to build the jdk22plus profile "
            "(no preview features; the resulting SDK runs unmodified on any "
            "JDK from 22 up, so any JDK >= 22 here produces an identical "
            "artifact — sdkman auto-detection on macOS/Linux defaults to 25 "
            "purely for a reproducible build JDK).  Required on Windows."
        ),
    )
    parser.add_argument(
        "--skip-windows-executables",
        action="store_true",
        help=(
            "Windows only: do not build or run the native .exe wrappers "
            "(tornado.exe via PyInstaller / pyinstaller.exe, and the zello_world "
            "Level Zero probe).  Use on restricted/managed machines that block "
            "running unsigned executables.  The produced SDK ships the .py "
            "launchers instead of .exe wrappers, and the SDK validation skips "
            "the tornado.exe smoke checks."
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

    skip_win_exe = args.skip_windows_executables and current_platform == "windows"

    if current_platform == "windows" and not skip_win_exe:
        check_pyinstaller()

    section(f"TornadoVM Release SDK Builder  {args.version}")
    info(f"Platform : {current_platform}-{arch}")
    info(f"Version  : {args.version}")
    if skip_win_exe:
        info("Windows executables: SKIPPED (--skip-windows-executables) — "
             "tornado.exe / pyinstaller.exe / zello_world will not be invoked.")

    output_dir = os.path.join(
        args.output_dir, args.version, f"{current_platform}-{arch}"
    )
    os.makedirs(output_dir, exist_ok=True)
    info(f"Output   : {os.path.abspath(output_dir)}")

    # One shared tag for every JDK — no more per-JDK divergent tags/branches.
    # Archives are always labeled with this, whether or not the tag itself
    # exists yet (see draft-release-test below).
    tag = args.version
    draft_release_test = args.draft_release_test == "true"

    if draft_release_test:
        # 2-finalize-release.yml's draft-test path deliberately skips creating
        # the v<version> tag/Release (nothing to publish to in test mode), so
        # by the time build-release-sdks.yml dispatches this script the tag
        # genuinely does not exist yet. Build from origin/master instead —
        # the same fallback deploy-maven-central.yml already uses for its own
        # checkout in draft-test mode — rather than requiring it.
        warn(
            f"Draft release (test): tag {tag} was intentionally not created "
            f"(2-finalize-release.yml skips tag/Release creation in this mode) "
            f"— building from master instead. Archives are still labeled {tag}."
        )
        fetch_branch("master")
        build_ref = "origin/master"
    else:
        build_ref = tag
        info(f"Checking tag {tag} ...")
        fetch_tag(tag)
        if not tag_exists(tag):
            error(f"Tag {tag} not found. Nothing to build.")
            sys.exit(1)

    # Resolve JDK homes before starting any build. A profile whose JDK can't
    # be resolved (not provisioned on this builder yet) is skipped with a
    # warning rather than aborting the whole run.
    profile_overrides = {
        "jdk21": args.jdk21_home,
        "jdk22plus": args.jdk22plus_home,
    }
    validated = []
    for profile, build_major in JDK_PROFILES.items():
        jdk_home = resolve_jdk_home(profile, build_major, profile_overrides[profile])
        if jdk_home:
            validated.append((profile, jdk_home))

    if not validated:
        error("No JDK homes could be resolved. Nothing to build.")
        sys.exit(1)

    backends_for_platform = BUILDS[current_platform]
    results = []  # list of (label, success)

    repo_root = os.getcwd()

    # One worktree for the whole run — every JDK builds from the same checkout.
    worktree_path = tempfile.mkdtemp(prefix=f"tornadovm-{tag}-")
    try:
        add_worktree(build_ref, worktree_path)

        if current_platform == "windows" and not skip_win_exe:
            patch_worktree_fix_pyinstaller(worktree_path)

        if current_platform == "windows":
            patch_worktree_fix_cutlass(worktree_path)

        if skip_win_exe:
            patch_worktree_skip_executables(worktree_path)

        for jdk_arg, jdk_home in validated:
            section(f"{jdk_arg}  (tag: {tag})")

            for backends in backends_for_platform:
                # Label mirrors bin/compile naming: opencl,cuda → full
                if set(backends.split(",")) == {"opencl", "cuda"}:
                    backend_label = "full"
                else:
                    backend_label = backends
                label = f"{tag}-{jdk_arg}-{backend_label}"
                success = build_sdk(worktree_path, jdk_home, jdk_arg, backends, label, repo_root)
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
    jdk_home_by_arg = dict(validated)

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
        # Archive names embed the backend as a hyphen-delimited component set by
        # bin/compile (tornadovm-<version>-<jdk>-<backend>-<platform>...): "cuda"
        # for a CUDA-only build, "full" for the combined opencl+cuda build (never
        # "opencl-cuda" — see bin/compile's backend_variant logic). Either means
        # this archive can carry the hybrid API checks.
        name_parts = basename.split("-")
        is_cuda_capable = "cuda" in name_parts or "full" in name_parts
        success = validate_sdk(
            archive, jdk_home,
            skip_windows_executables=skip_win_exe,
            test_hybrid_api=is_cuda_capable,
        )
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
