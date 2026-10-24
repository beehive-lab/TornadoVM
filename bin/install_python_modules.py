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
import site
import subprocess
import sys

# The modules the build and installer scripts import: requests, tqdm and urllib3 (pull_graal_jars.py,
# tornadovm-installer) and packaging (tornadovm-installer).
REQUIRED_MODULES = ["requests", "tqdm", "urllib3", "packaging"]


def _missing(modules):
    missing = []
    for module in modules:
        try:
            importlib.import_module(module)
        except ImportError:
            missing.append(module)
    return missing


def check_python_dependencies():
    """
    Check the required dependencies for the installation of TornadoVM, installing missing ones with
    pip. pip's output is shown: a Python that refuses user installs (PEP 668, "externally managed",
    as on Ubuntu 24.04 and Debian 12) says so, and the build stops with what to run instead.
    """
    missing = _missing(REQUIRED_MODULES)
    if not missing:
        return 0
    print("Installing the Python modules TornadoVM's build needs: " + " ".join(missing), flush=True)
    # Into the active virtual environment if there is one, else the user's site-packages.
    in_venv = sys.prefix != sys.base_prefix
    subprocess.call([sys.executable, "-m", "pip", "install"] + ([] if in_venv else ["--user"]) + missing)
    if not in_venv:
        # A user site-packages directory that did not exist when Python started is not on sys.path,
        # so a module pip just installed there is not found until it is added.
        site.addsitedir(site.getusersitepackages())
    importlib.invalidate_caches()
    still_missing = _missing(missing)
    if still_missing:
        names = " ".join(still_missing)
        sys.exit("\n[ERROR] TornadoVM's build needs the Python modules: " + names + "\n"
                 "pip could not install them (see its message above). Install them with one of:\n"
                 "  python3 -m venv ~/.venv-tornadovm && . ~/.venv-tornadovm/bin/activate && pip install " + names + "\n"
                 "  sudo apt install " + " ".join("python3-" + m for m in still_missing) + "\n"
                 "  python3 -m pip install --user --break-system-packages " + names + "\n")
    return 0
