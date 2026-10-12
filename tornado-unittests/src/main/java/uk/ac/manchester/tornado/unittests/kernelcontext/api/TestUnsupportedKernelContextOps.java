/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.unittests.kernelcontext.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.DeviceKernel;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix8x8Float;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@link KernelContext} operations that a backend cannot honour must be rejected by the compiler,
 * not silently replaced by their Java bodies.
 *
 * <p>The bodies in {@code KernelContext} are sequential fallbacks: {@code simdSum} returns its
 * argument, the atomic read-modify-writes are plain array updates. Compiled into a kernel they run
 * without complaint and produce wrong results, so every operation a backend does not intrinsify has
 * to fail with a TC diagnostic (its stable {@code TC-E} code) instead. Operations TC does compile
 * natively (atomics on local and global int arrays) are checked for correct results.
 *
 * <p>How to run:
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestUnsupportedKernelContextOps
 * </pre>
 */
public class TestUnsupportedKernelContextOps extends TornadoTestBase {

    private static final int SIZE = 256;

    // --- kernels ---------------------------------------------------------

    public static void simdSumKernel(KernelContext ctx, FloatArray in, FloatArray out) {
        out.set(ctx.globalIdx, ctx.simdSum(in.get(ctx.globalIdx)));
    }

    public static void simdShuffleDownKernel(KernelContext ctx, FloatArray in, FloatArray out) {
        out.set(ctx.globalIdx, ctx.simdShuffleDown(in.get(ctx.globalIdx), 1));
    }

    public static void simdBroadcastFirstKernel(KernelContext ctx, FloatArray in, FloatArray out) {
        out.set(ctx.globalIdx, ctx.simdBroadcastFirst(in.get(ctx.globalIdx)));
    }

    public static void atomicMaxLocalKernel(KernelContext ctx, IntArray in, IntArray out) {
        int[] shared = ctx.allocateIntLocalArray(1);
        if (ctx.localIdx == 0) {
            shared[0] = Integer.MIN_VALUE;
        }
        ctx.localBarrier();
        ctx.atomicMax(shared, 0, in.get(ctx.globalIdx));
        ctx.localBarrier();
        if (ctx.localIdx == 0) {
            out.set(ctx.groupIdx, shared[0]);
        }
    }

    public static void atomicCasLocalKernel(KernelContext ctx, IntArray out) {
        int[] shared = ctx.allocateIntLocalArray(1);
        if (ctx.localIdx == 0) {
            shared[0] = -1;
        }
        ctx.localBarrier();
        ctx.atomicCAS(shared, 0, -1, ctx.localIdx);
        ctx.localBarrier();
        if (ctx.localIdx == 0) {
            out.set(ctx.groupIdx, shared[0]);
        }
    }

    /** {@code data} is a kernel parameter, so it lives in global memory - not addressable as {@code &a[i]}. */
    public static void atomicMaxGlobalKernel(KernelContext ctx, int[] data) {
        ctx.atomicMax(data, 0, ctx.globalIdx);
    }

    public static void simdgroupMatrixKernel(KernelContext ctx, FloatArray out) {
        Matrix8x8Float zero = ctx.simdgroupMatrixZero();
        ctx.simdgroupMatrixStore(zero, out, 0, 8);
    }

    // --- harness ---------------------------------------------------------

