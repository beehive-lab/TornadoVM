# Metal backend — post-JVMCI reflection-path completion guide

Playbook for finishing the **Metal** backend on the `cleanup/opencl-post-jvmci` branch
(PR #894). CUDA and OpenCL are done and validated at parity; **Metal is the only remaining
backend** (PTX and SPIRV are being removed next release — ignore them). Every Metal fix has a
direct OpenCL/CUDA precedent you copy — this is mechanical porting, not new design.

Run this on a **macOS + Apple-Silicon** machine (Metal needs `xcrun`/Metal toolchain).

---

## 1. The one root cause (read this first)

Without JVMCI the metadata layer is reflection-based. Graal's `InvocationPlugins.lookupInvocation`
**misses** at *sketch* time on this path, so intrinsics that normally get replaced by a plugin
(KernelContext ops, native-array/HalfFloat accessors) **survive as plain invokes** and either:
- reach lowering with an unhandled node (`address origin unimplemented`, `NewInstance not handled`),
- get compiled as their bodiless/abstract JDK fallback (`readFieldValue not implemented`, bogus
  folded index), or
- silently truncate (a `half` read as a `short`/`ulong`).

**The fix is always the same shape:** recognise the surviving invoke (in the backend's
`IntrinsicsReplacements` phase, `LoweringProvider`, `GraphBuilderPlugins`, or `Backend` codegen)
and emit the node the plugin would have — using Metal's own node classes, which already exist.

The plugins **do** fire at real-compile time, so these bugs are **reflection-path (sketch) only**
and usually reflection-path-parity with what CUDA/OpenCL already fixed.

---

## 2. Setup & build

```bash
# JDK: 21 (native JVMCI, patch-module) is the easiest baseline; 25/26/27 also supported.
export JAVA_HOME=<jdk21-home>
make metal            # == bin/compile --jdk jdk21 --backend metal,opencl
source setvars.sh     # sets TORNADOVM_HOME, PATH; re-source per shell
tornado --devices     # confirm a Metal device shows up
```

Build gotchas learned this session:
- **Never `rm graalJars/*`.** The jdk25/26/27 builds regenerate `tornado-graal` via `jdeps`, which
  needs `jdk.internal.vm.ci` — present natively only on **JDK 21**. If graalJars gets cleared,
  build **jdk21 first** to regenerate the jars, then the other JDKs reuse them.
- **XML comments can't contain `--`** (double dash). Bit us twice in pom/profile comments.

---

## 3. Establish the baseline

```bash
source setvars.sh
tornado-test --quickPass 2>&1 | grep -oE "Test ran: [0-9]+, Failed: [0-9]+, Unsupported: [0-9]+"
```

Aggregate `Failed` across lines. **`Unsupported` ≠ `Failed`** — those are feature-gated skips
(FP16/FP64/MMA/multi-device), already reported separately by commit `045000f38`. Real fails on
CUDA were ~7, OpenCL ~10 after the fixes; expect Metal to start higher and drop as you port.

Get the exact failing classes:
```bash
LOG=/tmp/qp.log; tornado-test --quickPass > "$LOG" 2>&1
awk '/--params "/{c=$0;sub(/.*--params "/,"",c);sub(/".*/,"",c)}
     /Test ran: [0-9]+, Failed: [1-9]/{print c" | "$0}' "$LOG"
```

Per-class detail (this is your main debug loop):
```bash
# real failure reasons (bailout enabled so the true error isn't masked):
tornado-test -V -J"-Dtornado.recover.bailout=True" uk.ac.manchester.tornado.unittests.<pkg>.<Class>
# see generated Metal kernel:
tornado-test -V -pk uk.ac.manchester.tornado.unittests.<pkg>.<Class>#<method>
```

---

## 4. The fix playbook

For any failing class: **`git log --oneline | grep -i <symptom>`** to find the OpenCL/CUDA commit,
read its diff, mirror it into the Metal file below. Metal already has the target node classes
(`AtomAddNodeTemplate`, `ReadHalfFloatNode`, `WriteHalfFloatNode`, `LocalArrayNode`, barrier nodes).

### Known gaps (surveyed — Metal currently lacks all of these)

| # | Symptom / failing tests | OpenCL/CUDA reference commit | Metal file to edit |
|---|---|---|---|
| A | `atomics.TestAtomics` `testAtomic18*` → `address origin unimplemented` / `NewInstance not handled`. **Metal `IntrinsicsReplacements` has 0 KernelContext cases.** | `8633c68f4` (OCL) | `graal/phases/TornadoMetalIntrinsicsReplacements.java` |
| B | Quantized/HalfFloat matmul wrong result (e.g. `getFloat32` returns `1.0` for `1.5`) — HalfFloat device-fn param typed as its SHORT platform kind. | `da21aaafd` (OCL) | `graal/backend/MetalBackend.java` (`emitMethodParameters`) |
| C | `TestHalfFloatLocalArray`, `TestLocalMemoryReductionsHalfFloats` — local `half` read as word/`ulong`, truncates `1.5→1`. Reflection path wraps the array in a **PiNode**. | `e97cd963d` (OCL) | `graal/MetalLoweringProvider.java` (`lowerLoad/StoreIndexedNode`) |
| D | `QuantizationTests` / Q8 ByteArray path → `readFieldValue not implemented` / OOB folded index. `ByteArray.getHalfFloat/setHalfFloat` not intrinsified. | `03b3007aa` (OCL), `65a7256ae` (CUDA) | `graal/compiler/plugins/MetalGraphBuilderPlugins.java` (`registerByteArrayHalfFloatAccess`) |

Plus the base **KernelContext recovery** (`allocate*LocalArray`, `localBarrier`, `globalBarrier`)
in `TornadoMetalIntrinsicsReplacements` if those tests fail — mirror `TornadoOpenCLIntrinsicsReplacements`.

### Worked pattern — gap A (atomicAdd), what you'll do for each

1. Open `TornadoOpenCLIntrinsicsReplacements.java` (the reference) and the Metal one.
2. Add the case + helper, swapping OCL node types for Metal's:
   ```java
   case "Direct#KernelContext.atomicAdd":
       lowerAtomicAdd(graph, invoke);      // build address at (index + PANAMA_OBJECT_HEADER_SIZE/byteCount)
       break;                              //   then new Metal AtomAddNodeTemplate(address, inc, kind)
   ```
   `atomicElementKind(segment)` classifies by the stamp type name (IntArray→Int, LongArray→Long, …).
   Use `TornadoOptions.PANAMA_OBJECT_HEADER_SIZE` (16) for the header, **not** `getArrayBaseOffset`.
3. Import Metal's `AtomAddNodeTemplate`, `OffsetAddressNode`, `AddNode/MulNode/SignExtendNode`,
   `ObjectStamp/Stamp/NodeView/GraphUtil`, `TornadoOptions`.

Gap B: in `emitMethodParameters`, before the `guarantee`, add
`if (isHalfFloat(javaType)) metalKind = MetalKind.HALF;` (mirror `OCLKind.HALF`).
Gap C: add `isLocalHalfArray(ValueNode)` that does `GraphUtil.unproxify(array)` then checks
`LocalArrayNode` HALF kind OR a surviving `allocateHalfFloatLocalArray` invoke; use it in both
`lowerLoadIndexedNode`/`lowerStoreIndexedNode` instead of the bare `instanceof`.
Gap D: `arrayElementAddress(b, receiver, byteIndex, JavaKind.Byte, segmentField, baseIndexField)`
(byte offset = `baseIndex + byteIndex`, baseIndex==arrayHeaderSize) + `ReadHalfFloatNode`/
`WriteHalfFloatNode`; register `getHalfFloat`/`setHalfFloat` on `ByteArray.class`.

Also mirror any `TornadoHalfFloatReplacement` reflection-path recovery (`getFloat32`/
`getHalfFloatValue` surviving-invoke recognition) that CUDA has and Metal lacks.

---

## 5. Per-fix loop (do one gap at a time)

```bash
make metal && source setvars.sh
# confirm the targeted class(es) now pass:
tornado-test -V uk.ac.manchester.tornado.unittests.<pkg>.<Class> 2>&1 | grep "Test ran:"
# then full regression — must not increase any other class's real fails:
tornado-test --quickPass 2>&1 | grep -oE "Test ran: [0-9]+, Failed: [0-9]+, Unsupported: [0-9]+"
make checkstyle    # must be clean
```

Commit each fix separately, one-line message, **no attribution / no Co-Authored-By**, mirroring the
OpenCL/CUDA subjects (e.g. "Recover ctx.atomicAdd on the Metal reflection path: … mirrors the OpenCL
backend"). Never `git add -A` (untracked `.gguf`/scratch). Push:
```bash
git push mp HEAD:jdk27-jvmci-removal      # 'mp' = mikepapadim fork; PR #894
```

---

## 6. Definition of done

- Metal `quickPass` real fails reduced to the same residual class as CUDA/OpenCL (only genuine
  port-tail / platform-specific / multi-device items remain; `Unsupported` doesn't count).
- `make checkstyle` clean; JDK 21 build green (spot-check jdk25/27 if available).
- Post a comment on PR #894 with the before/after Metal quickPass numbers and the fixes landed,
  matching the CUDA/OpenCL validation-matrix format already there.

## 7. References
- Root-cause + timings + the shared fix patterns: `docs/BACKEND-POST-JVMCI-PORTING.md` (if present),
  and the commit log — `git log --oneline | grep -iE "reflection path|mirrors the"`.
- Startup/launch: `LEYDEN-next-steps.md`. Argfile launch: `gen-tornado-argfile-template.py`
  (fixed for the reflection path) → `java @<argfile> -m <module>/<Main>`.
