#!/usr/bin/env python3
#
# Vendors the frozen Graal compiler as a relocated application module `tornado.graal`.
#
# TornadoVM ships Graal 23.1.0 to drive its own GPU compilation pipeline. Historically
# this was the JDK module `jdk.internal.vm.compiler`, injected via `--upgrade-module-path`
# because that name is a built-in JDK module that shadows anything on the regular
# `--module-path`. To drop `--upgrade-module-path` we relocate the compiler off the
# `jdk.*` namespace (`org.graalvm.compiler.*` -> `tornado.graal.compiler.*`) so it becomes
# a normal module resolvable from `--module-path`.
#
# Recipe (all steps proven in isolation before automating):
#   1. maven-shade relocate classes + META-INF/services (module-info.class excluded).
#   2. jdeps --generate-module-info to rebuild requires/exports/provides from the
#      relocated service files.
#   3. Inject the `uses` clauses jdeps never emits (Graal calls ServiceLoader.load for
#      them internally; without `uses` discovery returns empty).
#   4. Drop `provides jdk.vm.ci.services.JVMCIServiceLocator` — it makes the renamed
#      module unresolvable (its service package is qualified-exported by jvmci only to
#      `jdk.internal.vm.compiler` by name, and --add-exports cannot satisfy the
#      resolution-time provides check). Safe: it registers Graal as the HotSpot JIT,
#      which TornadoVM never uses (+EnableJVMCI without +UseJVMCICompiler).
#   5. Compile + inject the module-info, drop the now-orphan services file.
#   6. Emit graalJars/tornado-graal-<ver>.jar and install it to the local Maven repo as
#      tornado.graal:tornado-graal:<ver> so the reactor can compile against it.
#
# Consumed by bin/pull_graal_jars.py after the raw Graal jars are fetched.

import os
import re
import shutil
import subprocess
import sys
import tempfile

VERSION = "23.1.0"
MODULE_NAME = "tornado.graal"
ARTIFACT = f"tornado-graal-{VERSION}.jar"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
GRAAL_JARS_DIR = os.path.join(REPO_ROOT, "graalJars")
RELOCATE_POM = os.path.join(SCRIPT_DIR, "graal-relocate", "pom.xml")

# The service interface types Graal loads via ServiceLoader from inside the module.
# jdeps regenerates `provides` from META-INF/services but never emits `uses`; without a
# `uses` clause a modular ServiceLoader.load returns nothing. Derived from the original
# jdk.internal.vm.compiler descriptor and relocated below.
DROPPED_PROVIDES_SERVICE = "jdk.vm.ci.services.JVMCIServiceLocator"
ORPHAN_SERVICES_FILE = f"META-INF/services/{DROPPED_PROVIDES_SERVICE}"


def _java_home():
    jh = os.environ.get("JAVA_HOME")
    if not jh:
        sys.exit("build_graal_module: JAVA_HOME is not set")
    return jh


def _tool(name):
    return os.path.join(_java_home(), "bin", name)


def _run(cmd, **kw):
    print("  $ " + " ".join(cmd))
    subprocess.run(cmd, check=True, **kw)


# Neutral cwd for `mvn install:install-file`: run outside the repo so the root pom's
# active <classifier>${platform}</classifier> property (linux-amd64) is not applied,
# which would otherwise publish a classified artifact the reactor can't resolve.
def _neutral_cwd():
    return tempfile.gettempdir()


def _capture(cmd):
    return subprocess.run(cmd, check=True, stdout=subprocess.PIPE, text=True).stdout


def _mvn():
    return shutil.which("mvn") or "mvn"


def _dep_jar(*rel):
    p = os.path.join(GRAAL_JARS_DIR, *rel)
    if not os.path.exists(p):
        sys.exit(f"build_graal_module: missing dependency jar {p}")
    return p


def _module_path_deps():
    return os.pathsep.join([
        _dep_jar(f"word-{VERSION}.jar"),
        _dep_jar(f"collections-{VERSION}.jar"),
        _dep_jar(f"truffle-compiler-{VERSION}.jar"),
    ])


def _install_file(jar, group, artifact):
    _run([_mvn(), "-q", "install:install-file",
          f"-Dfile={jar}", f"-DgroupId={group}", f"-DartifactId={artifact}",
          f"-Dversion={VERSION}", "-Dpackaging=jar"], cwd=_neutral_cwd())


