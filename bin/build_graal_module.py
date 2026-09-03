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

# Lowest JDK each profile's SDK has to run on. The module descriptor compiled below must be
# emitted at this release and no higher: a module-info carries the class-file version of the
# JDK that compiled it, and the JVM reads descriptors under the ordinary backward-compatibility
# rule, so one built by (say) JDK 27 is unreadable on anything older -- the SDK silently ends up
# pinned to its own build host ("InvalidModuleDescriptorException: Unsupported major.minor
# version 71.0"). Everything else in the jar is Graal 23.1.0's own bytecode, which is already
# old enough to load anywhere.
DEFAULT_RELEASE = 22
JDK_FLOOR = {"jdk21": 21}


def _release_for(jdk):
    return JDK_FLOOR.get(jdk, DEFAULT_RELEASE)


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
    """argv prefix that invokes the repo's mvnw wrapper, falling back to `mvn` on PATH.

    The wrapper is preferred so the build doesn't depend on a system Maven install; on
    Windows a .cmd file isn't directly executable via CreateProcess, so it's launched
    through `cmd /c`.
    """
    wrapper = os.path.join(REPO_ROOT, "mvnw.cmd" if os.name == "nt" else "mvnw")
    if os.path.exists(wrapper):
        return ["cmd", "/c", wrapper] if os.name == "nt" else [wrapper]
    mvn = shutil.which("mvn")
    if not mvn:
        sys.exit("build_graal_module: no mvnw wrapper found at repo root and no `mvn` on PATH")
    return [mvn]


def _dep_jar(*rel):
    p = os.path.join(GRAAL_JARS_DIR, *rel)
    if not os.path.exists(p):
        sys.exit(f"build_graal_module: missing dependency jar {p}")
    return p


def _build_jdk_has_jvmci():
    """Whether the JDK running this build still ships the jdk.internal.vm.ci platform module.

    Asked of the build JDK rather than derived from the target profile: which JDKs the SDK
    RUNS on is a property of the artifact, while where jdeps/javac find jdk.vm.ci.* while
    producing it is a property of the machine doing the building. Those are independent once
    a profile targets a floor (jdk22plus) instead of one exact release, and conflating them
    means a build host newer than the target cannot resolve the module at all.
    """
    out = _capture([_tool("java"), "--list-modules"])
    return any(line.startswith("jdk.internal.vm.ci@") for line in out.splitlines())


def _module_path_deps(jdk=None):
    deps = [
        _dep_jar(f"word-{VERSION}.jar"),
        _dep_jar(f"collections-{VERSION}.jar"),
        _dep_jar(f"truffle-compiler-{VERSION}.jar"),
    ]
    if not _build_jdk_has_jvmci():
        # JDK 27 dropped jdk.internal.vm.ci from the platform entirely, so the jdeps/javac calls
        # below cannot resolve it via --add-modules against the system modules. pull_graal_jars.py
        # stages the vendored replacement (built by build_jvmci_module) before calling us,
        # precisely so it is available here. On a build JDK that still ships the platform module
        # it is deliberately left off the module path -- a same-named module there is shadowed by
        # the system one anyway, and listing both is just noise.
        import build_jvmci_module
        deps.append(_dep_jar(f"{build_jvmci_module.ARTIFACT}-{build_jvmci_module.VERSION}.jar"))
    return os.pathsep.join(deps)


def _install_file(jar, group, artifact):
    _run(_mvn() + ["-q", "install:install-file",
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


def build(jdk=None):
    java = _java_home()
    compiler_jar = _dep_jar(f"compiler-{VERSION}.jar")
    print(f"build_graal_module: relocating Graal {VERSION} -> module {MODULE_NAME}")

    # Publish the raw compiler jar to the local repo so the shade build can depend on it
    # (it is not on Maven Central under this coordinate). A release version present in the
    # local repo resolves without any remote lookup, so this needs no offline flag.
    _install_file(compiler_jar, "org.graalvm.compiler", "compiler")

    with tempfile.TemporaryDirectory() as work:
        # (1) shade: relocate classes + services.
        # Deliberately NOT run with -o: this is the first Maven build in a clean checkout,
        # so on CI the local repo is empty and offline mode cannot resolve even the default
        # lifecycle plugins (process-resources binds maven-resources-plugin, which is what
        # broke the runners). Online resolution still hits the local repo first.
        _run(_mvn() + ["-q", "-f", RELOCATE_POM, "package"])
        shaded = os.path.join(SCRIPT_DIR, "graal-relocate", "target", ARTIFACT)
        if not os.path.exists(shaded):
            sys.exit(f"build_graal_module: shade did not produce {shaded}")
        staged = os.path.join(work, ARTIFACT)
        shutil.copy(shaded, staged)

        deps = _module_path_deps(jdk)

        # (2) jdeps generate module-info from the relocated services.
        # --ignore-missing-deps is required from jdk25 on: those JDKs still ship a platform
        # jdk.internal.vm.ci, but it is a LATER jvmci than the JDK-21 SPI Graal 23.1.0 was
        # compiled against (jdk.vm.ci.code.RegisterArray, jdk.vm.ci.common.NativeImageReinitialize
        # and jdk.vm.ci.hotspot.HotSpotJVMCICompilerFactory are all gone), so jdeps reports the
        # dangling references as missing deps and exits 1. Those types sit exclusively on Graal's
        # HotSpot-JIT paths, which TornadoVM never enters, and ignoring them changes nothing about
        # the descriptor: the module-info generated here on jdk25 is byte-identical to the jdk21
        # one, and the flag is a no-op on jdk21/jdk27 (jdk27 resolves the vendored JDK-21 jvmci
        # staged on the module path above, so nothing is missing there in the first place).
        mi_root = os.path.join(work, "modout")
        os.makedirs(mi_root, exist_ok=True)
        _run([_tool("jdeps"), "--ignore-missing-deps", "--generate-module-info", mi_root,
              "--module-path", deps, "--add-modules", "jdk.internal.vm.ci", staged])
        mi_java = os.path.join(mi_root, MODULE_NAME, "module-info.java")

        # (3)+(4) inject uses, drop jvmci provides
        mi_text = _build_module_info(mi_java, compiler_jar)
        with open(mi_java, "w") as f:
            f.write(mi_text)

        # (5) compile module-info against the relocated classes, inject, drop orphan service.
        # -source/-target pins the descriptor class-file version to the SDK floor so the jar stays
        # readable on every JDK the SDK supports, not just the one that happened to build it.
        _run([_tool("javac"), "-source", str(_release_for(jdk)), "-target", str(_release_for(jdk)),
              "-Xlint:-options",
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
