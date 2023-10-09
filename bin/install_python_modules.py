#!/usr/bin/env python3

#
# This file is part of Tornado: A heterogeneous programming framework:
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2023, APT Group, Department of Computer Science,
# School of Engineering, The University of Manchester. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#

import subprocess


def check_python_dependencies():
    try:
        import requests
    except:
        subprocess.call(["pip3", "install", "requests"], stderr=subprocess.DEVNULL)
    try:
        import tqdm
    except:
        subprocess.call(["pip3", "install", "tqdm"], stderr=subprocess.DEVNULL)

    try:
        import urllib3
    except:
        subprocess.call(["pip3", "install", "urllib3"], stderr=subprocess.DEVNULL)
    try:
        import wget
    except:
        print("Installing dependencies")
        subprocess.call(["pip3", "install", "wget"], stderr=subprocess.DEVNULL)