def _relocated_uses_clauses(compiler_jar):
    """Extract `uses` from the original compiler descriptor, relocated to tornado.graal."""
    out = _capture([_tool("jar"), "--describe-module", "--file", compiler_jar])
    clauses = []
    for line in out.splitlines():
        line = line.strip()
        if line.startswith("uses "):
            svc = line[len("uses "):].strip()
            svc = svc.replace("org.graalvm.compiler", "tornado.graal.compiler")
            clauses.append(f"    uses {svc};")
    return clauses


def _build_module_info(generated_mi, compiler_jar):
    text = generated_mi.read_text() if hasattr(generated_mi, "read_text") else open(generated_mi).read()
    # (4) drop the JVMCIServiceLocator provides block (spans the `with ...;` list)
    text = re.sub(r"\n\s*provides\s+jdk\.vm\.ci\.services\.JVMCIServiceLocator\s+with[^;]*;", "", text)
    # (3) inject relocated `uses` clauses before the closing brace
    uses = "\n".join(_relocated_uses_clauses(compiler_jar))
    idx = text.rstrip().rfind("}")
    text = text[:idx] + uses + "\n}\n"
    return text


def build():
    java = _java_home()
    compiler_jar = _dep_jar(f"compiler-{VERSION}.jar")
    print(f"build_graal_module: relocating Graal {VERSION} -> module {MODULE_NAME}")

    # Make the raw compiler jar resolvable for the offline shade build.
    _install_file(compiler_jar, "org.graalvm.compiler", "compiler")

    with tempfile.TemporaryDirectory() as work:
        # (1) shade: relocate classes + services
        _run([_mvn(), "-q", "-o", "-f", RELOCATE_POM, "package"])
        shaded = os.path.join(SCRIPT_DIR, "graal-relocate", "target", ARTIFACT)
        if not os.path.exists(shaded):
            sys.exit(f"build_graal_module: shade did not produce {shaded}")
        staged = os.path.join(work, ARTIFACT)
        shutil.copy(shaded, staged)

        deps = _module_path_deps()

        # (2) jdeps generate module-info from the relocated services
        mi_root = os.path.join(work, "modout")
        os.makedirs(mi_root, exist_ok=True)
        _run([_tool("jdeps"), "--generate-module-info", mi_root,
              "--module-path", deps, "--add-modules", "jdk.internal.vm.ci", staged])
        mi_java = os.path.join(mi_root, MODULE_NAME, "module-info.java")

        # (3)+(4) inject uses, drop jvmci provides
        mi_text = _build_module_info(mi_java, compiler_jar)
        with open(mi_java, "w") as f:
            f.write(mi_text)

        # (5) compile module-info against the relocated classes, inject, drop orphan service
        _run([_tool("javac"),
              "--module-path", deps, "--add-modules", "jdk.internal.vm.ci",
              "--patch-module", f"{MODULE_NAME}={staged}",
              "-d", os.path.join(mi_root, MODULE_NAME), mi_java])
        _run([_tool("jar"), "uf", staged,
              "-C", os.path.join(mi_root, MODULE_NAME), "module-info.class"])
        subprocess.run([_tool("jar"), "--delete", "--file", staged, ORPHAN_SERVICES_FILE],
                       stderr=subprocess.DEVNULL)

        # sanity: module must resolve as tornado.graal
        desc = _capture([_tool("jar"), "--describe-module", "--file", staged])
        if not desc.splitlines()[0].startswith(MODULE_NAME):
            sys.exit(f"build_graal_module: bad module descriptor:\n{desc}")

        # (6) publish: replace compiler jar in graalJars, install to local repo
        out = os.path.join(GRAAL_JARS_DIR, ARTIFACT)
        shutil.copy(staged, out)
        try:
            os.remove(compiler_jar)  # relocated jar supersedes the raw compiler jar
        except FileNotFoundError:
            pass
        _install_file(out, "tornado.graal", "tornado-graal")

    print(f"build_graal_module: wrote {os.path.join('graalJars', ARTIFACT)} and installed "
          f"tornado.graal:tornado-graal:{VERSION}")


if __name__ == "__main__":
    build()
