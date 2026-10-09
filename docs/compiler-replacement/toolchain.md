# Interop and verification toolchain (user-local, optional)

Installed 2026-10-09 for the user only (no sudo). TC never links against any of it: these are
external tools that tests and `tc` debug commands run on dumped `.ll` / `.mlir` files when present,
and skip cleanly otherwise.

| Tool | Version | Location | Use |
|---|---|---|---|
| LLVM `opt`, `llc`, `llvm-as`, `llvm-config` | 23.1.3 (conda-forge) | `~/.local/share/tc-llvm/bin` | IR verification (`opt -passes=verify`), `-O3` oracle, offline NVPTX (`llc -mtriple=nvptx64-nvidia-cuda -mcpu=sm_89`) |
| MLIR `mlir-opt`, `mlir-translate` | 23.1.3 (conda-forge) | same | `.mlir` verification (`--verify-each`), scf → cf → LLVM lowering check |
| `llvm-spirv` | 23.1.2 (conda-forge) | same | LLVM IR → SPIR-V (future Intel target) |
| `alive-tv` (Alive2) | source commit `01a5ec45` (2026-08-17; last before the LLVM 24 TLI API change `2fbdde96`) | `~/.local/src/alive2/build`, symlinked into the env `bin` | Translation validation of TC canonicaliser/folding rules |
| Z3 | conda-forge `libz3` | env `lib` | Alive2 solver |
| micromamba | 2.9.0 | `~/.local/bin/micromamba` | Environment manager |

Environment: `export PATH=$HOME/.local/share/tc-llvm/bin:$PATH` (TC tests look for `TC_LLVM_HOME`,
default `~/.local/share/tc-llvm`).

## Smoke test (all passed 2026-10-09)

- `opt -passes=verify` on a scalar function: OK.
- `alive-tv`: `mul x, 8` → `shl x, 3` "Transformation seems to be correct"; `mul x, 8` → `ashr x, 3` "doesn't verify".
- `mlir-opt --verify-each` on an `scf.for` + `memref` kernel: OK; `--convert-scf-to-cf --convert-to-llvm | mlir-translate --mlir-to-llvmir` emits `define void @add`.
- `llvm-spirv` on a `spir_kernel` with `addrspace(1)`: 248-byte SPIR-V module.
- `llc` NVPTX for `sm_89`: emits `.target sm_89`.

## Rebuild recipe

```
micromamba create -p ~/.local/share/tc-llvm -c conda-forge "llvmdev=23.1" "mlir=23.1" \
  "llvm-spirv=23.1" "llvm-tools=23.1" "clangxx=23.1" libz3 zlib zstd libxml2 cmake ninja re2c lit
git clone https://github.com/AliveToolkit/alive2 && cd alive2 && git checkout 01a5ec45
cmake -GNinja -B build -DCMAKE_BUILD_TYPE=Release -DBUILD_TV=1 -DLLVM_DIR=$E/lib/cmake/llvm \
  -DZ3_INCLUDE_DIR=$E/include -DZ3_LIBRARIES=$E/lib/libz3.so -DCMAKE_PREFIX_PATH=$E -DZLIB_ROOT=$E \
  -DCMAKE_CXX_COMPILER=$E/bin/clang++ -DCMAKE_C_COMPILER=$E/bin/clang
ninja -C build alive-tv
```
(`E=~/.local/share/tc-llvm`.) Alive2 `main` tracks LLVM `main`; when the env moves to LLVM 24,
move Alive2 forward too.
