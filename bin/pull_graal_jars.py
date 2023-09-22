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
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry
from tqdm import tqdm

VERSION = "23.1.0"
COMPILER_JAR_URL = f"https://repo1.maven.org/maven2/org/graalvm/compiler/compiler/{VERSION}/compiler-{VERSION}.jar"
COMPILER_MANAGEMENT_JAR_URL = f"https://repo1.maven.org/maven2/org/graalvm/compiler/compiler-management/{VERSION}/compiler-management-{VERSION}.jar"
GRAAL_COLLECTIONS = f"https://repo1.maven.org/maven2/org/graalvm/sdk/collections/{VERSION}/collections-{VERSION}.jar"
GRAAL_SDK_JAR_URL = f"https://repo1.maven.org/maven2/org/graalvm/sdk/graal-sdk/{VERSION}/graal-sdk-{VERSION}.jar"
TRUFFLE_API_JAR_URL = f"https://repo1.maven.org/maven2/org/graalvm/truffle/truffle-api/{VERSION}/truffle-api-{VERSION}.jar"
TRUFFLE_COMPILER_JAR_URL = f"https://repo1.maven.org/maven2/org/graalvm/truffle/truffle-compiler/{VERSION}/truffle-compiler-{VERSION}.jar"
GRAAL_WORD = f"https://repo1.maven.org/maven2/org/graalvm/sdk/word/{VERSION}/word-{VERSION}.jar"
GRAAL_POLYGLOT = f"https://repo1.maven.org/maven2/org/graalvm/polyglot/polyglot/{VERSION}/polyglot-{VERSION}.jar"

# Define ANSI escape codes for colors
GREEN = '\033[92m'
CYAN = '\033[96m'
RESET = '\033[0m'

graal_jars_dir = os.path.join(os.getcwd(), 'graalJars')

if not os.path.exists(graal_jars_dir):
    print(f"Creating directory graalJars under {os.getcwd()}")
    os.mkdir(graal_jars_dir)

def create_session_with_retries(retries=5):
    session = requests.Session()
    retry_strategy = Retry(
        total=retries,
        backoff_factor=1,
        status_forcelist=[500, 502, 503, 504],
    )
    adapter = HTTPAdapter(max_retries=retry_strategy)
    session.mount("https://", adapter)
    return session

def download_jar_if_not_exists(jar_url, target_dir):
    jar_filename = os.path.basename(jar_url)
    target_path = os.path.join(target_dir, jar_filename)

    if not os.path.exists(target_path):
        print(f"Downloading jar file for {GREEN}{jar_filename}{RESET} to {CYAN}{target_dir}/{RESET}")
        session = create_session_with_retries()
        response = session.get(jar_url, stream=True)
        total_size = int(response.headers.get('content-length', 0))
        block_size = 1024  # 1 KB

        with open(target_path, 'wb') as jar_file, tqdm(
            desc=jar_filename,
            total=total_size,
            unit='B',
            unit_scale=True,
            unit_divisor=1024
        ) as progress_bar:
            for data in response.iter_content(block_size):
                jar_file.write(data)
                progress_bar.update(len(data))

print(f"Download Graal {VERSION} jars from {GREEN}https://repo1.maven.org/maven2/org/graalvm{RESET} ...")
download_jar_if_not_exists(COMPILER_JAR_URL, graal_jars_dir)
download_jar_if_not_exists(COMPILER_MANAGEMENT_JAR_URL, graal_jars_dir)
download_jar_if_not_exists(GRAAL_SDK_JAR_URL, graal_jars_dir)
download_jar_if_not_exists(TRUFFLE_API_JAR_URL, graal_jars_dir)
download_jar_if_not_exists(TRUFFLE_COMPILER_JAR_URL, graal_jars_dir)
download_jar_if_not_exists(GRAAL_COLLECTIONS, graal_jars_dir)
download_jar_if_not_exists(GRAAL_WORD, graal_jars_dir)
download_jar_if_not_exists(GRAAL_POLYGLOT, graal_jars_dir)
print("Download complete.")
