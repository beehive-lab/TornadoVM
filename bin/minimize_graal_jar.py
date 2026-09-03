#!/usr/bin/env python3

#
# Copyright (c) 2013-2024, APT Group, Department of Computer Science,
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
Shrink graalJars/compiler-<VERSION>.jar to the bare-minimum set of classes that
TornadoVM needs to compile and run GPU kernels.

The compiler jar (module ``jdk.internal.vm.compiler``) ships ~4476 classes for the
full HotSpot/Truffle/multi-arch GraalVM JIT; TornadoVM only drives its own GPU
sketch/lowering pipeline and uses a small fraction of that. The keep-list in
``bin/graal-compiler-keep.txt`` is the verified working set: the union of

  * every compiler class actually class-loaded across a broad TornadoVM unit-test
    sweep (arrays, reductions, vectors, math, loops, conditionals, images, atomics,
    fields, kernelcontext, matrix-mul, api...) -- this captures the reflective /
    ServiceLoader closure, since ServiceLoader instantiates every provider;
  * every ``org.graalvm.compiler.*`` type imported by TornadoVM source (static floor
    for code paths the tests did not exercise, e.g. FFT/cuBLAS/dynamic reconfig);
  * the smallest class of every original package (keeps ModulePackages valid);
  * every ``provides ... with`` service implementation named by module-info;
  * the nested classes of all of the above.

Result: compiler-23.1.0.jar 21.5MB -> ~4.5MB, byte-for-byte identical unit-test
outcomes. Pinned to Graal 23.1.0; regenerate the keep-list if that version bumps
(re-run the sweep with ``-Xlog:class+load`` -- see the module docstring history).

The trim is idempotent: a trimmed jar carries a marker entry and is left untouched.
"""

import logging
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

TARGET_DIR = "graalJars"
COMPILER_PREFIX = "compiler-"
KEEP_LIST = os.path.join(os.path.dirname(os.path.abspath(__file__)), "graal-compiler-keep.txt")
# Marker entry written into a trimmed jar so re-runs are no-ops.
MARKER = "META-INF/tornado-graal-minimized"

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def _find_compiler_jar(target_dir):
    if not os.path.isdir(target_dir):
        return None
    for name in os.listdir(target_dir):
        if name.startswith(COMPILER_PREFIX) and name.endswith(".jar"):
            return os.path.join(target_dir, name)
    return None


def _load_keep_set():
    with open(KEEP_LIST, "r") as f:
        return {line.strip() for line in f if line.strip() and not line.startswith("#")}


def minimize(compiler_jar=None):
    """Trim the compiler jar in place to the checked-in keep-list. Idempotent."""
    if compiler_jar is None:
        compiler_jar = _find_compiler_jar(TARGET_DIR)
    if compiler_jar is None or not os.path.exists(compiler_jar):
        logger.warning("No compiler-*.jar found under %s; nothing to minimize.", TARGET_DIR)
        return

    with zipfile.ZipFile(compiler_jar, "r") as zf:
        names = zf.namelist()
        if MARKER in names:
            logger.info("%s already minimized; skipping.", os.path.basename(compiler_jar))
            return

    keep = _load_keep_set()
    tmp_dir = tempfile.mkdtemp(prefix="graal-min-")
    try:
        with zipfile.ZipFile(compiler_jar, "r") as zf:
            all_entries = zf.namelist()
            # Keep: the keep-list classes, everything under META-INF/ (services,
            # versions, manifest), module-info.class, and non-class resources.
            selected = []
            for entry in all_entries:
                if entry.endswith("/"):
                    continue
                if entry == "module-info.class" or entry.startswith("META-INF/"):
                    selected.append(entry)
                elif entry.endswith(".class"):
                    if entry in keep:
                        selected.append(entry)
                else:
                    # non-class resource (rare in the compiler jar) -- keep it
                    selected.append(entry)
            for entry in selected:
                zf.extract(entry, tmp_dir)

        # sanity: every keep-list entry that exists in the jar must be present
        missing = [k for k in keep if k in set(all_entries) and not os.path.exists(os.path.join(tmp_dir, k))]
        if missing:
            raise RuntimeError(f"keep-list extraction incomplete: {missing[:5]} ...")

        # drop marker so the trim is detectable/idempotent
        marker_path = os.path.join(tmp_dir, MARKER)
        os.makedirs(os.path.dirname(marker_path), exist_ok=True)
        with open(marker_path, "w") as m:
            m.write("TornadoVM bare-minimum Graal compiler jar. See bin/minimize_graal_jar.py\n")

        # repackage as a modular jar (module-info.class + ModulePackages preserved;
        # every original package retained non-empty so the descriptor stays valid).
        orig_size = os.path.getsize(compiler_jar)
        new_jar = compiler_jar + ".min"
        _jar_create(new_jar, tmp_dir)
        shutil.move(new_jar, compiler_jar)
        new_size = os.path.getsize(compiler_jar)
        logger.info(
            "Minimized %s: %.1fMB -> %.1fMB (%d classes kept).",
            os.path.basename(compiler_jar),
            orig_size / 1e6, new_size / 1e6,
            sum(1 for e in selected if e.endswith(".class")),
        )
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


def _jar_create(out_jar, root_dir):
    """Create a (modular) jar from root_dir using the JDK `jar` tool."""
    jar_tool = "jar"
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = os.path.join(java_home, "bin", "jar")
        if os.path.exists(candidate):
            jar_tool = candidate
    subprocess.run([jar_tool, "--create", "--file", os.path.abspath(out_jar), "-C", root_dir, "."],
                   check=True)


def main():
    minimize()


if __name__ == "__main__":
    sys.exit(main())
