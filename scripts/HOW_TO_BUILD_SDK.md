# How to Build TornadoVM Release SDKs

The script `scripts/build-release-sdks.py` automates building all TornadoVM SDK
distributions for a given release version.

Given a release tag (e.g. `v4.0.0`), the script:
1. Checks out that tag into a temporary git worktree (the current branch is never touched).
2. Builds all relevant SDK variants inside that worktree with the `jdk22plus` profile.
3. Collects the resulting archives into the output directory.
4. Removes the worktree when done.

One SDK serves every JDK from 22 up: it is compiled with `--release 22` and no
preview features, so any JDK >= 22 produces an identical artifact. JDK 21 is no
longer supported.

## Prerequisites

### All platforms
- Python 3.8 or later
- Git 2.5 or later (for `git worktree`)
- Maven wrapper (`mvnw` / `mvnw.cmd`) — already in the repo
- A working C/C++ toolchain (CMake, compiler) — most backends and library
  providers bind through `java.lang.foreign` and need no native build, but the
  cuDNN SDPA shim and CUTLASS (`cudnn-jni`, `cutlass-jni`) still compile native
  code when the CUDA SDK variant is built

### macOS and Linux — sdkman with Temurin JDKs
The script resolves the build JDK automatically from sdkman: JDK 25 builds the
SDK (any JDK >= 22 works; 25 is pinned for reproducibility).

1. Install sdkman if not already present:
   ```bash
   curl -s "https://get.sdkman.io" | bash
   source ~/.sdkman/bin/sdkman-init.sh
   ```

2. Install the Temurin JDK:
   ```bash
   sdk install java 25.0.2-tem    # or whatever the latest 25.x patch is
   ```

   The script picks the newest installed patch version automatically, so the
   exact identifier above is an example only.

### Windows
sdkman is not available on Windows. Supply the JDK path via a command-line
flag (see usage below). Download Temurin JDK 25 from
[Adoptium](https://adoptium.net) and note the installation path.

---

## What the script builds

| Platform | Backends built | Archive label |
|----------|----------------|---------------|
| macOS    | `opencl` | `opencl` |
| macOS    | `metal` | `metal` |
| Linux    | `opencl` | `opencl` |
| Linux    | `cuda` | `cuda` |
| Linux    | `opencl,cuda` | `full` |
| Windows  | `opencl` | `opencl` |
| Windows  | `cuda` | `cuda` |

Each build calls `make sdk-jdk22plus` (`nmake /f Makefile.mak sdk-jdk22plus` on
Windows) from the checked-out worktree, which compiles TornadoVM and produces
`.tar.gz` and `.zip` archives in that worktree's `dist/` directory. Archives are
moved to the output directory after every successful build.

---

## Output directory layout

```
release-sdks/                          # configurable via --output-dir
└── <version>/
    └── <platform>-<arch>/
        ├── tornadovm-<version>-jdk22plus-opencl-<platform>-<arch>.tar.gz
        ├── tornadovm-<version>-jdk22plus-opencl-<platform>-<arch>.zip
        ├── tornadovm-<version>-jdk22plus-metal-<platform>-<arch>.tar.gz     # macOS
        ├── ...
        └── tornadovm-<version>-jdk22plus-full-<platform>-<arch>.zip         # Linux
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

To override the sdkman auto-detection and point to a specific JDK installation:

```bash
python3 scripts/build-release-sdks.py --version v4.0.0 \
    --jdk22plus-home /path/to/jdk-25
```

### Windows

`--jdk22plus-home` is required on Windows:

```bat
python scripts\build-release-sdks.py --version v4.0.0 ^
    --jdk22plus-home "C:\Program Files\Eclipse Adoptium\jdk-25.0.x.y-hotspot"
```

On restricted/managed Windows machines that block running unsigned executables,
add `--skip-windows-executables`. The build then never invokes PyInstaller
(`pyinstaller.exe`), the `tornado.exe` wrappers, or the `zello_world` Level Zero
probe; the produced SDK ships the `.py` launchers instead of the `.exe`
wrappers, and the SDK validation skips the `tornado.exe` smoke checks.

```bat
python scripts\build-release-sdks.py --version v4.0.0 ^
    --jdk22plus-home "C:\jdks\jdk25" ^
    --skip-windows-executables
```

---

## Running via GitHub Actions

The `.github/workflows/build-release-sdks.yml` workflow runs this script on the
self-hosted macOS, Linux, and Windows runners. It is dispatched by the
finalize-release workflow, or manually via **workflow_dispatch**. The runners'
JDK paths are configured in the workflow (`JDK22PLUS_HOME`); update them there if
a runner layout changes.

---

## Command-line reference

| Flag | Required | Default | Description |
|------|----------|---------|-------------|
| `--version VERSION` | Yes | — | Release tag (e.g. `v4.0.0`) |
| `--output-dir DIR` | No | `release-sdks/` | Root directory for collected SDK archives |
| `--jdk22plus-home PATH` | Windows only | auto (sdkman, JDK 25) | JDK >= 22 used to build the SDKs |
| `--skip-windows-executables` | No | off | Windows only: don't build/run the native `.exe` wrappers (PyInstaller / `tornado.exe` / `zello_world`); ship the `.py` launchers instead |

---

## Exit codes

| Code | Meaning |
|------|---------|
| `0` | All builds succeeded |
| `1` | One or more builds failed (partial output may exist) |

When one build fails the script continues with the remaining builds so that as
many archives as possible are produced. Failed build labels are printed in the
summary at the end.

---

## Notes

- The script must be run from the **TornadoVM repository root** (where `bin/compile` lives).
- The tag is checked out into a **temporary git worktree** in the system temp
  directory. The current working branch is never modified. The worktree is
  always removed on exit, even if a build fails.
- Before every build the script clears `graalJars/` inside the worktree so that
  `pull_graal_jars.py` always stages fresh GraalVM jars.
- Each individual build (`bin/compile`) runs a Maven clean before compiling, so
  builds are fully independent of one another.
- Archives are moved out of the worktree's `dist/` into the output directory
  after each successful build.
