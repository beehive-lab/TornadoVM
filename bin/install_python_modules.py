#!/usr/bin/env python3

#
# Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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

import importlib
import subprocess
import sys

# Modules the build and installer tooling actually imports, with the pip
# distribution that provides each. Keep this list in step with the imports:
# a module listed here that nothing imports blocks the build on every machine
# where pip cannot install it, for no benefit.
#
#   requests, tqdm, urllib3 -> bin/pull_graal_jars.py
#   packaging               -> bin/tornadovm-installer ("from packaging import version")
REQUIRED_MODULES = (
    ("requests", "requests"),
    ("tqdm", "tqdm"),
    ("urllib3", "urllib3"),
    ("packaging", "packaging"),
)


def _install_hint(module, distribution, pip_output):
    """
    Explain what to do, including the case pip refuses outright.
    """
    lines = [
        "[ERROR] TornadoVM's build tooling needs the Python module '%s' and it could not be installed automatically." % module,
    ]
    if pip_output:
        lines.append("")
        lines.append("pip reported:")
        lines.extend("    " + line for line in pip_output.strip().splitlines())
    lines.append("")
    if "externally-managed-environment" in (pip_output or ""):
        # PEP 668. The default on Debian 12+, Ubuntu 23.04+ and Fedora 38+, where a
        # bare `pip3 install` into the system interpreter is refused by design.
        lines.append("This interpreter is externally managed (PEP 668), so install it one of these ways:")
        lines.append("    sudo apt install python3-%s        # or the equivalent for your distribution" % distribution)
        lines.append("    python3 -m venv ~/.tornadovm-venv && source ~/.tornadovm-venv/bin/activate")
        lines.append("    pip3 install --user %s" % distribution)
    else:
        lines.append("Install it with:")
        lines.append("    pip3 install %s" % distribution)
    return "\n".join(lines)


def check_python_dependencies():
    """
    Check the required dependencies for the installation of TornadoVM.

    Missing modules are installed with pip when that is possible. When it is not,
    the reason pip gave is reported rather than discarded -- a bare ImportError
    traceback gives no clue that the real problem is an externally-managed
    interpreter.
    """
    for module, distribution in REQUIRED_MODULES:
        try:
            importlib.import_module(module)
            continue
        except ImportError:
            pass

        completed = subprocess.run(
            [sys.executable, "-m", "pip", "install", distribution],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )

        try:
            importlib.invalidate_caches()
            importlib.import_module(module)
        except ImportError:
            print(_install_hint(module, distribution, completed.stdout), file=sys.stderr)
            sys.exit(1)

    return 0
