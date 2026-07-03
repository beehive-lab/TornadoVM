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

import logging
import os
import requests
from requests.adapters import HTTPAdapter
from tqdm import tqdm
from urllib3.util.retry import Retry

# Constants
TARGET_DIR = "graalJars"
VERSION = "23.1.0"
BASE_URL = "https://repo1.maven.org/maven2/org/graalvm"
# Bare minimum Graal modules TornadoVM needs to compile and run kernels.
# The `jdk.internal.vm.compiler` module (compiler jar) transitively requires only
# org.graalvm.word, org.graalvm.collections and org.graalvm.truffle.compiler; its
# `uses` of Truffle runtime/polyglot services are optional (ServiceLoader) and never
# exercised by TornadoVM's GPU pipeline. So truffle-api (16MB), polyglot (935KB),
# graal-sdk (requires the absent org.graalvm.nativeimage) and compiler-management
# are dead weight and are intentionally NOT downloaded/shipped.
GRAAL_JARS = [
    f"compiler/compiler/{VERSION}/compiler-{VERSION}.jar",
    f"truffle/truffle-compiler/{VERSION}/truffle-compiler-{VERSION}.jar",
    f"sdk/collections/{VERSION}/collections-{VERSION}.jar",
    f"sdk/word/{VERSION}/word-{VERSION}.jar",
]

# Define ANSI escape codes for colors
GREEN = "\033[92m"
CYAN = "\033[96m"
RESET = "\033[0m"

# Initialize logger
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def create_session_with_retries(retries=5):
    """
    Create an HTTP session with retry capabilities.

    Args:
        retries (int): The number of retries for failed requests.

    Returns:
        requests.Session: A configured HTTP session.
    """
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
    """
    Download a JAR file from the specified URL to the target directory if it does not already exist.

    Args:
        jar_url (str): The URL of the JAR file to download.
        target_dir (str): The directory where the JAR file should be saved.
    """
    jar_filename = os.path.basename(jar_url)
    target_path = os.path.join(target_dir, jar_filename)

    if os.path.exists(target_path):
        logger.info(
            f"{GREEN}Skipping download of {jar_filename}{RESET}, jar file already exists."
        )
    else:
        logger.info(
            f"Downloading {GREEN} {jar_filename} {RESET} to {CYAN} {target_dir} {RESET}"
        )
        session = create_session_with_retries()
        response = session.get(jar_url, stream=True)
        total_size = int(response.headers.get("content-length", 0))
        block_size = 1024  # 1 KB

        with open(target_path, "wb") as jar_file, tqdm(
                desc=jar_filename,
                total=total_size,
                unit="B",
                unit_scale=True,
                unit_divisor=1024,
        ) as progress_bar:
            for data in response.iter_content(block_size):
                jar_file.write(data)
                progress_bar.update(len(data))


def main():
    """
    Main function to download GraalVM JAR files.
    """
    if not os.path.exists(TARGET_DIR):
        os.mkdir(TARGET_DIR)

    logger.info(f"Downloading GraalVM {GREEN} {VERSION} {RESET} JAR files...")

    for jar_url in GRAAL_JARS:
        download_jar_if_not_exists(f"{BASE_URL}/{jar_url}", TARGET_DIR)

    logger.info("Download complete.")

    # Relocate the Graal compiler off the jdk.* namespace into the vendored module
    # `tornado.graal` so it can live on the regular --module-path (no upgrade-module-path).
    # This replaces compiler-<ver>.jar with tornado-graal-<ver>.jar and installs the
    # latter to the local Maven repo for the reactor to compile against.
    relocated = os.path.join(TARGET_DIR, f"tornado-graal-{VERSION}.jar")
    if os.path.exists(relocated):
        logger.info(f"Graal module {CYAN}tornado.graal{RESET} already relocated; skipping.")
    else:
        logger.info(f"Relocating Graal into the {CYAN}tornado.graal{RESET} module...")
        import build_graal_module
        build_graal_module.build()


if __name__ == "__main__":
    main()
