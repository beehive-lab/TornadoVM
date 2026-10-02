/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.unittests.codegen;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Binary math intrinsics whose arguments are compile-time constants, which are constant-folded, and
 * branches whose conditions become constant once scalar arguments are specialised.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.codegen.TestConstantFoldedIntrinsics
 * </code>
 */
public class TestConstantFoldedIntrinsics extends TornadoTestBase {

    private static final int N = 256;

    public static void floatBinaryIntrinsicsOfConstants(KernelContext context, FloatArray a, FloatArray out, int n) {
        int i = context.globalIdx;
        if (i < n) {
            float c = TornadoMath.pow(1.5f, 2.0f) + TornadoMath.atan2(1.0f, 2.0f) + TornadoMath.min(0.5f, 2.0f) + TornadoMath.max(0.5f, 2.0f);
            out.set(i, a.get(i) * c);
        }
    }

    public static void doubleBinaryIntrinsicsOfConstants(KernelContext context, DoubleArray a, DoubleArray out, int n) {
        int i = context.globalIdx;
        if (i < n) {
            double c = TornadoMath.pow(1.5, 2.0) + TornadoMath.atan2(1.0, 2.0) + TornadoMath.min(0.5, 2.0) + TornadoMath.max(0.5, 2.0);
            out.set(i, a.get(i) * c);
        }
    }

    /**
     * Copies each line of {@code in} to {@code out} through local memory, choosing how to read
     * (complex pairs, reals, or the first half of a conjugate-symmetric line) and how to write
     * (complex pairs or reals) from scalar arguments. Once those arguments are specialised, several
     * of the branches have constant conditions.
     */
    public static void branchOnArguments(KernelContext context, FloatArray in, FloatArray out, int n, int inLen, int outLen, int inKind, int realOut) {
        float[] re = context.allocateFloatLocalArray(256);
        float[] im = context.allocateFloatLocalArray(256);
        int line = context.groupIdx;
        int tid = context.localIdx;
        int inBase = line * inLen;
        int outBase = line * outLen;
        for (int i = tid; i < n; i += 64) {
            float vr = 0.0f;
            float vi = 0.0f;
            if (inKind == 2) {
                int k = i <= n / 2 ? i : n - i;
                if (k < inLen) {
                    vr = in.get(2 * (inBase + k));
                    vi = in.get(2 * (inBase + k) + 1);
                    if (i > n / 2) {
                        vi = -vi;
                    }
                }
            } else if (i < inLen) {
                if (inKind == 1) {
                    vr = in.get(inBase + i);
                } else {
                    vr = in.get(2 * (inBase + i));
                    vi = in.get(2 * (inBase + i) + 1);
                }
            }
            re[i] = vr;
            im[i] = vi;
        }
        context.localBarrier();
        for (int k = tid; k < outLen; k += 64) {
            if (realOut != 0) {
                out.set(outBase + k, re[k]);
            } else {
                out.set(2 * (outBase + k), re[k]);
                out.set(2 * (outBase + k) + 1, im[k]);
            }
        }
    }

    private static GridScheduler grid(String task, int threads) {
        WorkerGrid1D grid = new WorkerGrid1D(threads);
        grid.setLocalWork(64, 1, 1);
        return new GridScheduler(task, grid);
    }

    @Test
    public void testFloatBinaryIntrinsicsOfConstants() throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, i);
        }
        FloatArray out = new FloatArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConstantFoldedIntrinsics::floatBinaryIntrinsicsOfConstants, new KernelContext(), a, out, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid("s0.t0", N)).execute();
        }
        float c = TornadoMath.pow(1.5f, 2.0f) + TornadoMath.atan2(1.0f, 2.0f) + TornadoMath.min(0.5f, 2.0f) + TornadoMath.max(0.5f, 2.0f);
        for (int i = 0; i < N; i++) {
            assertEquals(i * c, out.get(i), 1e-5f * (1 + i * c));
        }
    }

    @Test
    public void testDoubleBinaryIntrinsicsOfConstants() throws TornadoExecutionPlanException {
        // Metal has no double type.
        assertNotBackend(TornadoVMBackendType.METAL);
        DoubleArray a = new DoubleArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, i);
        }
        DoubleArray out = new DoubleArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestConstantFoldedIntrinsics::doubleBinaryIntrinsicsOfConstants, new KernelContext(), a, out, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid("s0.t0", N)).execute();
        }
        double c = TornadoMath.pow(1.5, 2.0) + TornadoMath.atan2(1.0, 2.0) + TornadoMath.min(0.5, 2.0) + TornadoMath.max(0.5, 2.0);
        for (int i = 0; i < N; i++) {
            assertEquals(i * c, out.get(i), 1e-12 * (1 + i * c));
        }
    }

    /** {inKind, realOut}: real to complex, conjugate-symmetric half to real, complex to complex. */
    @Test
    public void testBranchesOnSpecialisedArguments() throws TornadoExecutionPlanException {
        final int lines = 4;
        final int n = 128;
        for (int[] mode : new int[][] { { 1, 0 }, { 2, 1 }, { 0, 0 } }) {
            int inKind = mode[0];
            boolean realOut = mode[1] != 0;
            int inLen = inKind == 2 ? n / 2 + 1 : n;
            int inFloats = lines * inLen * (inKind == 1 ? 1 : 2);
            FloatArray in = new FloatArray(inFloats);
            for (int i = 0; i < inFloats; i++) {
                in.set(i, i % 97);
            }
            FloatArray out = new FloatArray(lines * n * (realOut ? 1 : 2));
            TaskGraph graph = new TaskGraph("s0") //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                    .task("t0", TestConstantFoldedIntrinsics::branchOnArguments, new KernelContext(), in, out, n, inLen, n, inKind, mode[1]) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
                plan.withGridScheduler(grid("s0.t0", lines * 64)).execute();
            }
            for (int line = 0; line < lines; line++) {
                for (int i = 0; i < n; i++) {
                    float vr;
                    float vi = 0.0f;
                    if (inKind == 2) {
                        int k = i <= n / 2 ? i : n - i;
                        vr = in.get(2 * (line * inLen + k));
                        vi = (i > n / 2 ? -1 : 1) * in.get(2 * (line * inLen + k) + 1);
                    } else if (inKind == 1) {
                        vr = in.get(line * inLen + i);
                    } else {
                        vr = in.get(2 * (line * inLen + i));
                        vi = in.get(2 * (line * inLen + i) + 1);
                    }
                    String what = "inKind=" + inKind + " realOut=" + realOut + " line " + line + " element " + i;
                    if (realOut) {
                        assertEquals(what, vr, out.get(line * n + i), 0.0f);
                    } else {
                        assertEquals(what, vr, out.get(2 * (line * n + i)), 0.0f);
                        assertEquals(what, vi, out.get(2 * (line * n + i) + 1), 0.0f);
                    }
                }
            }
        }
    }
}
