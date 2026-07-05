#!/usr/bin/env python3
#
# Vendors JVMCI as a TornadoVM-owned application module so TornadoVM keeps building and
# running on JDKs that have REMOVED JVMCI entirely (JDK 27+, openjdk/jdk#30834): no
# jdk.internal.vm.ci / jdk.graal.compiler platform modules and no -XX:+EnableJVMCI flag.
#
# TornadoVM (and the vendored Graal 23.1.0 in tornado.graal) is typed against the
# jdk.vm.ci.* SPI. Historically that came from the JDK's built-in jdk.internal.vm.ci
# module. This script repackages the jdk.vm.ci.* classes from a JDK 21 image into a plain
# application jar, KEEPING the module name `jdk.internal.vm.ci`, so it is a drop-in
# replacement resolvable from the regular --module-path with ZERO module-info edits (every
# TornadoVM module already `requires jdk.internal.vm.ci` by name, and tornado.graal
# `requires transitive jdk.internal.vm.ci`).
#
# Why JDK 21 as the source: Graal 23.1.0 and every TornadoVM reflection provider were
# compiled against the JDK-21 jvmci SPI. Freezing the interfaces at that exact shape means
# the per-release SPI drift that JDK 26 introduced (new abstract ResolvedJavaMethod
# .isDeclared(), ConstantPool.lookupConstant(int,boolean), etc.) never reaches our code.
#
# Recipe (proven in isolation before automating):
#   1. jimage extract the source JDK 21 runtime image and take the jdk.internal.vm.ci
#      module's jdk/vm/ci/** classes (~298 classes, all 14 jvmci packages incl. hotspot).
#   2. Drop the JDK-21 module-info.class and compile a fresh one that `exports` every
#      jvmci package (javac --patch-module jdk.internal.vm.ci=<classes>).
#   3. jar it and install to the local Maven repo as tornado.jvmci:jvmci:<ver>, wired into
#      the `jdk27` Maven profile as a provided-scope dependency.
#
# The bundled jdk.vm.ci.hotspot.* classes let everything COMPILE/link, but their native
# glue is gone on JDK 27 -- the reflection provider path (-Dtornado.jvmci.reflection) is
# the exclusive runnable path there.
#
# Env:
#   JVMCI_SOURCE_JDK  path to a JDK 21 home to extract jvmci from (default: the sdkman
#                     21.0.2-open candidate). JAVA_HOME is the build JDK (used for javac/jar).

import os
import shutil
import subprocess
import sys
import tempfile

VERSION = "21.0.2"
MODULE_NAME = "jdk.internal.vm.ci"
GROUP = "tornado.jvmci"
ARTIFACT = "jvmci"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
GRAAL_JARS_DIR = os.path.join(REPO_ROOT, "graalJars")

JVMCI_PACKAGES = [
    "jdk.vm.ci.aarch64", "jdk.vm.ci.amd64", "jdk.vm.ci.code", "jdk.vm.ci.code.site",
    "jdk.vm.ci.code.stack", "jdk.vm.ci.common", "jdk.vm.ci.hotspot",
    "jdk.vm.ci.hotspot.aarch64", "jdk.vm.ci.hotspot.amd64", "jdk.vm.ci.hotspot.riscv64",
    "jdk.vm.ci.meta", "jdk.vm.ci.riscv64", "jdk.vm.ci.runtime", "jdk.vm.ci.services",
]


def _build_java_home():
    jh = os.environ.get("JAVA_HOME")
    if not jh:
        sys.exit("build_jvmci_module: JAVA_HOME is not set")
    return jh


def _source_jdk():
    src = os.environ.get("JVMCI_SOURCE_JDK")
    if not src:
        src = os.path.expanduser("~/.sdkman/candidates/java/21.0.2-open")
    modules = os.path.join(src, "lib", "modules")
    if not os.path.exists(modules):
        sys.exit(f"build_jvmci_module: no runtime image at {modules}; set JVMCI_SOURCE_JDK to a JDK 21 home")
    return src


def _tool(name):
    return os.path.join(_build_java_home(), "bin", name)


def _source_tool(name, src):
    # jimage is image-version specific: JDK 27's jimage cannot read a JDK 21 image, so the
    # source JDK's own jimage must extract its runtime image.
    return os.path.join(src, "bin", name)


def _run(cmd, **kw):
    print("  $ " + " ".join(cmd))
    subprocess.run(cmd, check=True, **kw)


# Run `mvn install:install-file` outside the repo so the root pom's active
# <classifier>${platform}</classifier> (linux-amd64) is not applied, which would otherwise
# publish a classified artifact the reactor can't resolve.
def _neutral_cwd():
    return tempfile.gettempdir()


def _mvn():
    return shutil.which("mvn") or "mvn"


def build():
    src = _source_jdk()
    work = tempfile.mkdtemp(prefix="tornado-jvmci-")
    try:
        img = os.path.join(work, "img")
        _run([_source_tool("jimage", src), "extract", "--dir", img, os.path.join(src, "lib", "modules")])
        vmci = os.path.join(img, MODULE_NAME)
        if not os.path.isdir(os.path.join(vmci, "jdk", "vm", "ci")):
            sys.exit(f"build_jvmci_module: no jdk/vm/ci classes under {vmci}")

        classes = os.path.join(work, "classes")
        shutil.copytree(os.path.join(vmci, "jdk"), os.path.join(classes, "jdk"))
        stale_mi = os.path.join(classes, "module-info.class")
        if os.path.exists(stale_mi):
            os.remove(stale_mi)

        misrc = os.path.join(work, "misrc")
        os.makedirs(misrc)
        exports = "\n".join(f"    exports {p};" for p in JVMCI_PACKAGES)
        with open(os.path.join(misrc, "module-info.java"), "w") as f:
            f.write(
                f"module {MODULE_NAME} {{\n"
                "    requires java.base;\n"
                "    requires jdk.unsupported;\n"
                f"{exports}\n"
                "}\n"
            )
        _run([_tool("javac"), "-d", classes, "--patch-module", f"{MODULE_NAME}={classes}",
              os.path.join(misrc, "module-info.java")])

        jar = os.path.join(work, f"{ARTIFACT}-{VERSION}.jar")
        _run([_tool("jar"), "--create", "--file", jar, "-C", classes, "."])
        print(f"  built vendored {MODULE_NAME} module: {jar}")

        # Drop the jar into graalJars/ alongside the other vendored jars so the assembly can
        # ship it. It goes to share/java/jvmci (NOT share/java/graalJars) because a module
        # named jdk.internal.vm.ci must only be on the module-path on JVMCI-absent JDKs.
        os.makedirs(GRAAL_JARS_DIR, exist_ok=True)
        shipped = os.path.join(GRAAL_JARS_DIR, f"{ARTIFACT}-{VERSION}.jar")
        shutil.copyfile(jar, shipped)
        print(f"  staged for assembly: {shipped}")

        _run([_mvn(), "-q", "install:install-file", f"-Dfile={jar}",
              f"-DgroupId={GROUP}", f"-DartifactId={ARTIFACT}", f"-Dversion={VERSION}",
              "-Dpackaging=jar"], cwd=_neutral_cwd())
        print(f"  installed {GROUP}:{ARTIFACT}:{VERSION} to the local Maven repository")
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    build()
