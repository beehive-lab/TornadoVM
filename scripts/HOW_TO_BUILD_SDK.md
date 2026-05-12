# How to Build TornadoVM Release SDKs

The script `scripts/build-release-sdks.py` automates building all TornadoVM SDK
distributions for a given release version.

Given a base version tag (e.g. `v4.0.0`), the script:
1. Resolves the JDK-specific tags `v4.0.0-jdk21` and `v4.0.0-jdk25`.
2. Checks out each tag into a temporary git worktree (the current branch is never touched).
3. Builds all relevant SDK variants inside that worktree.
4. Collects the resulting archives into the output directory.
5. Removes the worktree when done.

## Prerequisites

### All platforms
- Python 3.8 or later
- Git 2.5 or later (for `git worktree`)
- Maven wrapper (`mvnw` / `mvnw.cmd`) — already in the repo
- A working C/C++ toolchain (CMake, compiler) for the native JNI libraries

### macOS and Linux — sdkman with Temurin JDKs
The script resolves JDK paths automatically from sdkman.  Both JDK 21 and JDK 25
must be installed as Temurin distributions before running the script.

1. Install sdkman if not already present:
   ```bash
   curl -s "https://get.sdkman.io" | bash
   source ~/.sdkman/bin/sdkman-init.sh
   ```

2. Install the Temurin JDKs:
   ```bash
   sdk install java 21.0.10-tem   # or whatever the latest 21.x patch is
   sdk install java 25.0.2-tem    # or whatever the latest 25.x patch is
   ```

   The script picks the newest installed patch version for each major
   automatically, so the exact identifiers above are examples only.

### Windows
sdkman is not available on Windows.  You must supply the paths to both JDKs
via command-line flags (see usage below).  Download Temurin JDK 21 and JDK 25
from [Adoptium](https://adoptium.net) and note the installation paths.

---

## Tag naming convention

The script constructs the per-JDK tags from the base version you pass:

| Argument | Tags checked out |
|----------|-----------------|
| `v4.0.0` | `v4.0.0-jdk21`, `v4.0.0-jdk25` |

If a tag does not exist locally, the script fetches it from `origin`
automatically.  If it still cannot be found, that JDK version is skipped
with a warning and the script continues with the remaining tags.

---

## What the script builds

The build matrix depends on the detected platform:

| Platform | JDK tag | Backends built | Archive label |
|----------|---------|----------------|---------------|
| macOS    | jdk21, jdk25 | `opencl` | `opencl` |
| macOS    | jdk21, jdk25 | `metal,opencl` | `metal-opencl` |
| Linux    | jdk21, jdk25 | `opencl` | `opencl` |
| Linux    | jdk21, jdk25 | `ptx` | `ptx` |
| Linux    | jdk21, jdk25 | `spirv` | `spirv` |
| Linux    | jdk21, jdk25 | `opencl,ptx,spirv` | `full` |
| Windows  | jdk21, jdk25 | `opencl` | `opencl` |
| Windows  | jdk21, jdk25 | `ptx` | `ptx` |
| Windows  | jdk21, jdk25 | `spirv` | `spirv` |
| Windows  | jdk21, jdk25 | `opencl,ptx,spirv` | `full` |

Each build calls `bin/compile --sdk` from the checked-out worktree, which
compiles TornadoVM and produces `.tar.gz` and `.zip` archives in that
worktree's `dist/` directory.  Archives are moved to the output directory
after every successful build.

---

## Output directory layout

```
release-sdks/                          # configurable via --output-dir
└── <version>/
    └── <platform>-<arch>/
        ├── tornadovm-<version>-jdk21-opencl-<platform>-<arch>.tar.gz
        ├── tornadovm-<version>-jdk21-opencl-<platform>-<arch>.zip
        ├── tornadovm-<version>-jdk21-metal-opencl-<platform>-<arch>.tar.gz  # macOS
        ├── tornadovm-<version>-jdk21-metal-opencl-<platform>-<arch>.zip     # macOS
        ├── tornadovm-<version>-jdk25-opencl-<platform>-<arch>.tar.gz
        ├── ...
        └── tornadovm-<version>-jdk25-full-<platform>-<arch>.zip             # Linux/Windows
```

---

## Usage

Run the script from the **TornadoVM repository root**.

### macOS / Linux (sdkman auto-detection)

```bash
python3 scripts/build-release-sdks.py --version v4.0.0
```

Optionally specify a different output directory:

```bash
python3 scripts/build-release-sdks.py --version v4.0.0 --output-dir /tmp/tornadovm-release
```

To override the sdkman auto-detection and point to specific JDK installations:

```bash
python3 scripts/build-release-sdks.py --version v4.0.0 \
    --jdk21-home /path/to/jdk-21 \
    --jdk25-home /path/to/jdk-25
```

### Windows

Both `--jdk21-home` and `--jdk25-home` are required on Windows:

```bat
python scripts\build-release-sdks.py --version v4.0.0 ^
    --jdk21-home "C:\Program Files\Eclipse Adoptium\jdk-21.0.x.y-hotspot" ^
    --jdk25-home "C:\Program Files\Eclipse Adoptium\jdk-25.0.x.y-hotspot"
```

---

## Command-line reference

| Flag | Required | Default | Description |
|------|----------|---------|-------------|
| `--version VERSION` | Yes | — | Base release tag (e.g. `v4.0.0`); the script appends `-jdk21` / `-jdk25` |
| `--output-dir DIR` | No | `release-sdks/` | Root directory for collected SDK archives |
| `--jdk21-home PATH` | Windows only | auto (sdkman) | Path to a JDK 21 installation |
| `--jdk25-home PATH` | Windows only | auto (sdkman) | Path to a JDK 25 installation |

---

## Exit codes

| Code | Meaning |
|------|---------|
| `0` | All builds succeeded |
| `1` | One or more builds failed (partial output may exist) |

When one build fails the script continues with the remaining builds so that as
many archives as possible are produced.  Failed build labels are printed in the
summary at the end.

---

## Notes

- The script must be run from the **TornadoVM repository root** (where `bin/compile` lives).
- Each tag is checked out into a **temporary git worktree** in the system temp
  directory.  The current working branch is never modified.  Worktrees are
  always removed on exit, even if a build fails.
- Before every build the script clears `graalJars/` inside the worktree so that
  `pull_graal_jars.py` always downloads the correct GraalVM JAR versions for the
  JDK being built.  Stale JARs would otherwise be silently reused, causing
  version mismatches between JDK 21 and JDK 25 builds.
- Each individual build (`bin/compile`) runs a Maven clean before compiling, so
  builds are fully independent of one another.
- Archives are moved out of the worktree's `dist/` into the output directory
  after each successful build.
