# Gate results

Each gate is go/no-go. Evidence and the profiling decision review are recorded per gate.

| Gate | Status | Date | Evidence |
|---|---|---|---|
| C0 repo + builds + devices | **passed** | 2026-10-09 | private repo (`gh repo view` → PRIVATE); `make BACKEND=opencl,cuda` on JDK 21.0.2 and `make jdk25 BACKEND=opencl,cuda` on JDK 25.0.2: BUILD SUCCESS; `tornado --devices` (both): 0:0 OpenCL RTX 4090, 0:1 OpenCL UHD 770, 1:0 CUDA RTX 4090 |
| C1 baseline reproducible | pending | | |
| C2 ⚑ dependency + phase matrix | pending | | |
| C3 ⚑ end-to-end design test | in progress | 2026-10-09 | `tc-ir` (TC-SSA types/IR/builder/printer/parser/verifier/dominators) and `tc-check` (FileCheck, lit) done: 31 headless tests green |
| C4a/b/c frontend, passes, Access[] | pending | | |
| C5a/b ⚑ structurizer, vertical slice | pending | | |
| C6 differential parity | pending | | |
| C7 performance + stability | pending | | |
| C8 Graal removed | pending | | |
| C10 Babylon adapter | pending | | |