    /**
     * Runs {@code body} and requires it to fail with {@code expectedFragment} somewhere in the
     * exception chain. An operation that runs to completion is the failure this class exists to catch.
     */
    private static void assertRejected(String expectedFragment, Runnable body) {
        try {
            body.run();
        } catch (Throwable thrown) {
            for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
                if (cause.getMessage() != null && cause.getMessage().contains(expectedFragment)) {
                    return;
                }
            }
            throw new AssertionError("Rejected, but not with the expected message (wanted \"" + expectedFragment + "\"): " + thrown, thrown);
        }
        throw new AssertionError("Expected the unsupported operation to be rejected by the compiler, but the kernel ran");
    }

    private static void run(String taskId, TaskGraphBody body) {
        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(64, 1, 1);
        GridScheduler grid = new GridScheduler("unsupported." + taskId, worker);
        TaskGraph taskGraph = new TaskGraph("unsupported");
        body.build(taskGraph, context, taskId);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        } catch (Exception e) {
            // Surface the compilation failure to assertRejected; the plan is closed either way.
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface TaskGraphBody {
        void build(TaskGraph taskGraph, KernelContext context, String taskId);
    }

    // --- SIMD-group reductions: OpenCL has no equivalent -------------------

    /** TC-E4015: warp operations on a device whose SIMD width TC does not know. */
    private static final String SIMD_MESSAGE = "TC-E4015";

    @Test
    public void testSimdSumRejectedOnOpenCL() {
        assertNotBackend(TornadoVMBackendType.CUDA);
        assertNotBackend(TornadoVMBackendType.METAL);
        FloatArray in = new FloatArray(SIZE);
        FloatArray out = new FloatArray(SIZE);
        in.init(1.0f);
        assertRejected(SIMD_MESSAGE, () -> run("simdSum", (taskGraph, context, id) -> taskGraph //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task(id, TestUnsupportedKernelContextOps::simdSumKernel, context, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out)));
    }

    @Test
    public void testSimdShuffleDownRejectedOnOpenCL() {
        assertNotBackend(TornadoVMBackendType.CUDA);
        assertNotBackend(TornadoVMBackendType.METAL);
        FloatArray in = new FloatArray(SIZE);
        FloatArray out = new FloatArray(SIZE);
        in.init(1.0f);
        assertRejected(SIMD_MESSAGE, () -> run("simdShuffle", (taskGraph, context, id) -> taskGraph //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task(id, TestUnsupportedKernelContextOps::simdShuffleDownKernel, context, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out)));
    }

    @Test
    public void testSimdBroadcastFirstRejectedOnOpenCL() {
        assertNotBackend(TornadoVMBackendType.CUDA);
        assertNotBackend(TornadoVMBackendType.METAL);
        FloatArray in = new FloatArray(SIZE);
        FloatArray out = new FloatArray(SIZE);
        in.init(1.0f);
        assertRejected(SIMD_MESSAGE, () -> run("simdBroadcast", (taskGraph, context, id) -> taskGraph //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task(id, TestUnsupportedKernelContextOps::simdBroadcastFirstKernel, context, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out)));
    }

    // --- device-side kernel launch: CUDA-only -----------------------------

    /** TC-E4016: device-side launches need CUDA dynamic parallelism. */
    private static final String DEVICE_LAUNCH_MESSAGE = "TC-E4016";

    private static final DeviceKernel CHILD = DeviceKernel.of(TestUnsupportedKernelContextOps::launchedChild);

    private static void launchedChild(KernelContext context, IntArray out) {
        out.set(context.globalIdx, 1);
    }

    private static void deviceLaunchKernel(KernelContext context, IntArray out) {
        if (context.globalIdx == 0) {
            context.launch(CHILD, SIZE, 64, out);
        }
    }

    @Test
    public void testDeviceLaunchRejectedOnNonCuda() {
        assertNotBackend(TornadoVMBackendType.CUDA);
        IntArray out = new IntArray(SIZE);
        assertRejected(DEVICE_LAUNCH_MESSAGE, () -> run("deviceLaunch", (taskGraph, context, id) -> taskGraph //
                .task(id, TestUnsupportedKernelContextOps::deviceLaunchKernel, context, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out)));
    }

    // --- atomic read-modify-write: native on every backend ----------------

    @Test
    public void testAtomicMaxOnLocalArray() {
        IntArray in = new IntArray(SIZE);
        IntArray out = new IntArray(SIZE / 64);
        for (int i = 0; i < SIZE; i++) {
            in.set(i, (i * 37) % 101);
        }
        runOrRejected(() -> run("atomicMax", (taskGraph, context, id) -> taskGraph //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task(id, TestUnsupportedKernelContextOps::atomicMaxLocalKernel, context, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out)), () -> {
                    for (int g = 0; g < SIZE / 64; g++) {
                        int expected = Integer.MIN_VALUE;
                        for (int i = g * 64; i < (g + 1) * 64; i++) {
                            expected = Math.max(expected, in.get(i));
                        }
                        assertEquals(expected, out.get(g), "group " + g);
                    }
                });
    }

    @Test
    public void testAtomicCasOnLocalArray() {
        IntArray out = new IntArray(SIZE / 64);
        out.init(-2);
        runOrRejected(() -> run("atomicCas", (taskGraph, context, id) -> taskGraph //
                .task(id, TestUnsupportedKernelContextOps::atomicCasLocalKernel, context, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out)), () -> {
                    // Exactly one thread of each group wins the compare-and-swap: its local index.
                    for (int g = 0; g < SIZE / 64; g++) {
                        assertTrue(out.get(g) >= 0 && out.get(g) < 64, "group " + g + ": " + out.get(g));
                    }
                });
    }

    @Test
    public void testAtomicOnGlobalArray() {
        int[] data = new int[SIZE];
        runOrRejected(() -> run("atomicGlobal", (taskGraph, context, id) -> taskGraph //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task(id, TestUnsupportedKernelContextOps::atomicMaxGlobalKernel, context, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data)), () -> assertEquals(SIZE - 1, data[0]));
    }

    /**
     * Runs {@code body}: a kernel that runs must produce the right result ({@code check}); one that
     * does not compile must be refused by TC with a diagnostic, never fall back to the Java body.
     */
    private static void runOrRejected(Runnable body, Runnable check) {
        try {
            body.run();
        } catch (Throwable thrown) {
            for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
                if (cause.getMessage() != null && cause.getMessage().contains("TC-E")) {
                    return;
                }
            }
            throw new AssertionError("Failed without a TC diagnostic: " + thrown, thrown);
        }
        check.run();
    }

    // --- simdgroup matrices: Metal-only -----------------------------------

    @Test
    public void testSimdgroupMatrixRejectedOnNonMetal() {
        assertNotBackend(TornadoVMBackendType.METAL);
        FloatArray out = new FloatArray(64);
        assertRejected("Matrix8x8Float", () -> run("simdgroup", (taskGraph, context, id) -> taskGraph //
                .task(id, TestUnsupportedKernelContextOps::simdgroupMatrixKernel, context, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out)));
    }
}
