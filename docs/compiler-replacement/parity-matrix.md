# Parity matrix: TC vs Graal (Phase 6)

Differential results of the whole `tornado-test` suite on CUDA (RTX 4090, device 1:0), the Graal path
as reference ([baseline/tests-run1.json](baseline/tests-run1.json), `tests-cuda4090-run1`) and the same
suite with `-Dtornado.compiler=tc` ([baseline/run-tests.sh](baseline/run-tests.sh) with
`EXTRA_JVM`). Compared per test: pass/fail. Generated source is not compared.

## Run 1 (2026-10-10, fallback on)

| | Graal | TC (fallback on), run 1 | TC after the fixes (run 1 + rerun) |
|---|---:|---:|---:|
| tests compared | 1662 | 1662 | 1662 |
| PASS | 1515 | 1437 | **1514** |
| FAILED | 7 | 85 | 8 (Graal's 7 + one accepted difference) |
| CUDA configuration unsupported | 140 | 140 | 140 |
| kernel compilations by TC | – | 3026 | |
| fell back to Graal | – | 636 | |

The 9 memory tests the run stopped before were run separately under TC: `TestStressDeviceMemory` 2/2
(with the suite's `-Dtornado.device.memory=4GB -Xmx14g`, Graal also 2/2), `TestReclaimReleasedBuffers` 1/1,
and the six 5-minute `TestMemoryLeak` loops 6/6 (88,646 kernel compilations by TC, no fallback, `-Xmx2g`).

The 78 tests that differed in run 1 were rerun after fixes (`run-classes.sh rerun1`): 107/109 pass.
The two left: `TestComputePi` fails on Graal as well (baseline), and the one difference below.

| Difference | Cause | Status |
|---|---|---|
| `reductions.*` (42), `atomics.TestAtomicsWithReduction`, `grid.TestIrregularGridSizes#testReductionAtPrimeSize` | `@Reduce` compiled as a plain parallel loop: threads race on the result | fixed for now: TC refuses `@Reduce` (TC-E3201, Graal compiles it); the reduction rewrite is the next family |
| `batches.TestBatches` (26), `api.TestMultipleCopyOuts` (2) | batched execution: each device buffer holds one chunk, TC folded the full length and indexed past it (CUDA status 700), which poisoned the context for the following tests | fixed for now: TC refuses batched compilations (TC-E3202); per-chunk lengths/offsets todo |
| `codegen.TestPrivateArrayZeroInit` (2) | `new int[n]` not zero-filled | fixed: private arrays are declared `= {}` |
| `profiler.TestProfiler` (3) | TC compile time not reported | fixed: reported as the task's compiler time |
| `fails.BitOpsUnsupportedFail#testReverseUnsupported` | expects the compiler to reject `Integer.reverse`; TC compiles its JDK bytecode | **accepted difference**: TC is more capable, result checked correct by the other `Integer.reverse` tests |
| memory stress tests (9, not run) | with `-Dtornado.tc.verbose=true` each compile prints a line; `tornado-test` does not drain the pipe until exit, so the hot loop blocked on stdout | harness issue, not TC: full runs use `tornado.tc.stats` only |

## Metal parity (2026-10-11, fallback off, every family)

The same harness on an Apple M3 Pro (macOS 26.6, Metal device 0:0), Graal on Metal as the
reference, all 223 suite classes except `memory.leak.TestMemoryLeak`. Before running anything on the
device, every unit-test kernel TC emits for Metal (EmitSweep: 778 sources) was compiled by Apple's
Metal compiler on the GPU (`utils/metalc`, the driver's own `newLibraryWithSource:` call): 778/778.

| Run | Device | Tests | Graal PASS | TC PASS | TC worse | TC better | Launch differences |
|---|---|---:|---:|---:|---:|---:|---:|
| `metal1` | M3 Pro, Metal (0:0) | 1690 | 942 | 921 | 22 | 1 | 16 |
| `metal3` | M3 Pro, Metal (0:0) | 1560 | 942 | **941** | 2 (accepted) | 1 | 13 (test order) |

(`metal3` counts 130 fewer tests: the fp64 tests are now *unsupported* under both compilers, which
the harness does not count.) TC passes every test Graal passes on Metal, except:

- `fails.BitOpsUnsupportedFail#testReverseUnsupported`: TC compiles `Integer.reverse`. **Accepted
  difference**, as on CUDA and OpenCL.
- `fails.LocalMemoryFail#testTooMuchLocalMemoryIsReported`: the kernel asks for 48 KB of threadgroup
  memory on a 32 KB device. TC refuses it at compile time (TC-E4014, "the kernel's local arrays need
  49152 bytes of shared memory; a work-group has 32768"), so the Metal driver never sees it; the
  test checks the driver's own wording for a refused pipeline ("Unable to create the Metal compute
  pipeline"). **Accepted difference**: the same fault, reported earlier and with the sizes.
- Launch-configuration differences (`TestReductionsIntegers`, `TestReductionsLong`): JUnit ran the
  class's methods in a different order under the two JVMs, so the generated task-graph numbers
  (`__GENERATED_TASK_GRAPH__N`) name different tests; the configurations are the same, rotated.
  Harness artifact, not a compiler difference.
- `warmup.TestWarmUp#test01` (not in the table: it passed under both in `metal3`): asserts a warm-up
  of 1000-1008 ms of wall-clock time and fails intermittently under **both** compilers here.

TC better: `quantization.QuantizationTests#testQuantization` (fails under Graal on Metal).

What closed the gap from `metal1` (fixes in TC and the harness):

| Change | Tests |
|---|---|
| MSL has no `goto` or labels (Apple's compiler: "'goto' is not supported in Metal"; the mock header did not catch it): a jump C cannot express as `break`/`continue` sets a flag and breaks out of the innermost loop or switch, `if (tc_jump_ == k)` repeats it after that statement; a block left from inside becomes `do { } while (false)` | every kernel with a multi-level exit (7 in the sweep) or an early exit from a block (short-circuit conditions) |
| TC-E4012 (fp64 on a device without it) raises `TornadoDeviceFP64NotSupported`, as Graal's FP64 phase does | the fp64 tests: *unsupported* under both instead of FAILED under TC |
| `KernelContext.simdgroupMatrix*`: a `Matrix8x8Float` is an MSL `simdgroup_float8x8` value (the one object type allowed after ABI lowering); load (device or threadgroup), multiply-accumulate and store as helpers; `matrixMultiply8x8` compiled from its Java body | 13 simdgroup-matrix tests |
| `QuantizationUtils.dp4a` on Metal: four int8 products in a helper (MSL has no dp4a) | 3 dp4a tests |
| 64-bit atomic add on Metal: low words, then the high word plus the carry. Exact sum, unlike Graal's 32-bit add on the low word | `TestAtomics#testAtomic18_parallel_api_LongArray` |
| Fast math (Metal's default compile option) assumes no NaN: NaN tests (`x != x`, ordered/unordered comparisons) are spelled with `isnan` | `Math.signum(NaN)` (2 tests) |
| `tc-diff.py`: a Python timeout instead of coreutils `timeout` (absent on macOS) | harness |

**jitLLM on Metal (M3 Pro):** greedy output token-identical to Graal on Metal for Llama-3.2-1B-Instruct F16 (181 kernels by TC) and Qwen3-1.7B F16 (313 kernels), fallback off, `--fp32-kv-cache` (jitLLM has no FP16 key/value path on Metal). Bench (`jitllm bench --pp 128,512 --tg 64 --repetitions 5`, two rounds in alternating order, [perf/jitllm/metal](perf/jitllm/metal)), TC/Graal mean of the two rounds' medians: Llama 1.04 / 1.01 / 1.08 (pp128/pp512/tg64), Qwen3 0.99 / 0.99 / 0.98. Round-to-round spread on this laptop is up to 15% (Qwen3 tg64: 1.08 then 0.89), so the two compilers are level within noise.

<!-- tc-diff: generated by baseline/tc-diff.py; do not edit by hand -->
Run `metal3`: 1560 tests in 223 classes, TC with fallback **off**. PASS: Graal 942, TC 941. Status differences: 3 (TC worse 2, TC better 1). Launch-configuration differences: 13. Kernels compiled by TC: 3153.

TC refusals (each fails its test with fallback off): TC-E4012 158, TC-E1004 1, TC-E4014 1, TC-E1002 1.

| Class | Tests | Graal PASS | TC PASS | Differences |
|---|---:|---:|---:|---|
| fails.BitOpsUnsupportedFail | 1 | 1 | 0 | `testReverseUnsupported` PASS→FAILED |
| fails.LocalMemoryFail | 1 | 1 | 0 | `testTooMuchLocalMemoryIsReported` PASS→FAILED |
| quantization.QuantizationTests | 7 | 4 | 5 | `testQuantization` FAILED→PASS |
| reductions.TestReductionsIntegers | 15 | 15 | 15 | 11 launch config(s) differ |
| reductions.TestReductionsLong | 6 | 6 | 6 | 2 launch config(s) differ |
<!-- /tc-diff -->

## OpenCL parity (2026-10-11, fallback off, every family)

The same differential harness ([baseline/tc-diff.py](baseline/tc-diff.py)) on the two OpenCL devices,
Graal on OpenCL as the reference, all 223 suite classes except `memory.leak.TestMemoryLeak`. The
CUDA rerun (`run25`) checks that the OpenCL work changed nothing there.

| Run | Device | Tests | Graal PASS | TC PASS | TC worse | TC better | Launch differences |
|---|---|---:|---:|---:|---:|---:|---:|
| `ocl8` | RTX 4090, OpenCL (0:0) | 1703 | 1027 | **1053** | 1 | 26 | 0 |
| `igpu3` | Intel UHD 770, OpenCL (0:1) | 1703 | 932 | **930** | 1 (+1 racy) | 0 | 0 |
| `run25` | RTX 4090, CUDA (1:0) | 1692 | 1516 | **1534** | 1 | 19 | 0 |

TC passes every test Graal passes on each device, except:

- `fails.BitOpsUnsupportedFail#testReverseUnsupported` (all three): the test expects the compiler to
  reject `Integer.reverse`; TC compiles it correctly. **Accepted difference**, as on CUDA.
- `compute.TransformerKernelsTest#testReductionOneBlockWithLayer` (UHD 770 only): **racy test**.
  Thread 0 of group 0 sums the other groups' partial results without global synchronisation, so
  the result depends on the order groups run (CUDA: Graal FAILED / TC PASS; UHD 770: Graal PASS /
  TC FAILED, 3 of 3 runs each). `tc-diff.py` lists it in `RACY_TESTS`, outside the better/worse counts.

TC better: `vectortypes.TestHalfFloats` (Half3/4/8/16, 26/26 everywhere; Graal 3 on the 4090
OpenCL, 8 on CUDA), the half-float local-memory reductions, and `TestComputePi`. The UHD 770 has no
fp64: TC refuses double kernels with TC-E4012 where Graal fails them (`None→FAILED` rows).

**jitLLM on OpenCL (RTX 4090, 2026-10-11):** every kernel by TC with fallback off (Llama-3.2-1B
Q8_0: 148, Qwen3-0.6B F16: 284; all OpenCL C), greedy output token-identical to Graal on OpenCL.
`jitllm bench --gpu --opencl --pp 128,512 --tg 64 --repetitions 5`, two rounds in alternating order
([perf/jitllm/opencl](perf/jitllm/opencl)): TC/Graal 1.073 / 1.082 / 1.047 (Llama pp128/pp512/tg64),
1.088 / 1.059 / 1.036 (Qwen3).

What closed the gap, in order (OpenCL runs `ocl1`→`ocl8`, `igpu1`→`igpu3`):

| Change | Tests |
|---|---|
| OpenCL dialect (ABI from Graal's `--printKernel`, `__global`/`__local` pointees, built-ins, Java min/max and float atomics helpers) | baseline: `ocl1` TC 951 / Graal 1027 |
| fp16 legality skipped on OpenCL (the 4090 compiles `half` without advertising `cl_khr_fp16`); half bits through private memory | half-float families |
| byte/char/short kernel parameters declared at their width (`clSetKernelArg` checks sizes), widened at entry | scalar-parameter tests |
| Float/Int/Double 8- and 16-lane vectors; `lane_at`/`insert_at` folded to constant lanes | `vectortypes` |
| object reference fields decoded once per object; decoded references printed as variables (16-lane kernel 80 → 40 registers, = Graal; it failed with `CL_OUT_OF_RESOURCES` at 1024 threads) | `TestFloats#testVectorFloat16Int16` |
| Half3/4/8/16 bindings | 18 tests on the UHD 770 |
| `TornadoMath.sinpi/cospi/toRadians` as device built-ins (their Java bodies are double) | 3 tests on the UHD 770 |
| write-only API matrices allocated in a kernel are dropped (escape-analysis equivalent) | `fails.CodeFail#codeFail02` |

## Differential run 5 (2026-10-10, fallback off, every family) - CUDA parity

All 223 suite classes except `memory.leak.TestMemoryLeak` (run only as the final validation of the
CUDA work). **TC passes exactly Graal's 1516 tests with fallback off**, the tile family included
(CUDA Tile C++ through nvcc: 224/224), compiling 3758 kernels. The differences left are the
accepted ones: `BitOpsUnsupportedFail` (TC compiles `Integer.reverse`, which the test expects to
fail), `TestComputePi` (passes only under TC), and `TestReductionsAutomatic` launch sizes
(`testIrregularSize03` draws random sizes on every run). TC-E4011 refusals are fp8 tiles on this
sm_89 device, which the tests expect to be refused, as on Graal.

<!-- tc-diff: generated by baseline/tc-diff.py; do not edit by hand -->
Run `run5`: 1692 tests in 223 classes, TC with fallback **off**. PASS: Graal 1516, TC 1516. Status differences: 20 (TC worse 1, TC better 1). Launch-configuration differences: 8. Kernels compiled by TC: 3758.

TC refusals (each fails its test with fallback off): TC-E1011 19, TC-E4011 11, TC-E1010 2, TC-E1004 1, TC-E1002 1.

| Class | Tests | Graal PASS | TC PASS | Differences |
|---|---:|---:|---:|---|
| fails.BitOpsUnsupportedFail | 1 | 1 | 0 | `testReverseUnsupported` PASS→FAILED |
| reductions.TestReductionsAutomatic | 4 | 4 | 4 | 8 launch config(s) differ |
| reductions.TestReductionsFloats | 13 | 12 | 13 | `testComputePi` FAILED→PASS |
| vectortypes.TestHalfFloats | 26 | 8 | 8 | `privateVectorHalf4` None→FAILED; `privateVectorHalf8` None→FAILED; `testInternalSetMethod02` None→FAILED; `testInternalSetMethod03` None→FAILED; `testInternalSetMethod04` None→FAILED; `testSimpleDotProductHalf16` None→FAILED … |
<!-- /tc-diff -->

## Differential run 4 (2026-10-10, fallback off, all families except tile)

After Debug.printf, enum constants (ordinals; fields read by select), vector lane aliases and
whole-vector `set`/`fill`/`size`, vector parameters written back after setters, and device methods
returning any value. Tile left out (next: the CUDA Tile path), memory-leak left out (final CUDA
validation only). **TC passes exactly Graal's set outside tile.** Accepted differences:
`BitOpsUnsupportedFail` (TC compiles `Integer.reverse`), `TestComputePi` (passes only under TC),
`TestReductionsAutomatic` launch sizes (`testIrregularSize03` draws random sizes on every run).
Launches are now compared as per-task multisets (test methods may run in another order).

<!-- tc-diff: generated by baseline/tc-diff.py; do not edit by hand -->
Run `run4`: 1458 tests in 203 classes, TC with fallback **off**. PASS: Graal 1292, TC 1292. Status differences: 20 (TC worse 1, TC better 1). Launch-configuration differences: 7. Kernels compiled by TC: 3487.

TC refusals (each fails its test with fallback off): TC-E1011 19, TC-E1010 2, TC-E1004 1, TC-E1002 1.

| Class | Tests | Graal PASS | TC PASS | Differences |
|---|---:|---:|---:|---|
| fails.BitOpsUnsupportedFail | 1 | 1 | 0 | `testReverseUnsupported` PASS→FAILED |
| reductions.TestReductionsAutomatic | 4 | 4 | 4 | 7 launch config(s) differ |
| reductions.TestReductionsFloats | 13 | 12 | 13 | `testComputePi` FAILED→PASS |
| vectortypes.TestHalfFloats | 26 | 8 | 8 | `privateVectorHalf4` None→FAILED; `privateVectorHalf8` None→FAILED; `testInternalSetMethod02` None→FAILED; `testInternalSetMethod03` None→FAILED; `testInternalSetMethod04` None→FAILED; `testSimpleDotProductHalf16` None→FAILED … |
<!-- /tc-diff -->

## Differential run 3 (2026-10-10, fallback off)

After batches, the kernelcontext leftovers (cp.async, MMA global fragments, dynamic parallelism),
local vector collections, Half2, quantization (dp4a). Graal reference: the run-2 Graal logs
([baseline/tc-diff-run3.json](baseline/tc-diff-run3.json)); `memory.leak.TestMemoryLeak` is left
out as in run 2 (300 s loops, no Graal reference). No test that passed under TC in run 2 fails here.

Outside the tile family TC is 7 tests short of Graal, 3 of them TestHello (`Debug.printf`, fixed after
this run: 7/7). The tile family (224 Graal passes) needs the CUDA Tile path: the support probe is a
`TileContext` kernel, refused by TC, so every tile test reports CUDA CONFIGURATION UNSUPPORTED.

<!-- tc-diff: generated by baseline/tc-diff.py; do not edit by hand -->
Run `run3`: 1692 tests in 223 classes, TC with fallback **off**. PASS: Graal 1516, TC 1285. Status differences: 251 (TC worse 232, TC better 1). Launch-configuration differences: 7. Kernels compiled by TC: 3480.

TC refusals (each fails its test with fallback off): TC-E1011 31, TC-E1002 13, TC-E1003 3, TC-E1010 2, TC-E1004 1.

| Class | Tests | Graal PASS | TC PASS | Differences |
|---|---:|---:|---:|---|
| TestHello | 7 | 7 | 4 | `testHello` PASS→FAILED; `testPrintIntArray` PASS→FAILED; `testPrintIntArray2` PASS→FAILED |
| arrays.TestNewArrays | 7 | 5 | 5 | 1 launch config(s) differ |
| bitsets.BitSetTests | 1 | 1 | 0 | `test01` PASS→FAILED |
| compute.ComputeTests | 19 | 17 | 16 | `testRenderTrack` PASS→FAILED |
| fails.BitOpsUnsupportedFail | 1 | 1 | 0 | `testReverseUnsupported` PASS→FAILED |
| logic.TestEnumInKernel | 1 | 1 | 0 | `testEnumWithFieldAndMethodInKernel` PASS→FAILED |
| math.TestTornadoMathCollection | 37 | 37 | 37 | 1 launch config(s) differ |
| reductions.TestReductionsAutomatic | 4 | 4 | 4 | 5 launch config(s) differ |
| reductions.TestReductionsFloats | 13 | 12 | 13 | `testComputePi` FAILED→PASS |
| tasks.TestMultipleFunctions | 10 | 10 | 9 | `testVector01` PASS→FAILED |
| tile.TestTileAtomics | 6 | 6 | 0 | `testAccumulateRank1` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAccumulateRowsRank2` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAtomicSubtract` PASS→CUDA CONFIGURATION UNSUPPORTED; `testIntegerAtomics` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSplitKFourSplits` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSplitKTwoSplits` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileAttention | 7 | 7 | 0 | `testFlashAttentionGroupedQuery` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashAttentionLongerKeys` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashAttentionManyKvBlocks` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashAttentionMultiHead` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashAttentionSingleKvBlock` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSplitKReduceFourSplits` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileAttentionVariants | 8 | 8 | 0 | `testAttentionSinkDominant` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAttentionSinkNegligible` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashDecodeFourSplits` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashDecodeMultipleRowBlocks` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashDecodeOneBlockPerSplit` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashDecodeSingleSplit` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileChaining | 8 | 8 | 0 | `testChainUnderIntraPlanConcurrency` PASS→CUDA CONFIGURATION UNSUPPORTED; `testJitAndKernelContextAndTileAndLibrary` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSimtThenCuBlasOnly` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSimtThenTileThenCuBlasThenSimt` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTileKernelUnderCudaGraph` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTileProducerCuBlasConsumerAcrossGraphs` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileDTypes | 8 | 8 | 0 | `testDoubleMatmul` PASS→CUDA CONFIGURATION UNSUPPORTED; `testDoubleRowSum` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFp8RefusedBelowHopper` PASS→CUDA CONFIGURATION UNSUPPORTED; `testInt8Absolute` PASS→CUDA CONFIGURATION UNSUPPORTED; `testInt8MatmulDequantised` PASS→CUDA CONFIGURATION UNSUPPORTED; `testInt8MatmulRectangular` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileElementwise | 8 | 8 | 0 | `testFull` PASS→CUDA CONFIGURATION UNSUPPORTED; `testIota` PASS→CUDA CONFIGURATION UNSUPPORTED; `testNarrowingCastRoundTrip` PASS→CUDA CONFIGURATION UNSUPPORTED; `testRaggedEdge` PASS→CUDA CONFIGURATION UNSUPPORTED; `testScaledAdd` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTranspose` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileGemmVariants | 9 | 9 | 0 | `testFusedGluMlp` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMatmulTransposedA` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMatmulTransposedB` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMatmulTransposedBRectangular` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMatmulTransposedBoth` PASS→CUDA CONFIGURATION UNSUPPORTED; `testPersistentMatmulFewerBlocksThanTiles` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileLlmKernels | 4 | 4 | 0 | `testBmmLargerBatch` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBmmSmallBatch` PASS→CUDA CONFIGURATION UNSUPPORTED; `testDropoutWithMask` PASS→CUDA CONFIGURATION UNSUPPORTED; `testRope` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileMasking | 10 | 10 | 0 | `testCausalLongerSequence` PASS→CUDA CONFIGURATION UNSUPPORTED; `testCausalSeveralBlocks` PASS→CUDA CONFIGURATION UNSUPPORTED; `testCausalSingleBlock` PASS→CUDA CONFIGURATION UNSUPPORTED; `testClamp` PASS→CUDA CONFIGURATION UNSUPPORTED; `testCountAboveThreshold` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMinAndProduct` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileMathOps | 6 | 6 | 0 | `testAtomicLoadAndStore` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBinaryMath` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBitwise` PASS→CUDA CONFIGURATION UNSUPPORTED; `testPredicates` PASS→CUDA CONFIGURATION UNSUPPORTED; `testScansShiftsBitcastAndConcat` PASS→CUDA CONFIGURATION UNSUPPORTED; `testUnaryMath` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileMatmul | 11 | 11 | 0 | `testGemm128` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemm64` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemmKLoopAuto` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemmKLoopRolled` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemmKLoopUnrolled` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemmLongKLoop` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileMixedPipelines | 5 | 5 | 0 | `testCutlassEpilogueThenTile` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFourMixedChainsConcurrently` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTileBetweenTwoLibraryCalls` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTransformerBlock` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTransformerBlockUnderCudaGraph` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileOpLevel | 106 | 98 | 0 | `testAbs` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAdd` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAtan2` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAtomicLoadStore` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBf16Add` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBf16Div` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileRank3 | 4 | 4 | 0 | `testBatchedMatmulLarger` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBatchedMatmulSingleBatch` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBatchedMatmulSmall` PASS→CUDA CONFIGURATION UNSUPPORTED; `testScaleByBatch` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileRawBuffers | 2 | 2 | 0 | `testByteViewReadsRawBytes` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTwoViewsOfOneBufferAtDifferentTypes` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileRecurrence | 3 | 3 | 0 | `testDeltaRuleLongerSequence` PASS→CUDA CONFIGURATION UNSUPPORTED; `testDeltaRuleShortSequence` PASS→CUDA CONFIGURATION UNSUPPORTED; `testDeltaRuleSingleStep` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileRowKernels | 20 | 20 | 0 | `testGeglu` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGeluTanhApproximation` PASS→CUDA CONFIGURATION UNSUPPORTED; `testLayerNormFullRowTile` PASS→CUDA CONFIGURATION UNSUPPORTED; `testLayerNormNonPowerOfTwoRow` PASS→CUDA CONFIGURATION UNSUPPORTED; `testLayerNormSquare` PASS→CUDA CONFIGURATION UNSUPPORTED; `testRmsNormNonPowerOfTwoRow` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileShapes | 4 | 4 | 0 | `testBroadcastBiasRow` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBroadcastRowSumColumn` PASS→CUDA CONFIGURATION UNSUPPORTED; `testExtractSplitsAPackedRow` PASS→CUDA CONFIGURATION UNSUPPORTED; `testReshapeToRank1` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileStridedViews | 3 | 3 | 0 | `testDequantiseInterleavedGgufBlocks` PASS→CUDA CONFIGURATION UNSUPPORTED; `testPitchedRows` PASS→CUDA CONFIGURATION UNSUPPORTED; `testViewFromOffset` PASS→CUDA CONFIGURATION UNSUPPORTED |
| vectortypes.TestHalfFloats | 26 | 8 | 8 | `privateVectorHalf4` None→FAILED; `privateVectorHalf8` None→FAILED; `testInternalSetMethod02` None→FAILED; `testInternalSetMethod03` None→FAILED; `testInternalSetMethod04` None→FAILED; `testSimpleDotProductHalf16` None→FAILED … |
<!-- /tc-diff -->

## Differential run 2 (2026-10-10, fallback off)

After reductions, vector types, atomics, fields/instances and lambdas. No test that passed under TC
in run 1 fails in run 2.

<!-- tc-diff: generated by baseline/tc-diff.py; do not edit by hand -->
Run `run2`: 1692 tests in 223 classes, TC with fallback **off**. PASS: Graal 1516, TC 1188. Status differences: 348 (TC worse 329, TC better 1). Launch-configuration differences: 8. Kernels compiled by TC: 3331.

TC refusals (each fails its test with fallback off): TC-E1002 63, TC-E1003 51, TC-E3202 27, TC-E1006 2, TC-E2108 1, TC-E1004 1.

| Class | Tests | Graal PASS | TC PASS | Differences |
|---|---:|---:|---:|---|
| TestHello | 7 | 7 | 4 | `testHello` PASS→FAILED; `testPrintIntArray` PASS→FAILED; `testPrintIntArray2` PASS→FAILED |
| api.TestMultipleCopyOuts | 6 | 6 | 5 | `testBatchedCopyOut` PASS→FAILED |
| arrays.TestNewArrays | 7 | 5 | 5 | 1 launch config(s) differ |
| batches.TestBatches | 30 | 30 | 4 | `test100MB` PASS→FAILED; `test100MBLazy` PASS→FAILED; `test100MBSmall` PASS→FAILED; `test100MBSmallLazy` PASS→FAILED; `test300MB` PASS→FAILED; `test300MBLazy` PASS→FAILED … |
| bitsets.BitSetTests | 1 | 1 | 0 | `test01` PASS→FAILED |
| compute.ComputeTests | 19 | 17 | 16 | `testRenderTrack` PASS→FAILED |
| fails.BitOpsUnsupportedFail | 1 | 1 | 0 | `testReverseUnsupported` PASS→FAILED |
| fails.CodeFail | 3 | 3 | 2 | `codeFail01` PASS→FAILED |
| fails.DynamicArrayFail | 1 | 1 | 0 | `testDynamicSizedArrayAllocation` PASS→FAILED |
| fails.MultiDimArrayFail | 1 | 1 | 0 | `testMultiDimArrayDeclaration` PASS→FAILED |
| kernelcontext.api.TestAsyncCopyToLocal | 6 | 6 | 0 | `testAsyncCopyByteArray` PASS→FAILED; `testAsyncCopyFP8Array` PASS→FAILED; `testAsyncCopyHalfFloatArray` PASS→FAILED; `testAsyncCopyMultipleSlotsPerThread` PASS→FAILED; `testAsyncCopyVisibleAcrossLanes` PASS→FAILED; `testDoubleBufferedPipeline` PASS→FAILED |
| kernelcontext.api.TestCudaSamplesDynamicParallelism | 4 | 4 | 0 | `testCdpBezierTessellation` PASS→FAILED; `testCdpQuadtree` PASS→FAILED; `testCdpSimplePrint` PASS→FAILED; `testCdpSimpleQuicksort` PASS→FAILED |
| kernelcontext.api.TestDynamicParallelism | 15 | 15 | 2 | `testChildOnlyWrites` PASS→FAILED; `testFireAndForget` PASS→FAILED; `testInvalidLaunchDoesNotRunChild` PASS→FAILED; `testLaunch2D` PASS→FAILED; `testLaunchCycle` PASS→FAILED; `testLaunchPerBlock` PASS→FAILED … |
| kernelcontext.api.TestDynamicParallelismPipelines | 4 | 4 | 0 | `testDeviceDrivenConvergenceLoop` PASS→FAILED; `testJitChainUnderCUDAGraph` PASS→FAILED; `testLibraryChainUnderCUDAGraph` PASS→FAILED; `testRecursiveQuicksort` PASS→FAILED |
| kernelcontext.api.TestHalfFloatLocalArray | 1 | 1 | 0 | `testHalfFloatLocalArrayToFloat` PASS→FAILED |
| kernelcontext.matrices.TestMMAGlobalFragmentLoad | 6 | 6 | 0 | `testDeepK` PASS→FAILED; `testFourWarpsPerGroup` PASS→FAILED; `testMixedGlobalAndSharedFragments` PASS→FAILED; `testMultiTile` PASS→FAILED; `testRectangular` PASS→FAILED; `testSingleTile` PASS→FAILED |
| kernelcontext.matrices.TestMatrixMultiplicationMMACpAsync | 8 | 8 | 0 | `testGemmCpAsyncDeepK` PASS→FAILED; `testGemmCpAsyncIdentityB` PASS→FAILED; `testGemmCpAsyncMinimal` PASS→FAILED; `testGemmCpAsyncMultiTile` PASS→FAILED; `testGemmCpAsyncPrefillSlice` PASS→FAILED; `testGemmCpAsyncWideN` PASS→FAILED … |
| kernelcontext.reductions.TestLocalMemoryReductionsHalfFloats | 4 | 4 | 0 | `testHalfFloatReductionsAddLocalMemory01` PASS→FAILED; `testHalfFloatReductionsAddLocalMemory02` PASS→FAILED; `testHalfFloatReductionsMaxLocalMemory` PASS→FAILED; `testHalfFloatReductionsMinLocalMemory` PASS→FAILED |
| logic.TestEnumInKernel | 1 | 1 | 0 | `testEnumWithFieldAndMethodInKernel` PASS→FAILED |
| quantization.QuantizationTests | 7 | 7 | 3 | `testDP4A` PASS→FAILED; `testMatrixVector4WayDP4AKernel` PASS→FAILED; `testMatrixVectorDP4AKernelContext` PASS→FAILED; `testMatrixVectorDP4AKernelLocalMemory` PASS→FAILED |
| reductions.TestReductionsAutomatic | 4 | 4 | 4 | 7 launch config(s) differ |
| reductions.TestReductionsFloats | 13 | 12 | 13 | `testComputePi` FAILED→PASS |
| tasks.TestMultipleFunctions | 10 | 10 | 9 | `testVector01` PASS→FAILED |
| tile.TestTileAtomics | 6 | 6 | 0 | `testAccumulateRank1` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAccumulateRowsRank2` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAtomicSubtract` PASS→CUDA CONFIGURATION UNSUPPORTED; `testIntegerAtomics` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSplitKFourSplits` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSplitKTwoSplits` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileAttention | 7 | 7 | 0 | `testFlashAttentionGroupedQuery` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashAttentionLongerKeys` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashAttentionManyKvBlocks` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashAttentionMultiHead` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashAttentionSingleKvBlock` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSplitKReduceFourSplits` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileAttentionVariants | 8 | 8 | 0 | `testAttentionSinkDominant` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAttentionSinkNegligible` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashDecodeFourSplits` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashDecodeMultipleRowBlocks` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashDecodeOneBlockPerSplit` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashDecodeSingleSplit` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileChaining | 8 | 8 | 0 | `testChainUnderIntraPlanConcurrency` PASS→CUDA CONFIGURATION UNSUPPORTED; `testJitAndKernelContextAndTileAndLibrary` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSimtThenCuBlasOnly` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSimtThenTileThenCuBlasThenSimt` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTileKernelUnderCudaGraph` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTileProducerCuBlasConsumerAcrossGraphs` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileDTypes | 8 | 8 | 0 | `testDoubleMatmul` PASS→CUDA CONFIGURATION UNSUPPORTED; `testDoubleRowSum` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFp8RefusedBelowHopper` PASS→CUDA CONFIGURATION UNSUPPORTED; `testInt8Absolute` PASS→CUDA CONFIGURATION UNSUPPORTED; `testInt8MatmulDequantised` PASS→CUDA CONFIGURATION UNSUPPORTED; `testInt8MatmulRectangular` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileElementwise | 8 | 8 | 0 | `testFull` PASS→CUDA CONFIGURATION UNSUPPORTED; `testIota` PASS→CUDA CONFIGURATION UNSUPPORTED; `testNarrowingCastRoundTrip` PASS→CUDA CONFIGURATION UNSUPPORTED; `testRaggedEdge` PASS→CUDA CONFIGURATION UNSUPPORTED; `testScaledAdd` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTranspose` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileGemmVariants | 9 | 9 | 0 | `testFusedGluMlp` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMatmulTransposedA` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMatmulTransposedB` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMatmulTransposedBRectangular` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMatmulTransposedBoth` PASS→CUDA CONFIGURATION UNSUPPORTED; `testPersistentMatmulFewerBlocksThanTiles` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileLlmKernels | 4 | 4 | 0 | `testBmmLargerBatch` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBmmSmallBatch` PASS→CUDA CONFIGURATION UNSUPPORTED; `testDropoutWithMask` PASS→CUDA CONFIGURATION UNSUPPORTED; `testRope` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileMasking | 10 | 10 | 0 | `testCausalLongerSequence` PASS→CUDA CONFIGURATION UNSUPPORTED; `testCausalSeveralBlocks` PASS→CUDA CONFIGURATION UNSUPPORTED; `testCausalSingleBlock` PASS→CUDA CONFIGURATION UNSUPPORTED; `testClamp` PASS→CUDA CONFIGURATION UNSUPPORTED; `testCountAboveThreshold` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMinAndProduct` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileMathOps | 6 | 6 | 0 | `testAtomicLoadAndStore` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBinaryMath` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBitwise` PASS→CUDA CONFIGURATION UNSUPPORTED; `testPredicates` PASS→CUDA CONFIGURATION UNSUPPORTED; `testScansShiftsBitcastAndConcat` PASS→CUDA CONFIGURATION UNSUPPORTED; `testUnaryMath` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileMatmul | 11 | 11 | 0 | `testGemm128` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemm64` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemmKLoopAuto` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemmKLoopRolled` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemmKLoopUnrolled` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemmLongKLoop` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileMixedPipelines | 5 | 5 | 0 | `testCutlassEpilogueThenTile` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFourMixedChainsConcurrently` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTileBetweenTwoLibraryCalls` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTransformerBlock` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTransformerBlockUnderCudaGraph` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileOpLevel | 106 | 98 | 0 | `testAbs` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAdd` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAtan2` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAtomicLoadStore` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBf16Add` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBf16Div` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileRank3 | 4 | 4 | 0 | `testBatchedMatmulLarger` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBatchedMatmulSingleBatch` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBatchedMatmulSmall` PASS→CUDA CONFIGURATION UNSUPPORTED; `testScaleByBatch` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileRawBuffers | 2 | 2 | 0 | `testByteViewReadsRawBytes` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTwoViewsOfOneBufferAtDifferentTypes` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileRecurrence | 3 | 3 | 0 | `testDeltaRuleLongerSequence` PASS→CUDA CONFIGURATION UNSUPPORTED; `testDeltaRuleShortSequence` PASS→CUDA CONFIGURATION UNSUPPORTED; `testDeltaRuleSingleStep` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileRowKernels | 20 | 20 | 0 | `testGeglu` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGeluTanhApproximation` PASS→CUDA CONFIGURATION UNSUPPORTED; `testLayerNormFullRowTile` PASS→CUDA CONFIGURATION UNSUPPORTED; `testLayerNormNonPowerOfTwoRow` PASS→CUDA CONFIGURATION UNSUPPORTED; `testLayerNormSquare` PASS→CUDA CONFIGURATION UNSUPPORTED; `testRmsNormNonPowerOfTwoRow` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileShapes | 4 | 4 | 0 | `testBroadcastBiasRow` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBroadcastRowSumColumn` PASS→CUDA CONFIGURATION UNSUPPORTED; `testExtractSplitsAPackedRow` PASS→CUDA CONFIGURATION UNSUPPORTED; `testReshapeToRank1` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileStridedViews | 3 | 3 | 0 | `testDequantiseInterleavedGgufBlocks` PASS→CUDA CONFIGURATION UNSUPPORTED; `testPitchedRows` PASS→CUDA CONFIGURATION UNSUPPORTED; `testViewFromOffset` PASS→CUDA CONFIGURATION UNSUPPORTED |
| vectortypes.TestDoubles | 18 | 13 | 11 | `privateVectorDouble2` PASS→FAILED; `privateVectorDouble4` PASS→FAILED |
| vectortypes.TestFloats | 31 | 20 | 18 | `privateVectorFloat2` PASS→FAILED; `privateVectorFloat4` PASS→FAILED |
| vectortypes.TestHalf2Packed | 7 | 7 | 0 | `testDotRowsFloatAccumulate` PASS→FAILED; `testLocalTileDot` PASS→FAILED; `testPackRotatedPairs` PASS→FAILED; `testPackedAdd` PASS→FAILED; `testPackedCopy` PASS→FAILED; `testPackedFma` PASS→FAILED … |
| vectortypes.TestHalfFloats | 26 | 8 | 4 | `privateVectorHalf2` PASS→FAILED; `privateVectorHalf4` None→FAILED; `privateVectorHalf8` None→FAILED; `testInternalSetMethod01` PASS→FAILED; `testInternalSetMethod02` None→FAILED; `testInternalSetMethod03` None→FAILED … |
| vectortypes.TestInts | 26 | 19 | 17 | `privateVectorInt2` PASS→FAILED; `privateVectorInt4` PASS→FAILED |
<!-- /tc-diff -->

Since run 2: TC raises TornadoVM's exceptions for invalid kernels (`ErrorCodes`: invalid kernel,
unsupported, bailout, internal); the `fails` family matches Graal except the accepted
`BitOpsUnsupportedFail` difference, and zero-length local arrays and object allocations behave as on
Graal (`codeFail01`, `codeFail02`).

Family runs since run 2, fallback off (Graal reference: the run-2 Graal logs):

| Family | Tests | Graal PASS | TC PASS | Differences | What closed the gap |
|---|---:|---:|---:|---:|---|
| batches | 30 | 30 | 30 | 0 | per-chunk lengths for native and Java arrays, global index offset (`BatchOffset`) |
| kernelcontext | 228 | 203 | 203 | 0 (launch configs: 0) | cp.async, MMA fragments from global memory, local `HalfFloat[]` after a merge, dynamic parallelism (`KernelContext.launch`); 255 kernels compiled by TC ([baseline/tc-diff-kernelcontext-kc4.json](baseline/tc-diff-kernelcontext-kc4.json)) |

## Differential run 1 (`baseline/tc-diff.py`, 2026-10-10, fallback off)

All 223 suite classes except the memory-leak loops, each run under Graal and under TC with
**fallback off** (a kernel TC refuses fails its test), with the class's own suite parameters.
Compared: per-test status, and per task the ordered launch configurations. This is the C6 baseline;
later runs replace it.

<!-- tc-diff: generated by baseline/tc-diff.py; do not edit by hand -->
Run `run1`: 1692 tests in 223 classes, TC with fallback **off**. PASS: Graal 1516, TC 1024. Status differences: 510 (TC worse 492, TC better 0). Launch-configuration differences: 1. Kernels compiled by TC: 3064.

TC refusals (each fails its test with fallback off): TC-E1002 131, TC-E1003 63, TC-E3201 62, TC-E3202 27, TC-E1005 16, TC-E3101 5, TC-E1006 2, TC-E2108 1, TC-E1004 1, TC-E2107 1.

| Class | Tests | Graal PASS | TC PASS | Differences |
|---|---:|---:|---:|---|
| TestHello | 7 | 7 | 2 | `testHello` PASS→FAILED; `testPrintIntArray` PASS→FAILED; `testPrintIntArray2` PASS→FAILED; `testSimpleCompute` PASS→FAILED; `testSimpleCompute2` PASS→FAILED |
| api.TestMultipleCopyOuts | 6 | 6 | 5 | `testBatchedCopyOut` PASS→FAILED |
| arrays.TestNewArrays | 7 | 5 | 5 | 1 launch config(s) differ |
| atomics.TestAtomics | 27 | 26 | 8 | `testAtomic04` PASS→FAILED; `testAtomic04Get` PASS→FAILED; `testAtomic06` PASS→FAILED; `testAtomic07` PASS→FAILED; `testAtomic08_decrementAndGet` PASS→FAILED; `testAtomic09` PASS→FAILED … |
| atomics.TestAtomicsWithReduction | 1 | 1 | 0 | `testReductionAndAtomicInSameTaskGraph` PASS→FAILED |
| batches.TestBatches | 30 | 30 | 4 | `test100MB` PASS→FAILED; `test100MBLazy` PASS→FAILED; `test100MBSmall` PASS→FAILED; `test100MBSmallLazy` PASS→FAILED; `test300MB` PASS→FAILED; `test300MBLazy` PASS→FAILED … |
| bitsets.BitSetTests | 1 | 1 | 0 | `test01` PASS→FAILED |
| compute.ComputeTests | 19 | 17 | 14 | `matrixVectorFloat4` PASS→FAILED; `testDFTVectorTypes` PASS→FAILED; `testRenderTrack` PASS→FAILED |
| fails.BitOpsUnsupportedFail | 1 | 1 | 0 | `testReverseUnsupported` PASS→FAILED |
| fails.CodeFail | 3 | 3 | 2 | `codeFail01` PASS→FAILED |
| fails.DynamicArrayFail | 1 | 1 | 0 | `testDynamicSizedArrayAllocation` PASS→FAILED |
| fails.MultiDimArrayFail | 1 | 1 | 0 | `testMultiDimArrayDeclaration` PASS→FAILED |
| fields.TestFields | 9 | 9 | 0 | `testFields01` PASS→FAILED; `testFields02` PASS→FAILED; `testFields03` PASS→FAILED; `testFieldsLazyCopyout` PASS→FAILED; `testFieldsPartialCopyout` PASS→FAILED; `testFieldsPartialLazyCopyout` PASS→FAILED … |
| fields.TestInheritedFields | 4 | 4 | 0 | `testIncrementPrimitiveAB` PASS→FAILED; `testIncrementPrimitiveB` PASS→FAILED; `testIncrementTornadoAB` PASS→FAILED; `testIncrementTornadoB` PASS→FAILED |
| functional.TestLambdas | 4 | 4 | 3 | `testParameterUnboxing` PASS→FAILED |
| grid.TestIrregularGridSizes | 4 | 4 | 3 | `testReductionAtPrimeSize` PASS→FAILED |
| images.TestImages | 18 | 17 | 6 | `testImageFloat01` PASS→FAILED; `testImageFloat02` PASS→FAILED; `testImageFloat03` PASS→FAILED; `testImageFloat07` PASS→FAILED; `testImageFloat08` PASS→FAILED; `testImageFloat09` PASS→FAILED … |
| instances.TestInstances | 2 | 2 | 0 | `testInit` PASS→FAILED; `testThis` PASS→FAILED |
| kernelcontext.api.TestAsyncCopyToLocal | 6 | 6 | 0 | `testAsyncCopyByteArray` PASS→FAILED; `testAsyncCopyFP8Array` PASS→FAILED; `testAsyncCopyHalfFloatArray` PASS→FAILED; `testAsyncCopyMultipleSlotsPerThread` PASS→FAILED; `testAsyncCopyVisibleAcrossLanes` PASS→FAILED; `testDoubleBufferedPipeline` PASS→FAILED |
| kernelcontext.api.TestCudaSamplesDynamicParallelism | 4 | 4 | 0 | `testCdpBezierTessellation` PASS→FAILED; `testCdpQuadtree` PASS→FAILED; `testCdpSimplePrint` PASS→FAILED; `testCdpSimpleQuicksort` PASS→FAILED |
| kernelcontext.api.TestDynamicParallelism | 15 | 15 | 2 | `testChildOnlyWrites` PASS→FAILED; `testFireAndForget` PASS→FAILED; `testInvalidLaunchDoesNotRunChild` PASS→FAILED; `testLaunch2D` PASS→FAILED; `testLaunchCycle` PASS→FAILED; `testLaunchPerBlock` PASS→FAILED … |
| kernelcontext.api.TestDynamicParallelismPipelines | 4 | 4 | 0 | `testDeviceDrivenConvergenceLoop` PASS→FAILED; `testJitChainUnderCUDAGraph` PASS→FAILED; `testLibraryChainUnderCUDAGraph` PASS→FAILED; `testRecursiveQuicksort` PASS→FAILED |
| kernelcontext.api.TestHalfFloatLocalArray | 1 | 1 | 0 | `testHalfFloatLocalArrayToFloat` PASS→FAILED |
| kernelcontext.matrices.TestMMAGlobalFragmentLoad | 6 | 6 | 0 | `testDeepK` PASS→FAILED; `testFourWarpsPerGroup` PASS→FAILED; `testMixedGlobalAndSharedFragments` PASS→FAILED; `testMultiTile` PASS→FAILED; `testRectangular` PASS→FAILED; `testSingleTile` PASS→FAILED |
| kernelcontext.matrices.TestMatrixMultiplicationMMACpAsync | 8 | 8 | 0 | `testGemmCpAsyncDeepK` PASS→FAILED; `testGemmCpAsyncIdentityB` PASS→FAILED; `testGemmCpAsyncMinimal` PASS→FAILED; `testGemmCpAsyncMultiTile` PASS→FAILED; `testGemmCpAsyncPrefillSlice` PASS→FAILED; `testGemmCpAsyncWideN` PASS→FAILED … |
| kernelcontext.reductions.TestLocalMemoryReductionsHalfFloats | 4 | 4 | 0 | `testHalfFloatReductionsAddLocalMemory01` PASS→FAILED; `testHalfFloatReductionsAddLocalMemory02` PASS→FAILED; `testHalfFloatReductionsMaxLocalMemory` PASS→FAILED; `testHalfFloatReductionsMinLocalMemory` PASS→FAILED |
| logic.TestEnumInKernel | 1 | 1 | 0 | `testEnumWithFieldAndMethodInKernel` PASS→FAILED |
| matrices.TestMatrixTypes | 35 | 35 | 28 | `testMatrix11` PASS→FAILED; `testMatrix12` PASS→FAILED; `testMatrix13` PASS→FAILED; `testMatrix14` PASS→FAILED; `testMatrix15` PASS→FAILED; `testMatrix16` PASS→FAILED … |
| profiler.TestProfiler | 9 | 9 | 4 | `testKernelOnAndOff` PASS→FAILED; `testProfilerReduction` PASS→FAILED; `testProfilerReductionOffAndOn` PASS→FAILED; `testProfilerReductionOnAndOff` PASS→FAILED; `testThreadInfoOnAndOff` PASS→FAILED |
| quantization.QuantizationTests | 7 | 7 | 3 | `testDP4A` PASS→FAILED; `testMatrixVector4WayDP4AKernel` PASS→FAILED; `testMatrixVectorDP4AKernelContext` PASS→FAILED; `testMatrixVectorDP4AKernelLocalMemory` PASS→FAILED |
| reductions.InstanceReduction | 1 | 1 | 0 | `testReductionInstanceClass` PASS→FAILED |
| reductions.MultipleReductions | 1 | 1 | 0 | `test` PASS→FAILED |
| reductions.TestReductionsAutomatic | 4 | 4 | 0 | `testIrregularSize01` PASS→FAILED; `testIrregularSize02` PASS→FAILED; `testIrregularSize03` PASS→FAILED; `testIrregularSize04` PASS→FAILED |
| reductions.TestReductionsDoubles | 17 | 16 | 0 | `testMaxReduction` PASS→FAILED; `testMaxReduction2` PASS→FAILED; `testMinReduction` PASS→FAILED; `testMinReduction2` PASS→FAILED; `testMultDoubles` PASS→FAILED; `testMultipleReductions` PASS→FAILED … |
| reductions.TestReductionsFloats | 13 | 12 | 0 | `testComputeAverage` PASS→FAILED; `testIntegrate` PASS→FAILED; `testMaxReduction` PASS→FAILED; `testMaxReduction2` PASS→FAILED; `testMinReduction` PASS→FAILED; `testMinReduction2` PASS→FAILED … |
| reductions.TestReductionsIntegers | 15 | 15 | 1 | `testMapReduce` PASS→FAILED; `testMapReduce3` PASS→FAILED; `testMaxReduction` PASS→FAILED; `testMaxReduction2` PASS→FAILED; `testMinReduction` PASS→FAILED; `testMinReduction2` PASS→FAILED … |
| reductions.TestReductionsLong | 6 | 6 | 0 | `testMaxReduction` PASS→FAILED; `testMaxReduction2` PASS→FAILED; `testMinReduction` PASS→FAILED; `testMinReduction2` PASS→FAILED; `testMultiplicationReduction` PASS→FAILED; `testReductionSum` PASS→FAILED |
| tasks.TestMultipleFunctions | 10 | 10 | 6 | `testMultipleTasks` PASS→FAILED; `testMultipleTasksMultipleCallees` PASS→FAILED; `testSingleTask` PASS→FAILED; `testVector01` PASS→FAILED |
| tile.TestTileAtomics | 6 | 6 | 0 | `testAccumulateRank1` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAccumulateRowsRank2` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAtomicSubtract` PASS→CUDA CONFIGURATION UNSUPPORTED; `testIntegerAtomics` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSplitKFourSplits` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSplitKTwoSplits` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileAttention | 7 | 7 | 0 | `testFlashAttentionGroupedQuery` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashAttentionLongerKeys` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashAttentionManyKvBlocks` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashAttentionMultiHead` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashAttentionSingleKvBlock` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSplitKReduceFourSplits` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileAttentionVariants | 8 | 8 | 0 | `testAttentionSinkDominant` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAttentionSinkNegligible` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashDecodeFourSplits` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashDecodeMultipleRowBlocks` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashDecodeOneBlockPerSplit` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFlashDecodeSingleSplit` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileChaining | 8 | 8 | 0 | `testChainUnderIntraPlanConcurrency` PASS→CUDA CONFIGURATION UNSUPPORTED; `testJitAndKernelContextAndTileAndLibrary` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSimtThenCuBlasOnly` PASS→CUDA CONFIGURATION UNSUPPORTED; `testSimtThenTileThenCuBlasThenSimt` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTileKernelUnderCudaGraph` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTileProducerCuBlasConsumerAcrossGraphs` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileDTypes | 8 | 8 | 0 | `testDoubleMatmul` PASS→CUDA CONFIGURATION UNSUPPORTED; `testDoubleRowSum` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFp8RefusedBelowHopper` PASS→CUDA CONFIGURATION UNSUPPORTED; `testInt8Absolute` PASS→CUDA CONFIGURATION UNSUPPORTED; `testInt8MatmulDequantised` PASS→CUDA CONFIGURATION UNSUPPORTED; `testInt8MatmulRectangular` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileElementwise | 8 | 8 | 0 | `testFull` PASS→CUDA CONFIGURATION UNSUPPORTED; `testIota` PASS→CUDA CONFIGURATION UNSUPPORTED; `testNarrowingCastRoundTrip` PASS→CUDA CONFIGURATION UNSUPPORTED; `testRaggedEdge` PASS→CUDA CONFIGURATION UNSUPPORTED; `testScaledAdd` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTranspose` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileGemmVariants | 9 | 9 | 0 | `testFusedGluMlp` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMatmulTransposedA` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMatmulTransposedB` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMatmulTransposedBRectangular` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMatmulTransposedBoth` PASS→CUDA CONFIGURATION UNSUPPORTED; `testPersistentMatmulFewerBlocksThanTiles` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileLlmKernels | 4 | 4 | 0 | `testBmmLargerBatch` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBmmSmallBatch` PASS→CUDA CONFIGURATION UNSUPPORTED; `testDropoutWithMask` PASS→CUDA CONFIGURATION UNSUPPORTED; `testRope` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileMasking | 10 | 10 | 0 | `testCausalLongerSequence` PASS→CUDA CONFIGURATION UNSUPPORTED; `testCausalSeveralBlocks` PASS→CUDA CONFIGURATION UNSUPPORTED; `testCausalSingleBlock` PASS→CUDA CONFIGURATION UNSUPPORTED; `testClamp` PASS→CUDA CONFIGURATION UNSUPPORTED; `testCountAboveThreshold` PASS→CUDA CONFIGURATION UNSUPPORTED; `testMinAndProduct` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileMathOps | 6 | 6 | 0 | `testAtomicLoadAndStore` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBinaryMath` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBitwise` PASS→CUDA CONFIGURATION UNSUPPORTED; `testPredicates` PASS→CUDA CONFIGURATION UNSUPPORTED; `testScansShiftsBitcastAndConcat` PASS→CUDA CONFIGURATION UNSUPPORTED; `testUnaryMath` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileMatmul | 11 | 11 | 0 | `testGemm128` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemm64` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemmKLoopAuto` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemmKLoopRolled` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemmKLoopUnrolled` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGemmLongKLoop` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileMixedPipelines | 5 | 5 | 0 | `testCutlassEpilogueThenTile` PASS→CUDA CONFIGURATION UNSUPPORTED; `testFourMixedChainsConcurrently` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTileBetweenTwoLibraryCalls` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTransformerBlock` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTransformerBlockUnderCudaGraph` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileOpLevel | 106 | 98 | 0 | `testAbs` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAdd` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAtan2` PASS→CUDA CONFIGURATION UNSUPPORTED; `testAtomicLoadStore` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBf16Add` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBf16Div` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileRank3 | 4 | 4 | 0 | `testBatchedMatmulLarger` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBatchedMatmulSingleBatch` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBatchedMatmulSmall` PASS→CUDA CONFIGURATION UNSUPPORTED; `testScaleByBatch` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileRawBuffers | 2 | 2 | 0 | `testByteViewReadsRawBytes` PASS→CUDA CONFIGURATION UNSUPPORTED; `testTwoViewsOfOneBufferAtDifferentTypes` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileRecurrence | 3 | 3 | 0 | `testDeltaRuleLongerSequence` PASS→CUDA CONFIGURATION UNSUPPORTED; `testDeltaRuleShortSequence` PASS→CUDA CONFIGURATION UNSUPPORTED; `testDeltaRuleSingleStep` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileRowKernels | 20 | 20 | 0 | `testGeglu` PASS→CUDA CONFIGURATION UNSUPPORTED; `testGeluTanhApproximation` PASS→CUDA CONFIGURATION UNSUPPORTED; `testLayerNormFullRowTile` PASS→CUDA CONFIGURATION UNSUPPORTED; `testLayerNormNonPowerOfTwoRow` PASS→CUDA CONFIGURATION UNSUPPORTED; `testLayerNormSquare` PASS→CUDA CONFIGURATION UNSUPPORTED; `testRmsNormNonPowerOfTwoRow` PASS→CUDA CONFIGURATION UNSUPPORTED … |
| tile.TestTileShapes | 4 | 4 | 0 | `testBroadcastBiasRow` PASS→CUDA CONFIGURATION UNSUPPORTED; `testBroadcastRowSumColumn` PASS→CUDA CONFIGURATION UNSUPPORTED; `testExtractSplitsAPackedRow` PASS→CUDA CONFIGURATION UNSUPPORTED; `testReshapeToRank1` PASS→CUDA CONFIGURATION UNSUPPORTED |
| tile.TestTileStridedViews | 3 | 3 | 0 | `testDequantiseInterleavedGgufBlocks` PASS→CUDA CONFIGURATION UNSUPPORTED; `testPitchedRows` PASS→CUDA CONFIGURATION UNSUPPORTED; `testViewFromOffset` PASS→CUDA CONFIGURATION UNSUPPORTED |
| vectortypes.TestDoubles | 18 | 13 | 2 | `privateVectorDouble2` PASS→FAILED; `privateVectorDouble4` PASS→FAILED; `testDoubleAdd2` PASS→FAILED; `testDoubleAdd3` PASS→FAILED; `testDoubleAdd4` PASS→FAILED; `testInternalSetMethod01` PASS→FAILED … |
| vectortypes.TestFloats | 31 | 20 | 2 | `privateVectorFloat2` PASS→FAILED; `privateVectorFloat4` PASS→FAILED; `testInternalSetMethod01` PASS→FAILED; `testInternalSetMethod02` PASS→FAILED; `testInternalSetMethod03` PASS→FAILED; `testSimpleDotProductFloat2` PASS→FAILED … |
| vectortypes.TestHalf2Packed | 7 | 7 | 0 | `testDotRowsFloatAccumulate` PASS→FAILED; `testLocalTileDot` PASS→FAILED; `testPackRotatedPairs` PASS→FAILED; `testPackedAdd` PASS→FAILED; `testPackedCopy` PASS→FAILED; `testPackedFma` PASS→FAILED … |
| vectortypes.TestHalfFloats | 26 | 8 | 4 | `privateVectorHalf2` PASS→FAILED; `privateVectorHalf4` None→FAILED; `privateVectorHalf8` None→FAILED; `testInternalSetMethod01` PASS→FAILED; `testInternalSetMethod02` None→FAILED; `testInternalSetMethod03` None→FAILED … |
| vectortypes.TestInts | 26 | 19 | 2 | `privateVectorInt2` PASS→FAILED; `privateVectorInt4` PASS→FAILED; `testAddInt2` PASS→FAILED; `testAddInt3` PASS→FAILED; `testAddInt4` PASS→FAILED; `testInternalSetMethod01` PASS→FAILED … |
| vectortypes.TestVectorAllocation | 3 | 3 | 0 | `testAllocation1` PASS→FAILED; `testAllocation2` PASS→FAILED; `testAllocation3` PASS→FAILED |
<!-- /tc-diff -->

Launch-configuration differences after review: none caused by TC. `arrays.TestNewArrays`
(`initializeToOne`, a loop without `@Parallel`): TC launches one thread, Graal 256 threads that each run
the whole loop - TC matches Java (**accepted difference**). Classes with randomised sizes
(`TestReductionsAutomatic`) and graphs whose generated-name counter shifts after a refusal differ
in launch configuration by construction.

### Since run 1

- `@Reduce` reductions (`ReductionLowering`): `reductions` 54/57 under TC (Graal 55/57); `TestReductionsFloats#testComputePi` passes under TC and fails under Graal. Open: `InstanceReduction` (instance-method task), `TestAtomicsWithReduction` (`AtomicInteger`).
- TornadoVM profiler: TC has its own timers (`TASK_COMPILE_TC_TIME`, `TOTAL_TC_COMPILE_TIME`); `getTornadoCompilerTime()` is Graal + TC. `TestProfiler` 9/9 under TC with fallback off.

## Coverage: kernels TC compiles, per family (run 1)

`TC share` = kernel compilations by TC / all compilations of the family; the rest fell back to Graal
with a diagnostic. This is the Phase 6 work list.

| Family | Graal PASS (baseline) | TC kernels | fell back to Graal | TC share |
|---|---:|---:|---:|---:|
| tasks | 32 | 1533 | 14 | 99% |
| multithreaded | 5 | 420 | 0 | 100% |
| tile | 224 | 21 | 261 | 7% |
| kernelcontext | 203 | 97 | 160 | 37% |
| api | 135 | 224 | 0 | 100% |
| arrays | 129 | 138 | 0 | 100% |
| reductions | 55 | 125 | 1 | 99% |
| vectortypes | 71 | 12 | 79 | 13% |
| math | 75 | 76 | 0 | 100% |
| foundation | 40 | 42 | 5 | 89% |
| branching | 43 | 34 | 9 | 79% |
| streams | 13 | 38 | 0 | 100% |
| numpromotion | 46 | 36 | 0 | 100% |
| loops | 33 | 30 | 3 | 90% |
| codegen | 31 | 33 | 0 | 100% |
| compute | 31 | 30 | 3 | 90% |
| atomics | 27 | 2 | 27 | 6% |
| matrices | 35 | 0 | 27 | 0% |
| grid | 12 | 18 | 1 | 94% |
| logic | 19 | 18 | 1 | 94% |
| images | 19 | 0 | 18 | 0% |
| profiler | 11 | 16 | 0 | 100% |
| quantization | 7 | 12 | 4 | 75% |
| fields | 13 | 0 | 15 | 0% |
| executor | 7 | 10 | 0 | 100% |
| compiler | 7 | 9 | 0 | 100% |
| cublas | 34 | 7 | 0 | 100% |
| cutlass | 25 | 6 | 0 | 100% |
| fails | 11 | 2 | 4 | 33% |
| memory | 15 | 5 | 0 | 100% |
| dynsize | 2 | 5 | 0 | 100% |
| functional | 5 | 3 | 1 | 75% |
| pointers | 2 | 4 | 0 | 100% |
| prebuilt | 7 | 3 | 0 | 100% |
| cufft | 8 | 3 | 0 | 100% |
| nvtx | 4 | 2 | 0 | 100% |
| cusparse | 16 | 2 | 0 | 100% |
| instances | 2 | 0 | 2 | 0% |
| warmup | 2 | 2 | 0 | 100% |
| vm | 2 | 2 | 0 | 100% |
| cudnn | 11 | 1 | 0 | 100% |
| temporary | 1 | 1 | 0 | 100% |
| batches | 30 | 1 | 0 | 100% |
| lambdas | 1 | 1 | 0 | 100% |
| flatmap | 1 | 1 | 0 | 100% |
| bitsets | 1 | 0 | 1 | 0% |
| parameters | 3 | 1 | 0 | 100% |

(Reductions now fall back by design until the reduction rewrite lands.)

## Fallback reasons, ranked (run 1)

| Kernels | Reason | Family / plan item |
|---:|---|---|
| 245 + 4 | `TileContext.view` / `viewStrided` | tile (CUDA Tile mode last) |
| 44 + 10 + 6 | `KernelContext.mmaFragment` / `mmaFragmentInt` / `mmaLoadAInt8` | kernelcontext (tensor cores) |
| 27 | object allocation (`new`) other than HalfFloat | vectortypes / fields |
| 26 | instance-method tasks (TC-E1005) | lambdas / instances |
| 20 + 18 + 4 | `simdSum` / `simdShuffleDown` / `simdBallot` | kernelcontext (warp intrinsics) |
| 17 | static fields of test classes | fields |
| 13 + 13 + 5 | `AtomicInteger`, `KernelContext.atomicAdd` / `atomicMax` | atomics |
| 10 | `tile.DType` enum constants | tile |
| 8 | `KernelContext.asyncCopyToLocal` | kernelcontext |
| 7 + 6 + 5 + 4 + 4 | `Matrix2D*.get`, `getNumRows`, `Matrix3DFloat4` | matrices (object fields: `set` done) |
| 6 | `ImageFloat` | images |
| 6 + 5 + 4 + 4 + 4 | `VectorHalf`, `VectorInt2/3`, `VectorFloat3/4` | vectortypes |
| 5 | arrays of references | vectortypes |

## Order (plan): kernelcontext → reductions → vectortypes → atomics → fields/instances → lambdas → tile.
