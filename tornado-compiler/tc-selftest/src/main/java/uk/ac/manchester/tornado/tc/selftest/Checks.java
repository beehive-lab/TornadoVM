/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.tc.selftest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.FP8;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/**
 * The checks of {@code tornado-selftest}, in the order they run. Each compiles a Java kernel with TC
 * through TornadoVM's normal task path (the backend checks start from TC's IR instead, see
 * {@link BackendChecks}), runs it on the device and compares every element with the same method run
 * on the JVM. Floating-point results must match bit for bit unless a check says otherwise.
 */
final class Checks {

    private static final int N = 1 << 16;

    private Checks() {
    }

    static List<Check> all() {
        var checks = new ArrayList<Check>(BackendChecks.all());
        checks.addAll(List.of(
                // ---- core: what every TornadoVM program uses
                Check.of("core", "parallel-1d", "@Parallel loop, grid-stride (fewer threads than elements)", Checks::parallel1d),
                Check.of("core", "parallel-2d", "nested @Parallel loops with scalar bounds", Checks::parallel2d),
                Check.of("core", "branches", "divergent if/else and sqrt in a parallel loop", Checks::branches),
                Check.of("core", "local-memory", "KernelContext work-group reduction in local memory with barriers", Checks::localMemory),
                Check.of("core", "reduce", "@Reduce sum across work-groups", Checks::reduce),
                Check.of("core", "atomics", "global atomicAdd from every thread", Checks::atomics),
                // ---- types
                Check.of("types", "int64", "64-bit integer arithmetic with Java wrap-around", Checks::int64),
                new Check("types", "fp64", "double precision", TargetCaps::fp64, "double precision", Checks::fp64),
                new Check("types", "fp16", "half precision, rounded per operation as HalfFloat does", caps -> caps.fp16() || caps.dialect() == TargetCaps.Dialect.OPENCL, "half precision",
                        Checks::fp16),
                // ---- Java semantics where C differs
                Check.of("java", "integers", "MIN_VALUE / -1, remainders, shifts and overflow as Java defines them", Checks::integers),
                Check.of("java", "conversions", "float to int/long: NaN is 0, out-of-range values saturate", Checks::conversions),
                Check.of("java", "min-max", "Math.min/max with NaN and signed zeros", Checks::minMax),
                Check.of("java", "private-arrays", "new int[] in a loop starts from zero on every iteration", Checks::privateArrays),
                // ---- strict floating point
                Check.of("strict-fp", "ieee-edges", "withStrictFloatingPoint(): denormals, NaN and signed zeros as on the JVM", Checks::ieeeEdges),
                // ---- device features
                new Check("device", "warp-sum", "32-lane warp reduction (KernelContext.simdSum)", caps -> caps.warpSize() == 32, "32-lane warps", Checks::warpSum),
                new Check("device", "device-launch", "a kernel launching a kernel (KernelContext.launch)", TargetCaps::deviceLaunch, "device-side launches (CUDA dynamic parallelism)",
                        Checks::deviceLaunch),
                new Check("tensor-cores", "mma-f16", "F16 tensor-core GEMM (mma.sync m16n8k16)", TargetCaps::mma, "tensor cores (CUDA sm_80+)", Checks::mmaF16),
                new Check("tensor-cores", "mma-fp8", "FP8 e4m3 tensor-core GEMM (mma.sync m16n8k32)", TargetCaps::fp8Mma, "FP8 tensor cores (CUDA sm_89+, NVRTC 12.4+)",
                        Checks::mmaFp8)));
        return checks;
    }

    // ---------------------------------------------------------------- running

    /** Runs {@code graph} once on {@code device}; {@code grid} (may be null) is the worker grid of task {@code graph.t}. */
    static void run(TornadoDevice device, TaskGraph graph, WorkerGrid grid, boolean strict) throws Exception {
        try (var plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withDevice(device);
            if (grid != null) {
                plan.withGridScheduler(new GridScheduler(graph.getTaskGraphName() + ".t", grid));
            }
            if (strict) {
                plan.withStrictFloatingPoint();
            }
            plan.execute();
        }
    }

    static WorkerGrid grid1d(int global, int local) {
        var grid = new WorkerGrid1D(global);
        grid.setLocalWork(local, 1, 1);
        return grid;
    }

    // ---------------------------------------------------------------- comparisons

