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
import subprocess
import zipfile

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


def unusable_relocated_jar_reason(jar_path):
    """
    Check whether an already-staged tornado-graal jar can be reused by THIS build.

    Unlike the raw Graal jars above, the relocated module is a per-JDK build artifact
    rather than a portable download: its module-info is compiled by whichever JDK builds
    it. A jar seeded from a shared cache, or left over from a build under a different JDK,
    can therefore be present and still be wrong here, and both failure modes surface long
    after this point -- the Maven build succeeds (it compiles against the copy in the local
    repository) and only the first `tornado` launch dies. So validate the two invariants
    the module has to satisfy:

      1. The build JDK must be able to read the module descriptor. A module-info compiled
         by a newer JDK fails with InvalidModuleDescriptorException "Unsupported
         major.minor version" while the boot layer is being created.
      2. The relocation must be complete. Any surviving org.graalvm.* package splits with
         a JDK-bundled jdk.internal.vm.compiler (GraalVM JDKs ship one) and kills the boot
         layer with LayerInstantiationException.

    Returns a human-readable reason to rebuild, or None if the jar is good to reuse.
    """
    java_home = os.environ.get("JAVA_HOME")
    if not java_home:
        return "JAVA_HOME is not set"

    probe = subprocess.run([os.path.join(java_home, "bin", "jar"), "--describe-module", "--file", jar_path],
                           stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
    if probe.returncode != 0:
        # First stderr line carries the exception; the rest is a stack trace.
        detail = next((ln.strip() for ln in probe.stderr.splitlines() if ln.strip()), "unreadable module descriptor")
        return f"not readable by the build JDK ({detail})"

    with zipfile.ZipFile(jar_path) as staged:
        unrelocated = sorted({os.path.dirname(name).replace("/", ".")
                              for name in staged.namelist()
                              if name.startswith("org/graalvm/") and name.endswith(".class")})
    if unrelocated:
        return f"still carries un-relocated packages ({', '.join(unrelocated[:3])}...)"

    return None


def main(jdk=None):
    """
    Main function to download GraalVM JAR files.
    """
    if not os.path.exists(TARGET_DIR):
        os.mkdir(TARGET_DIR)

    logger.info(f"Downloading GraalVM {GREEN} {VERSION} {RESET} JAR files...")

    for jar_url in GRAAL_JARS:
        download_jar_if_not_exists(f"{BASE_URL}/{jar_url}", TARGET_DIR)

    logger.info("Download complete.")

    # Build and stage the vendored jvmci module for JDKs whose build patches or replaces
    # jdk.internal.vm.ci (jdk25/26 patch-module it; jdk27 has no platform jvmci at all).
    # build_jvmci_module stages graalJars/jvmci-<ver>.jar (shipped by the assembly to
    # share/java/jvmci) and installs tornado.jvmci:jvmci to the local Maven repository.
    #
    # This MUST run before the Graal-relocation step below: on jdk27 the platform has no
    # jdk.internal.vm.ci module at all, so build_graal_module's jdeps/javac calls can only
    # resolve it from this vendored jar (staged here) rather than via --add-modules against
    # the running JDK's own system modules.
    if jdk is not None and jdk != "jdk21":
        logger.info(f"Building/staging vendored {CYAN}jdk.internal.vm.ci{RESET} module for {jdk}...")
        import build_jvmci_module
        build_jvmci_module.build(jdk=jdk)

    # Relocate the Graal compiler off the jdk.* namespace into the vendored module
    # `tornado.graal` so it can live on the regular --module-path (no upgrade-module-path).
    # This replaces compiler-<ver>.jar with tornado-graal-<ver>.jar and installs the
    # latter to the local Maven repo for the reactor to compile against.
    relocated = os.path.join(TARGET_DIR, f"tornado-graal-{VERSION}.jar")
    reason = unusable_relocated_jar_reason(relocated) if os.path.exists(relocated) else None
    if os.path.exists(relocated) and reason is None:
        logger.info(f"Graal module {CYAN}tornado.graal{RESET} already relocated; skipping.")
    else:
        if reason:
            logger.info(f"Discarding the staged {CYAN}tornado.graal{RESET} module: {reason}")
        logger.info(f"Relocating Graal into the {CYAN}tornado.graal{RESET} module...")
        import build_graal_module
        build_graal_module.build(jdk=jdk)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Download/stage the vendored Graal and jvmci jars.")
    parser.add_argument("--jdk", default=None,
                        help="Target JDK profile (jdk21|jdk25|jdk26|jdk27); jdk25+ also builds the vendored jvmci module")
    args = parser.parse_args()
    main(jdk=args.jdk)
