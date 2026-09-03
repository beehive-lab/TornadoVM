#!/usr/bin/env python3
"""
Set the Maven version across every TornadoVM reactor pom.xml WITHOUT breaking the
CI-friendly ${revision} scheme.

Why this exists
---------------
`mvn versions:set -DnewVersion=X` rewrites <version>${revision}</version> (and
every <parent><version>) to a hard-coded literal in all 22 reactor poms. Once
that happens the jdk21 / jdk22plus profile <revision> overrides in the root pom
are dead, and every build stamps the same JDK-less version regardless of the
active profile.

That is exactly what shipped right after the 6.0.0 release: the release
automation ran `versions:set`, the follow-up dev bump ran it again, and develop
was left with a literal <version>6.0.1-dev</version> everywhere. A locally built
SDK then produced `tornado-api-6.0.1-dev.jar` with NO -jdkNN suffix, so the
jdk21 (--enable-preview) jar and the jdk22plus jar overwrote each other in the
same ~/.m2 coordinate and downstream sample-app builds (kfusion, ray-tracer)
failed with "class file ... uses preview features of Java SE 21".

The release workflows must call THIS script instead of `versions:set`.

Modes
-----
  dev  X.Y.Z-dev
      Every pom's <version> / <parent><version> becomes the literal ${revision}.
      Root pom <revision> (the default, = jdk21) and the jdk21 profile override
      become  X.Y.Z-jdk21-dev ; the jdk22plus profile override becomes
      X.Y.Z-jdk22plus-dev. This is the state `develop` must always be in.

  release  X.Y.Z
      Every pom carries the plain literal X.Y.Z (no -jdkNN suffix, no
      ${revision}). All three root <revision> values are set to X.Y.Z too, so a
      profile-pinned release build still stamps the plain version.
      scripts/build-release-sdks.py renames each archive with its profile token
      and .github/workflows/deploy-maven-central.yml appends the -jdkNN Maven
      Central coordinate suffix ephemerally, right before `mvn deploy`.

Usage
-----
  python3 scripts/set-maven-version.py dev 6.0.1-dev
  python3 scripts/set-maven-version.py release 6.0.0
"""

import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


def reactor_poms():
    out = subprocess.check_output(
        ["git", "ls-files", "*pom.xml"], cwd=REPO_ROOT, text=True
    )
    poms = []
    for line in out.split():
        # beehive-spirv-toolkit and bin/graal-relocate are vendored sub-builds
        # with their own independent version lines - never touch them.
        if line.startswith("beehive-spirv-toolkit/") or line.startswith("bin/"):
            continue
        poms.append(REPO_ROOT / line)
    return poms


# A coordinate <version>: body is the ${revision} placeholder or a bare semver
# (optionally suffixed). Plugin and dependency <version>s live inside <build> /
# <dependencies> / <dependencyManagement>, which always follow the coordinate
# header; inter-module ones are ${project.version} / ${tornado.version}. So a
# coordinate version is one that sits in <parent>, or in the project header
# before the first structural section tag.
_COORD_VERSION = re.compile(
    r"(<version>)(?:\$\{revision\}|\d+\.\d+\.\d+(?:-[A-Za-z0-9.\-]+)?)(</version>)"
)
_HEADER_END = re.compile(
    r"<(packaging|properties|modules|dependencyManagement|dependencies|build|profiles|reporting)>"
)


def _set_project_and_parent_version(text, new_body):
    """Rewrite the <version> in <parent> and the project's own coordinate <version>."""

    # <parent> ... </parent>
    text = re.sub(
        r"<parent>.*?</parent>",
        lambda m: _COORD_VERSION.sub(rf"\g<1>{new_body}\g<2>", m.group(0), count=1),
        text,
        flags=re.DOTALL,
    )

    parent_end = text.rfind("</parent>")
    header_start = parent_end + len("</parent>") if parent_end != -1 else 0
    sec = _HEADER_END.search(text, header_start)
    header_end = sec.start() if sec else len(text)

    m = _COORD_VERSION.search(text, header_start, header_end)
    if m:
        text = text[: m.start()] + f"{m.group(1)}{new_body}{m.group(2)}" + text[m.end():]
    return text


def update_poms(new_body):
    for pom in reactor_poms():
        original = pom.read_text()
        updated = _set_project_and_parent_version(original, new_body)
        if updated != original:
            pom.write_text(updated)
            print(f"  {pom.relative_to(REPO_ROOT)}: <version> -> {new_body}")


def set_root_revisions(default_rev, jdk21_rev, jdk22plus_rev):
    root = REPO_ROOT / "pom.xml"
    text = root.read_text()
    revs = list(re.finditer(r"<revision>[^<]*</revision>", text))
    if len(revs) != 3:
        sys.exit(
            f"pom.xml: expected exactly 3 <revision> elements "
            f"(default, jdk21 profile, jdk22plus profile), found {len(revs)}"
        )
    wanted = [default_rev, jdk21_rev, jdk22plus_rev]
    for m, value in zip(reversed(revs), reversed(wanted)):
        text = text[: m.start()] + f"<revision>{value}</revision>" + text[m.end():]
    root.write_text(text)
    print(f"  pom.xml: <revision> = {default_rev} / {jdk21_rev} / {jdk22plus_rev}")


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ("dev", "release"):
        sys.exit(__doc__)
    mode, version = sys.argv[1], sys.argv[2]

    if mode == "dev":
        if not version.endswith("-dev"):
            sys.exit(f"dev version must end with -dev, got: {version}")
        base = version[: -len("-dev")]
        if not re.fullmatch(r"\d+\.\d+\.\d+", base):
            sys.exit(f"dev version must be X.Y.Z-dev, got: {version}")
        print(f"dev mode: {version}")
        update_poms("${revision}")
        set_root_revisions(
            default_rev=f"{base}-jdk21-dev",
            jdk21_rev=f"{base}-jdk21-dev",
            jdk22plus_rev=f"{base}-jdk22plus-dev",
        )
    else:
        if not re.fullmatch(r"\d+\.\d+\.\d+", version):
            sys.exit(f"release version must be X.Y.Z, got: {version}")
        print(f"release mode: {version}")
        update_poms(version)
        set_root_revisions(version, version, version)

    print("Done.")


if __name__ == "__main__":
    main()