    static void same(float expected, float actual, String where) {
        if (Float.floatToRawIntBits(expected) != Float.floatToRawIntBits(actual) && !(Float.isNaN(expected) && Float.isNaN(actual))) {
            throw new AssertionError(where + ": expected " + expected + " (0x" + Integer.toHexString(Float.floatToRawIntBits(expected)) + "), got " + actual + " (0x"
                    + Integer.toHexString(Float.floatToRawIntBits(actual)) + ")");
        }
    }

    static void close(float expected, float actual, float relative, String where) {
        if (!(Math.abs(expected - actual) <= relative * Math.max(1f, Math.abs(expected)))) {
            throw new AssertionError(where + ": expected " + expected + ", got " + actual);
        }
    }

    static void same(long expected, long actual, String where) {
        if (expected != actual) {
            throw new AssertionError(where + ": expected " + expected + ", got " + actual);
        }
    }

    private static FloatArray floats(int n, java.util.function.IntToDoubleFunction f) {
        var a = new FloatArray(n);
        for (int i = 0; i < n; i++) {
            a.set(i, (float) f.applyAsDouble(i));
        }
        return a;
    }

    /** Values where Java and C (or fast math) part ways. */
    private static final float[] EDGES = { 0.0f, -0.0f, 1.0f, -1.5f, Float.MIN_VALUE, -Float.MIN_VALUE, 1.0e-40f, Float.MAX_VALUE, -Float.MAX_VALUE, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NaN, 2.5e9f, -2.5e9f, 0.49999997f, 3.4e38f };

    // ---------------------------------------------------------------- core

