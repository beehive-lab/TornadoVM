/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.fuzz;

import static org.junit.Assert.assertEquals;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Regression tests distilled from tornado-fuzz generative findings on the CUDA
 * backend. Each test is the auto-shrunk minimal integer expression that diverged
 * from Java semantics (or crashed the compiler), with a tiny hand-picked input
 * that triggers it. Every kernel is a plain {@link KernelContext} elementwise
 * expression; the expected values are the exact Java result.
 *
 * <p>These assert the correct (Java) semantics, so they FAIL / ERROR while the
 * underlying CUDA code-generator bug is open. When a bug is fixed, its test turns
 * green and becomes a permanent guard against regression. Findings referenced by
 * their fuzz seed under phase-2 generation.
 *
 * <p>Run: {@code tornado-test -V uk.ac.manchester.tornado.unittests.fuzz.TestCudaCodegenRegressions}
 * (add {@code --jvm="-Dtornado.cuda.priority=100"} on a multi-backend build).
 */
public class TestCudaCodegenRegressions extends TornadoTestBase {

    private static final int N = 8;

    // ---- kernels (minimal shrunk expressions from the fuzzer) ----

    /** seed 529 / 232: {@code a << 31} — bit 31 shift dropped on CUDA (yields 0). */
    public static void shiftBy31(KernelContext c, IntArray a, IntArray b, IntArray out) {
        int i = c.globalIdx;
        out.set(i, (a.get(i) << (255 & 31)));
    }

    /** seed 629: {@code INT_MIN * b} — INT_MIN multiply yields 0 on CUDA. */
    public static void intMinMul(KernelContext c, IntArray a, IntArray b, IntArray out) {
        int i = c.globalIdx;
        out.set(i, ((-2147483648) * b.get(i)));
    }

    /** seed 221: {@code -(a & ~INT_MAX)} == {@code -(a & INT_MIN)} — sign-bit mask/negate. */
    public static void signBitNegate(KernelContext c, IntArray a, IntArray b, IntArray out) {
        int i = c.globalIdx;
        out.set(i, (-(a.get(i) & (~(2147483647)))));
    }

    /** seed 167: {@code (a * a) >> 20} — signed right-shift of a product. */
    public static void signedShiftProduct(KernelContext c, IntArray a, IntArray b, IntArray out) {
        int i = c.globalIdx;
        out.set(i, ((a.get(i) * a.get(i)) >> (((-12)) & 31)));
    }

    /** seed 189: {@code (INT_MIN / (b|1)) / (i|1)} — crashes Graal reassociation on CUDA. */
    public static void intMinDivChain(KernelContext c, IntArray a, IntArray b, IntArray out) {
        int i = c.globalIdx;
        out.set(i, (((-2147483648) / ((b.get(i)) | 1)) / ((i) | 1)));
    }

    // ---- tests ----

    @Test
    @Ignore("Open CUDA backend bug (tornado-fuzz seed 529/232): `a << 31` yields 0 instead of a<<31; shift count 31 mishandled.")
    public void testShiftBy31() throws Exception {
        int[] in = { 0, 1, 2, 3, -1, -2, Integer.MAX_VALUE, Integer.MIN_VALUE };
        run(TestCudaCodegenRegressions::shiftBy31, in, in, (a, b, i) -> a << 31);
    }

    @Test
    @Ignore("Open CUDA backend bug (tornado-fuzz seed 629): `Integer.MIN_VALUE * b` yields 0 instead of the wrapped product.")
    public void testIntMinMul() throws Exception {
        int[] in = { 0, 1, 2, 3, -1, -2, 7, Integer.MAX_VALUE };
        run(TestCudaCodegenRegressions::intMinMul, in, in, (a, b, i) -> Integer.MIN_VALUE * b);
    }

    @Test
    @Ignore("Open CUDA backend bug (tornado-fuzz seed 221): `-(a & Integer.MIN_VALUE)` yields 0 instead of INT_MIN when the sign bit is set.")
    public void testSignBitNegate() throws Exception {
        int[] in = { 0, 1, -1, -2, Integer.MAX_VALUE, Integer.MIN_VALUE, 12345, -12345 };
        run(TestCudaCodegenRegressions::signBitNegate, in, in, (a, b, i) -> -(a & Integer.MIN_VALUE));
    }

    @Test
    @Ignore("Open CUDA backend bug (tornado-fuzz seed 167): `(a*a) >> 20` emitted as a logical shift, dropping the sign (returns +2048 where Java gives -2048).")
    public void testSignedShiftProduct() throws Exception {
        int[] in = { 0, 100000, 46341, -46341, 65536, 3, -3, 1 << 20 };
        run(TestCudaCodegenRegressions::signedShiftProduct, in, in, (a, b, i) -> (a * a) >> 20);
    }

    @Test
    @Ignore("Open CUDA backend crash (tornado-fuzz seed 189): `(INT_MIN / (b|1)) / (i|1)` throws GraalError 'unhandled node in reassociation with constants' during code generation.")
    public void testIntMinDivChain() throws Exception {
        int[] in = { 1, 2, 3, 4, 5, 6, 7, 8 };
        run(TestCudaCodegenRegressions::intMinDivChain, in, in, (a, b, i) -> (Integer.MIN_VALUE / (b | 1)) / (i | 1));
    }

    // ---- shared runner ----

    private interface RefIdx {
        int apply(int a, int b, int i);
    }

    /**
     * Runs a kernel (a direct static method reference — required so TornadoVM can
     * resolve the INVOKESTATIC target of the lambda) and asserts each element
     * against the Java reference.
     */
    private void run(Task4<KernelContext, IntArray, IntArray, IntArray> kernel, int[] aData, int[] bData, RefIdx ref) throws Exception {
        IntArray a = fromArray(aData);
        IntArray b = fromArray(bData);
        IntArray out = new IntArray(N);

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(N);
        worker.setGlobalWork(N, 1, 1);
        worker.setLocalWork(N, 1, 1);
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.addWorkerGrid("fuzz.t0", worker);

        TaskGraph taskGraph = new TaskGraph("fuzz") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", kernel, context, a, b, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        for (int i = 0; i < N; i++) {
            assertEquals("index " + i, ref.apply(aData[i], bData[i], i), out.get(i));
        }
    }

    private static IntArray fromArray(int[] data) {
        IntArray arr = new IntArray(data.length);
        for (int i = 0; i < data.length; i++) {
            arr.set(i, data[i]);
        }
        return arr;
    }
}
