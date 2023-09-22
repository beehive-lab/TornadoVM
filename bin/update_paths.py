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

import os
import subprocess

# Update PATHS in Tornado

# Determine the 'file' (You can modify this part accordingly)
tornado_sdk_dir = 'dist/tornado-sdk/'
files_in_sdk_dir = os.listdir(tornado_sdk_dir)
if files_in_sdk_dir:
    file = files_in_sdk_dir[0]
else:
    raise FileNotFoundError("No files found in 'dist/tornado-sdk/' directory")

print("\n########################################################## ")
print("\x1b[32mTornado build success\x1b[39m")
print(f"Updating PATH and TORNADO_SDK to {file}")

# Change to the 'bin/' directory
os.chdir('bin/')

print(f"Binaries: {os.getcwd()}")
commit = subprocess.check_output(['git', 'rev-parse', '--short', 'HEAD']).decode('utf-8').strip()
print(f"Commit  : {commit}")

# Unlink or remove 'bin' and 'sdk' if they exist
for symlink in ['bin', 'sdk']:
    if os.path.islink(symlink):
        os.unlink(symlink)
    else:
        # Windows cleanup - Mingw copies files during `ln`
        os.rmdir(symlink)

# Change back to the parent directory
os.chdir('..')

# Create symbolic links 'bin' and 'sdk'
os.symlink(os.path.join(os.getcwd(), tornado_sdk_dir, file, 'bin/'), 'bin/bin')
os.symlink(os.path.join(os.getcwd(), tornado_sdk_dir, file), 'bin/sdk')

print("########################################################## ")