    private static String parallel1d(TornadoDevice device, TargetCaps caps) throws Exception {
        var a = floats(N, i -> i * 0.5);
        var b = floats(N, i -> 1.0 - i);
        var c = new FloatArray(N);
        var graph = new TaskGraph("parallel1d").transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b).task("t", Kernels::vectorAdd, a, b, c)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        run(device, graph, grid1d(N / 4, 64), false);
        for (int i = 0; i < N; i++) {
            same(a.get(i) + b.get(i), c.get(i), "element " + i);
        }
        return N + " elements, 4 per thread";
    }

    private static String parallel2d(TornadoDevice device, TargetCaps caps) throws Exception {
        int rows = 96;
        int cols = 200;
        var a = floats(rows * cols, i -> i % 97);
        var b = floats(rows * cols, i -> 0.25 * (i % 13));
        var c = new FloatArray(rows * cols);
        var graph = new TaskGraph("parallel2d").transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b).task("t", Kernels::matrixAdd, a, b, c, rows, cols)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        run(device, graph, null, false);
        var expected = new FloatArray(rows * cols);
        Kernels.matrixAdd(a, b, expected, rows, cols);
        for (int i = 0; i < rows * cols; i++) {
            same(expected.get(i), c.get(i), "element " + i);
        }
        return rows + " x " + cols;
    }

    private static String branches(TornadoDevice device, TargetCaps caps) throws Exception {
        var in = floats(N, i -> (i % 11) - 5.25);
        var out = new FloatArray(N);
        var graph = new TaskGraph("branches").transferToDevice(DataTransferMode.EVERY_EXECUTION, in).task("t", Kernels::branches, in, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        run(device, graph, null, false);
        var expected = new FloatArray(N);
        Kernels.branches(in, expected);
        for (int i = 0; i < N; i++) {
            // OpenCL's default build (-cl-fast-relaxed-math) may use a sqrt a few ulp from Java's.
            close(expected.get(i), out.get(i), 1e-6f, "element " + i);
        }
        return N + " elements, three paths";
    }

    private static String localMemory(TornadoDevice device, TargetCaps caps) throws Exception {
        int block = 256;
        var in = floats(N, i -> (i % 13) * 0.25);
        var out = new FloatArray(N / block);
        var graph = new TaskGraph("localMemory").transferToDevice(DataTransferMode.EVERY_EXECUTION, in).task("t", Kernels::blockSum, new KernelContext(), in, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        run(device, graph, grid1d(N, block), false);
        for (int g = 0; g < N / block; g++) {
            float expected = 0;
            for (int i = g * block; i < (g + 1) * block; i++) {
                expected += in.get(i);
            }
            // Multiples of 0.25 below 2^24: every order of summation is exact.
            same(expected, out.get(g), "work-group " + g);
        }
        return (N / block) + " work-groups of " + block;
    }

    private static String reduce(TornadoDevice device, TargetCaps caps) throws Exception {
        int n = 100_003; // not a power of two: idle lanes in the last group
        var in = new IntArray(n);
        for (int i = 0; i < n; i++) {
            in.set(i, (i * 7) % 1000 - 300);
        }
        var result = new IntArray(1);
        var graph = new TaskGraph("reduce").transferToDevice(DataTransferMode.EVERY_EXECUTION, in).task("t", Kernels::reduceSum, in, result)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        run(device, graph, null, false);
        var expected = new IntArray(1);
        Kernels.reduceSum(in, expected);
        same(expected.get(0), result.get(0), "sum");
        return n + " elements";
    }

    private static String atomics(TornadoDevice device, TargetCaps caps) throws Exception {
        var in = new IntArray(N);
        for (int i = 0; i < N; i++) {
            in.set(i, i * 31 + (i >> 3));
        }
        var bins = new IntArray(16);
        var graph = new TaskGraph("atomics").transferToDevice(DataTransferMode.EVERY_EXECUTION, in, bins).task("t", Kernels::histogram, new KernelContext(), in, bins)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, bins);
        run(device, graph, grid1d(N, 128), false);
        var expected = new int[16];
        for (int i = 0; i < N; i++) {
            expected[in.get(i) & 15]++;
        }
        for (int k = 0; k < 16; k++) {
            same(expected[k], bins.get(k), "bin " + k);
        }
        return N + " increments into 16 bins";
    }

    // ---------------------------------------------------------------- types

    private static String int64(TornadoDevice device, TargetCaps caps) throws Exception {
        var in = new LongArray(N);
        var random = new Random(7);
        for (int i = 0; i < N; i++) {
            in.set(i, i < 4 ? new long[] { Long.MIN_VALUE, Long.MAX_VALUE, -1, 0 }[i] : random.nextLong());
        }
        var out = new LongArray(N);
        var graph = new TaskGraph("int64").transferToDevice(DataTransferMode.EVERY_EXECUTION, in).task("t", Kernels::longs, in, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        run(device, graph, null, false);
        var expected = new LongArray(N);
        Kernels.longs(in, expected);
        for (int i = 0; i < N; i++) {
            same(expected.get(i), out.get(i), "element " + i);
        }
        return N + " values";
    }

    private static String fp64(TornadoDevice device, TargetCaps caps) throws Exception {
        var in = new DoubleArray(N);
        for (int i = 0; i < N; i++) {
            in.set(i, (i - N / 2) * 0.001);
        }
        var out = new DoubleArray(N);
        var graph = new TaskGraph("fp64").transferToDevice(DataTransferMode.EVERY_EXECUTION, in).task("t", Kernels::doubles, in, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        run(device, graph, null, true);
        var expected = new DoubleArray(N);
        Kernels.doubles(in, expected);
        for (int i = 0; i < N; i++) {
            if (Double.doubleToRawLongBits(expected.get(i)) != Double.doubleToRawLongBits(out.get(i))) {
                throw new AssertionError("element " + i + ": expected " + expected.get(i) + ", got " + out.get(i));
            }
        }
        return N + " values, strict (correctly rounded division)";
    }

    private static String fp16(TornadoDevice device, TargetCaps caps) throws Exception {
        int n = 4096;
        var a = new HalfFloatArray(n);
        var b = new HalfFloatArray(n);
        for (int i = 0; i < n; i++) {
            a.set(i, new HalfFloat((i % 61) * 0.37f - 9.0f));
            b.set(i, new HalfFloat((i % 17) * 0.11f - 1.0f));
        }
        var out = new HalfFloatArray(n);
        var graph = new TaskGraph("fp16").transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b).task("t", Kernels::halves, a, b, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        run(device, graph, null, false);
        var expected = new HalfFloatArray(n);
        Kernels.halves(a, b, expected);
        for (int i = 0; i < n; i++) {
            same(expected.get(i).getHalfFloatValue(), out.get(i).getHalfFloatValue(), "element " + i);
        }
        return n + " values";
    }

    // ---------------------------------------------------------------- Java semantics

    private static String integers(TornadoDevice device, TargetCaps caps) throws Exception {
        int[] xs = { Integer.MIN_VALUE, Integer.MAX_VALUE, -7, 7, -1, 0, 123456789, -987654321 };
        int[] ys = { -1, 1, 2, -2, 0, 31, 33, -3, 64 };
        int n = xs.length * ys.length;
        var a = new IntArray(n);
        var b = new IntArray(n);
        for (int i = 0; i < n; i++) {
            a.set(i, xs[i / ys.length]);
            b.set(i, ys[i % ys.length]);
        }
        var out = new IntArray(n);
        var graph = new TaskGraph("integers").transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b).task("t", Kernels::integerSemantics, a, b, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        run(device, graph, null, false);
        var expected = new IntArray(n);
        Kernels.integerSemantics(a, b, expected);
        for (int i = 0; i < n; i++) {
            same(expected.get(i), out.get(i), a.get(i) + " op " + b.get(i));
        }
        return n + " operand pairs";
    }

    private static String conversions(TornadoDevice device, TargetCaps caps) throws Exception {
        var in = floats(EDGES.length, i -> EDGES[i]);
        var ints = new IntArray(EDGES.length);
        var longs = new LongArray(EDGES.length);
        var graph = new TaskGraph("conversions").transferToDevice(DataTransferMode.EVERY_EXECUTION, in).task("t", Kernels::conversions, in, ints, longs)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ints, longs);
        run(device, graph, null, false);
        for (int i = 0; i < EDGES.length; i++) {
            same((int) EDGES[i], ints.get(i), "(int) " + EDGES[i]);
            same((long) (EDGES[i] * 4.0e9f), longs.get(i), "(long) " + EDGES[i] + " * 4e9");
        }
        return EDGES.length + " edge values";
    }

    /**
     * {@link #EDGES} without denormals: OpenCL's default build (-cl-fast-relaxed-math) flushes them to
     * zero, which is a property of that build, checked in strict-fp, not of min/max.
     */
    private static final float[] NORMAL_EDGES = { 0.0f, -0.0f, 1.0f, -1.5f, Float.MAX_VALUE, -Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN,
            2.5e9f, -2.5e9f, Float.MIN_NORMAL };

    private static String minMax(TornadoDevice device, TargetCaps caps) throws Exception {
        float[] edges = NORMAL_EDGES;
        int n = edges.length * edges.length;
        var a = floats(n, i -> edges[i / edges.length]);
        var b = floats(n, i -> edges[i % edges.length]);
        var min = new FloatArray(n);
        var max = new FloatArray(n);
        var graph = new TaskGraph("minMax").transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b).task("t", Kernels::minMax, a, b, min, max)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, min, max);
        run(device, graph, null, false);
        for (int i = 0; i < n; i++) {
            same(Math.min(a.get(i), b.get(i)), min.get(i), "min(" + a.get(i) + ", " + b.get(i) + ")");
            same(Math.max(a.get(i), b.get(i)), max.get(i), "max(" + a.get(i) + ", " + b.get(i) + ")");
        }
        return n + " pairs";
    }

    private static String privateArrays(TornadoDevice device, TargetCaps caps) throws Exception {
        var out = new IntArray(256);
        var graph = new TaskGraph("privateArrays").task("t", Kernels::privateArrays, out).transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        run(device, graph, null, false);
        var expected = new IntArray(256);
        Kernels.privateArrays(expected);
        for (int i = 0; i < 256; i++) {
            same(expected.get(i), out.get(i), "thread " + i);
        }
        return "256 threads x 8 allocations";
    }

    // ---------------------------------------------------------------- strict floating point

    private static String ieeeEdges(TornadoDevice device, TargetCaps caps) throws Exception {
        var in = floats(EDGES.length, i -> EDGES[i]);
        var out = new FloatArray(4 * EDGES.length);
        var graph = new TaskGraph("ieeeEdges").transferToDevice(DataTransferMode.EVERY_EXECUTION, in).task("t", Kernels::ieeeEdges, in, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        run(device, graph, null, true);
        var expected = new FloatArray(4 * EDGES.length);
        Kernels.ieeeEdges(in, expected);
        String[] ops = { "0 + x", "x - x", "x * 0", "x / x" };
        // Apple GPUs flush float subnormals to zero even without fast math, and say so.
        boolean flushesSubnormals = !device.getPhysicalDevice().isDeviceSingleFPDenormSupported();
        int compared = 0;
        for (int i = 0; i < out.getSize(); i++) {
            float x = EDGES[i / 4];
            if (flushesSubnormals && x != 0.0f && Math.abs(x) < Float.MIN_NORMAL) {
                continue;
            }
            // Java fixes which results are NaN, not the bits of a NaN arithmetic produces.
            same(expected.get(i), out.get(i), ops[i % 4] + " for x = " + x);
            compared++;
        }
        return compared / 4 + " edge values x 4 operations" + (flushesSubnormals ? " (subnormals skipped: the device flushes them)" : "");
    }

    // ---------------------------------------------------------------- device features

    private static String warpSum(TornadoDevice device, TargetCaps caps) throws Exception {
        int n = 4096;
        var in = new FloatArray(n);
        for (int i = 0; i < n; i++) {
            in.set(i, (i % 32) + (i / 32) * 0.5f);
        }
        var out = new FloatArray(n);
        var graph = new TaskGraph("warpSum").transferToDevice(DataTransferMode.EVERY_EXECUTION, in).task("t", Kernels::warpSum, new KernelContext(), in, out)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        run(device, graph, grid1d(n, 128), false);
        for (int w = 0; w < n / 32; w++) {
            float expected = 0;
            for (int l = 0; l < 32; l++) {
                expected += in.get(w * 32 + l);
            }
            for (int l = 0; l < 32; l++) {
                // Small integers and halves: exact in any order.
                same(expected, out.get(w * 32 + l), "warp " + w + " lane " + l);
            }
        }
        return (n / 32) + " warps";
    }

    private static String deviceLaunch(TornadoDevice device, TargetCaps caps) throws Exception {
        int n = 1000;
        var a = new IntArray(n);
        for (int i = 0; i < n; i++) {
            a.set(i, i - 300);
        }
        var graph = new TaskGraph("deviceLaunch").transferToDevice(DataTransferMode.EVERY_EXECUTION, a).task("t", Kernels::launchFromDevice, new KernelContext(), a, n)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        run(device, graph, grid1d(32, 32), false);
        for (int i = 0; i < n; i++) {
            same((i - 300) * 2L, a.get(i), "element " + i);
        }
        return "child grid of " + n;
    }

    private static String mmaF16(TornadoDevice device, TargetCaps caps) throws Exception {
        int m = 32;
        int n = 32;
        int k = 64;
        var a = new HalfFloatArray(m * k);
        var b = new HalfFloatArray(k * n);
        for (int i = 0; i < m * k; i++) {
            a.set(i, new HalfFloat((i % 7) - 3));
        }
        for (int i = 0; i < k * n; i++) {
            b.set(i, new HalfFloat(((i % 5) - 2) * 0.5f));
        }
        var c = new FloatArray(m * n);
        var graph = new TaskGraph("mmaF16").transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b).task("t", TensorCoreKernels::gemmF16, new KernelContext(), a, b, c, m, n, k)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        int warps = (m / TensorCoreKernels.WMMA_M) * (n / TensorCoreKernels.WMMA_N);
        run(device, graph, grid1d(warps * 32, 32), false);
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float expected = 0;
                for (int p = 0; p < k; p++) {
                    expected += a.get(i * k + p).getFloat32() * b.get(p * n + j).getFloat32();
                }
                // Small integers and halves: exact in f32 whatever the order.
                same(expected, c.get(i * n + j), "c[" + i + "][" + j + "]");
            }
        }
        return m + "x" + n + "x" + k;
    }

    private static String mmaFp8(TornadoDevice device, TargetCaps caps) throws Exception {
        int m = 16;
        int n = 16;
        int k = 64;
        var a = new FP8Array(m * k);
        var b = new FP8Array(k * n);
        for (int i = 0; i < m * k; i++) {
            a.setE4M3(i, (i % 5) - 2);
        }
        for (int i = 0; i < k * n; i++) {
            b.setE4M3(i, ((i % 3) - 1) * 0.5f);
        }
        var c = new FloatArray(m * n);
        var graph = new TaskGraph("mmaFp8").transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b).task("t", TensorCoreKernels::gemmFp8E4M3, new KernelContext(), a, b, c, m, n, k)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        int warps = (m / TensorCoreKernels.WMMA_M) * (n / TensorCoreKernels.WMMA_N);
        run(device, graph, grid1d(warps * 32, 32), false);
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float expected = 0;
                for (int p = 0; p < k; p++) {
                    expected += FP8.e4m3ToFloat(a.get(i * k + p)) * FP8.e4m3ToFloat(b.get(p * n + j));
                }
                same(expected, c.get(i * n + j), "c[" + i + "][" + j + "]");
            }
        }
        return m + "x" + n + "x" + k;
    }
}
