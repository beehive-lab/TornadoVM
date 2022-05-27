#!/usr/bin/env python3

# This file is part of Tornado: A heterogeneous programming framework:
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2022, APT Group, Department of Computer Science,
# The University of Manchester. All rights reserved.
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

import os

if __name__ == "__main__":
    TORNADO_SDK = os.getenv('TORNADO_SDK')
    tornadoBackendFilePath = TORNADO_SDK + "/etc/tornado.backend"
    with open(tornadoBackendFilePath, 'r') as tornadoBackendFile:
        lines = tornadoBackendFile.read().splitlines()
        for line in lines:
            if "tornado.backends" in line:
                backends = line.replace("tornado.backends=", "").replace("-backend", "")
                print("backends=" + backends)
                