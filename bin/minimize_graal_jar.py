#!/usr/bin/env python3

#
# Copyright (c) 2013-2026, APT Group, Department of Computer Science,
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
Shrink the relocated tornado.graal jar to the classes TornadoVM needs to compile and run kernels.

Called by bin/build_graal_module.py on the shaded jar, before its module descriptor is generated, so
the descriptor (exports, provides, uses) describes only what is left.

The shaded jar carries the whole Graal 23.1.0 compiler (HotSpot JIT, Truffle, every ISA) plus the
GraalVM word, collections and truffle-compiler APIs; TornadoVM drives only its own GPU sketch and
lowering pipeline. Only the parts TornadoVM cannot use are trimmed: classes in a ``hotspot``,
``truffle``, ``amd64``, ``aarch64`` or ``riscv64`` package. Everything ISA-neutral (graph nodes,
plugins, phases, LIR) is kept whole, because Graal's standard plugins can create any IR node from
ordinary Java code and no trace covers them all.

Inside the trimmed packages, the keep-list in ``bin/graal-compiler-keep.txt`` names the classes that
are still used, in relocated names (``tornado/graal/...``):

  * every class loaded from tornado.graal across the TornadoVM unit-test suites
    (``-Xlog:class+load``), which also captures the reflective and ServiceLoader closure;
  * every tornado.graal type referenced by TornadoVM's own class files (all backends), the static
    floor for code paths the tests do not exercise;
  * the nested classes of all of the above.

On top of the list, the trim keeps every supertype of a kept class (a class cannot load without
them), every non-class resource, and prunes META-INF/services so no provider names a removed class.

To regenerate the list, run the unit tests with
``tornado-test -J"-Xlog:class+load=info:file=<dir>/%p.log"`` on every backend and take the union of
the loaded ``tornado.graal.*`` classes with the current list.
"""

import hashlib
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile

KEEP_LIST = os.path.join(os.path.dirname(os.path.abspath(__file__)), "graal-compiler-keep.txt")
SERVICES_DIR = "META-INF/services/"
# Packages whose classes are kept only when the keep-list names them: Graal's HotSpot JIT, Truffle and
# CPU ISA support. Everything else is kept whole.
TRIMMABLE_PACKAGE = re.compile(r"/(hotspot|truffle|amd64|aarch64|riscv64)(/|$)")
# Records the keep-list a jar was trimmed with, so a jar trimmed with another list is rebuilt.
MARKER = "META-INF/tornado-graal-keep-list.sha256"


def keep_list_digest():
    with open(KEEP_LIST, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def _load_keep_set():
    with open(KEEP_LIST, "r") as f:
        return {line.strip() for line in f if line.strip() and not line.startswith("#")}


def _supertypes(class_bytes):
    """Internal names of the superclass and direct interfaces declared in a class file."""
    data = class_bytes
    count = struct.unpack(">H", data[8:10])[0]
    pool = [None]
    i = 10
    index = 1
    while index < count:
        tag = data[i]
        if tag == 1:
            length = struct.unpack(">H", data[i + 1:i + 3])[0]
            pool.append(data[i + 3:i + 3 + length])
            i += 3 + length
        elif tag == 7:
            pool.append(struct.unpack(">H", data[i + 1:i + 3])[0])
            i += 3
        elif tag in (5, 6):
            pool.extend([None, None])
            i += 9
            index += 1
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pool.append(None)
            i += 5
        elif tag in (8, 16, 19, 20):
            pool.append(None)
            i += 3
        elif tag == 15:
            pool.append(None)
            i += 4
        else:
            raise ValueError(f"unknown constant-pool tag {tag}")
        index += 1
    i += 4  # access flags, this_class
    super_index = struct.unpack(">H", data[i:i + 2])[0]
    interfaces = struct.unpack(">H", data[i + 2:i + 4])[0]
    indices = [super_index] + [struct.unpack(">H", data[i + 4 + 2 * k:i + 6 + 2 * k])[0] for k in range(interfaces)]
    return [pool[pool[k]].decode() for k in indices if k]


def _service_class(line):
    line = line.split("#", 1)[0].strip()
    return line.replace(".", "/") + ".class" if line else None


def minimize(jar_path):
    """Trim jar_path in place to the keep-list. Returns (classes_before, classes_after)."""
    keep = _load_keep_set()
    tmp_dir = tempfile.mkdtemp(prefix="graal-min-")
    try:
        with zipfile.ZipFile(jar_path, "r") as zf:
            entries = [e for e in zf.namelist() if not e.endswith("/")]
            classes = {e for e in entries if e.endswith(".class") and e != "module-info.class"}
            selected = {c for c in classes if c in keep or not TRIMMABLE_PACKAGE.search(c.rsplit("/", 1)[0])}

            # Close over supertypes: a kept class cannot be loaded without its superclass and interfaces.
            work = list(selected)
            while work:
                for parent in _supertypes(zf.read(work.pop())):
                    entry = parent + ".class"
                    if entry in classes and entry not in selected:
                        selected.add(entry)
                        work.append(entry)

            for entry in entries:
                if entry.startswith(SERVICES_DIR):
                    service = entry[len(SERVICES_DIR):].replace(".", "/") + ".class"
                    if service in classes and service not in selected:
                        continue  # the service interface itself is gone
                    providers = [line for line in zf.read(entry).decode().splitlines()
                                 if _service_class(line) is None or _service_class(line) in selected]
                    if not any(_service_class(line) for line in providers):
                        continue
                    path = os.path.join(tmp_dir, entry)
                    os.makedirs(os.path.dirname(path), exist_ok=True)
                    with open(path, "w") as f:
                        f.write("\n".join(providers) + "\n")
                elif entry.endswith(".class") and entry != "module-info.class":
                    if entry in selected:
                        zf.extract(entry, tmp_dir)
                else:
                    zf.extract(entry, tmp_dir)

        marker = os.path.join(tmp_dir, MARKER)
        os.makedirs(os.path.dirname(marker), exist_ok=True)
        with open(marker, "w") as f:
            f.write(keep_list_digest() + "\n")

        new_jar = jar_path + ".min"
        _jar_create(new_jar, tmp_dir)
        shutil.move(new_jar, jar_path)
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)
    return len(classes), len(selected)


def _jar_create(out_jar, root_dir):
    """Create a jar from root_dir using the JDK `jar` tool."""
    jar_tool = "jar"
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = os.path.join(java_home, "bin", "jar")
        if os.path.exists(candidate):
            jar_tool = candidate
    subprocess.run([jar_tool, "--create", "--file", os.path.abspath(out_jar), "-C", root_dir, "."], check=True)


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: minimize_graal_jar.py <tornado-graal jar>")
    before, after = minimize(sys.argv[1])
    print(f"kept {after} of {before} classes")


if __name__ == "__main__":
    main()
