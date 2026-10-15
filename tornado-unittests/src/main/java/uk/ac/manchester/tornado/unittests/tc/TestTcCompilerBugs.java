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
package uk.ac.manchester.tornado.unittests.tc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Regression tests for TC compiler bugs found by the PR #1228 validation (TC-1 ... TC-10). Each test
 * compares the device result with the same method run on the JVM.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tc.TestTcCompilerBugs
 * </code>
 */
public class TestTcCompilerBugs extends TornadoTestBase {

    private static final int N = 64;

    private static void runOnDevice(String name, TaskGraph graph) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
    }

    private static void assertSame(IntArray expected, IntArray actual) {
        for (int i = 0; i < expected.getSize(); i++) {
            assertEquals(expected.get(i), actual.get(i), "index " + i);
        }
    }

    // ---------------------------------------------------------------- TC-1

    public static void privateArrayInLoop(IntArray out) {
        for (@Parallel int id = 0; id < out.getSize(); id++) {
            int s = 0;
            for (int i = 0; i < 8; i++) {
                int[] t = new int[2];
                t[0] += i + id;
                t[1] += 1;
                s += t[0] * 10 + t[1];
            }
            out.set(id, s);
        }
    }

    /** TC-1: {@code new int[2]} inside a loop starts from zero on every iteration. */
    @Test
    public void testPrivateArrayAllocatedInLoopIsZeroedEachIteration() throws TornadoExecutionPlanException {
        IntArray out = new IntArray(N);
        runOnDevice("tc1", new TaskGraph("s0") //
                .task("t0", TestTcCompilerBugs::privateArrayInLoop, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        IntArray expected = new IntArray(N);
        privateArrayInLoop(expected);
        assertSame(expected, out);
    }

    // ---------------------------------------------------------------- TC-2

    public static void unrollWrappingInduction(IntArray out) {
        for (@Parallel int id = 0; id < out.getSize(); id++) {
            int cnt = 0;
            for (int i = 0; i < 2000000000; i += 1500000000) {
                cnt++;
            }
            out.set(id, cnt + id);
        }
    }

    /** TC-2: a fully unrolled loop counts its trips with the induction variable's int wrap-around. */
    @Test
    public void testFullUnrollWrapsInductionVariable() throws TornadoExecutionPlanException {
        IntArray out = new IntArray(N);
        runOnDevice("tc2", new TaskGraph("s0") //
                .task("t0", TestTcCompilerBugs::unrollWrappingInduction, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        IntArray expected = new IntArray(N);
        unrollWrappingInduction(expected);
        assertSame(expected, out);
    }

    // ---------------------------------------------------------------- TC-3

    public static void floatRemainder(FloatArray a, FloatArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, a.get(i) % 0.75f);
        }
    }

    public static void doubleRemainder(DoubleArray a, DoubleArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, a.get(i) % 0.75);
        }
    }

    /** TC-3: float {@code %} compiles on OpenCL and Metal (no {@code fmodf} there) and matches Java. */
    @Test
    public void testFloatRemainder() throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, (i - N / 2) * 0.37f);
        }
        FloatArray out = new FloatArray(N);
        runOnDevice("tc3", new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestTcCompilerBugs::floatRemainder, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        FloatArray expected = new FloatArray(N);
        floatRemainder(a, expected);
        for (int i = 0; i < N; i++) {
            assertEquals(Float.floatToRawIntBits(expected.get(i)), Float.floatToRawIntBits(out.get(i)), "index " + i);
        }
    }

    /** TC-3: double {@code %} keeps working. */
    @Test
    public void testDoubleRemainder() throws TornadoExecutionPlanException {
        DoubleArray a = new DoubleArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, (i - N / 2) * 0.37);
        }
        DoubleArray out = new DoubleArray(N);
        runOnDevice("tc3d", new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestTcCompilerBugs::doubleRemainder, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        DoubleArray expected = new DoubleArray(N);
        doubleRemainder(a, expected);
        for (int i = 0; i < N; i++) {
            assertEquals(Double.doubleToRawLongBits(expected.get(i)), Double.doubleToRawLongBits(out.get(i)), "index " + i);
        }
    }

    // ---------------------------------------------------------------- TC-4

    // The locals keep their names in the kernel (javac -g): each is named like a function the emitter prints.
    public static void localsNamedSinpi(FloatArray a, FloatArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            float sinpi = TornadoMath.sinpi(a.get(i));
            float cospi = TornadoMath.cospi(a.get(i));
            float sinpif = sinpi * 2.0f;
            float cospif = cospi * 3.0f;
            out.set(i, sinpi + cospi + sinpif + cospif);
        }
    }

    /** TC-4: locals named {@code sinpi}, {@code cospi}, {@code sinpif}, {@code cospif} do not hide the builtins. */
    @Test
    public void testLocalsNamedLikeSinpiBuiltins() throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, i * 0.125f);
        }
        FloatArray out = new FloatArray(N);
        runOnDevice("tc4", new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestTcCompilerBugs::localsNamedSinpi, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        FloatArray expected = new FloatArray(N);
        localsNamedSinpi(a, expected);
        for (int i = 0; i < N; i++) {
            assertEquals(expected.get(i), out.get(i), 1e-4f, "index " + i);
        }
    }

    public static void localsNamedLlmax(LongArray x, LongArray out) {
        for (@Parallel int i = 0; i < x.getSize(); i++) {
            long llmax = Math.max(x.get(i), 7L);
            long llmin = Math.min(x.get(i), -7L);
            out.set(i, llmax * 1000 + llmin);
        }
    }

    /** TC-4: locals named {@code llmax}, {@code llmin} do not hide CUDA's long min/max. */
    @Test
    public void testLocalsNamedLikeLongMinMaxBuiltins() throws TornadoExecutionPlanException {
        LongArray x = new LongArray(N);
        for (int i = 0; i < N; i++) {
            x.set(i, (i - N / 2) * 3L);
        }
        LongArray out = new LongArray(N);
        runOnDevice("tc4l", new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .task("t0", TestTcCompilerBugs::localsNamedLlmax, x, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        LongArray expected = new LongArray(N);
        localsNamedLlmax(x, expected);
        for (int i = 0; i < N; i++) {
            assertEquals(expected.get(i), out.get(i), "index " + i);
        }
    }

    // ---------------------------------------------------------------- TC-5

    /**
     * TC-5: FP8 tensor-core MMA needs sm_89 and NVRTC 12.4+. Below that the compiler refuses the kernel
     * with TornadoDeviceMMANotSupported, which the harness reports as unsupported (not a raw ptxas
     * failure); on sm_89+ the GEMM runs and matches the reference.
     */
    @Test
    public void testFP8MmaIsRefusedOnDevicesWithoutFP8TensorCores() throws TornadoExecutionPlanException {
        var fp8 = new uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationMMAFP8();
        fp8.testGemmFP8Minimal();
        fp8.testGemmFP8E5M2Minimal();
    }

    // ---------------------------------------------------------------- P-1

    public static void scaleAdd(IntArray a, IntArray out, int k) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, a.get(i) * k + i);
        }
    }

    /**
     * P-1: tasks of one method share a compilation only when everything the compiler specialises on is
     * the same. Tasks 0 and 1 match (one compilation); task 2 has another scalar and task 3 another
     * array length, so each gets its own code and every result is the method's.
     */
    @Test
    public void testMemoisedCompilationsKeepPerTaskFacts() throws TornadoExecutionPlanException {
        int[] sizes = { N, N, N, 2 * N };
        int[] scales = { 3, 3, 5, 3 };
        IntArray[] in = new IntArray[4];
        IntArray[] out = new IntArray[4];
        TaskGraph graph = new TaskGraph("s0");
        for (int t = 0; t < 4; t++) {
            in[t] = new IntArray(sizes[t]);
            for (int i = 0; i < sizes[t]; i++) {
                in[t].set(i, i - t);
            }
            out[t] = new IntArray(sizes[t]);
            graph.transferToDevice(DataTransferMode.EVERY_EXECUTION, in[t]);
            graph.task("t" + t, TestTcCompilerBugs::scaleAdd, in[t], out[t], scales[t]);
            graph.transferToHost(DataTransferMode.EVERY_EXECUTION, out[t]);
        }
        runOnDevice("p1", graph);
        for (int t = 0; t < 4; t++) {
            IntArray expected = new IntArray(sizes[t]);
            scaleAdd(in[t], expected, scales[t]);
            assertSame(expected, out[t]);
        }
    }

    // ---------------------------------------------------------------- TC-8

    /** A kernel reached through an interface: the task method is the abstract {@code apply}. */
    public interface AbstractKernel {
        void apply(KernelContext context, IntArray a, IntArray b, int n);
    }

    public static final class CopyKernel implements AbstractKernel {
        @Override
        public void apply(KernelContext context, IntArray a, IntArray b, int n) {
            int i = context.globalIdx;
            if (i < n) {
                b.set(i, a.get(i));
            }
        }
    }

    private static String messages(Throwable t) {
        var sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append('\n');
        }
        return sb.toString();
    }

    /** TC-8: a task whose method has no bytecode is refused with TC-E1003, not an internal error (TC-E9003). */
    @Test
    public void testAbstractTaskMethodIsNotAnInternalError() {
        AbstractKernel k = new CopyKernel();
        IntArray a = new IntArray(N);
        IntArray b = new IntArray(N);
        KernelContext context = new KernelContext();
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", k::apply, context, a, b, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);
        String failure = "";
        try {
            runOnDevice("tc8", graph);
        } catch (Throwable t) {
            failure = messages(t);
        }
        assertFalse(failure.contains("TC-E9003"), failure);
        assertTrue(failure.isEmpty() || failure.contains("TC-E1003"), failure);
    }

    // ---------------------------------------------------------------- TC-9

    public static void floatNanBits(IntArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            int k = i % 4;
            int f = k == 0 ? Float.floatToRawIntBits(Float.intBitsToFloat(0x7f800001)) // signalling
                    : k == 1 ? Float.floatToRawIntBits(Float.intBitsToFloat(0x7fc00001)) // quiet, with a payload
                            : k == 2 ? Float.floatToRawIntBits(Float.intBitsToFloat(0xffc00000)) // negative
                                    : Float.floatToRawIntBits(Float.intBitsToFloat(0xff800123));
            out.set(i, f);
        }
    }

    public static void doubleNanBits(LongArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, (i % 2 == 0) ? Double.doubleToRawLongBits(Double.longBitsToDouble(0x7ff0000000000001L)) : Double.doubleToRawLongBits(Double.longBitsToDouble(0x7ff8000000000123L)));
        }
    }

    /** TC-9: float NaN constants keep their bits (signalling, payload and sign) through folding and printing. */
    @Test
    public void testNanConstantsKeepTheirBits() throws TornadoExecutionPlanException {
        IntArray out = new IntArray(8);
        runOnDevice("tc9", new TaskGraph("s0") //
                .task("t0", TestTcCompilerBugs::floatNanBits, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        int[] floats = { 0x7f800001, 0x7fc00001, 0xffc00000, 0xff800123 };
        for (int i = 0; i < out.getSize(); i++) {
            assertEquals(Integer.toHexString(floats[i % 4]), Integer.toHexString(out.get(i)), "index " + i);
        }
    }

    /** TC-9: double NaN constants keep their bits too. */
    @Test
    public void testDoubleNanConstantsKeepTheirBits() throws TornadoExecutionPlanException {
        LongArray out = new LongArray(4);
        runOnDevice("tc9d", new TaskGraph("s0") //
                .task("t0", TestTcCompilerBugs::doubleNanBits, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        long[] doubles = { 0x7ff0000000000001L, 0x7ff8000000000123L };
        for (int i = 0; i < out.getSize(); i++) {
            assertEquals(Long.toHexString(doubles[i % 2]), Long.toHexString(out.get(i)), "index " + i);
        }
    }

    // ---------------------------------------------------------------- TC-10

    /** A class and a method whose names are not ASCII. */
    public static final class Größe {
        public static void kernelÄ(IntArray a) {
            for (@Parallel int i = 0; i < a.getSize(); i++) {
                a.set(i, a.get(i) * 2 + 1);
            }
        }
    }

    /** {@code TestTcCompilerBugs$A$B.m}: its entry name must not be {@code TestTcCompilerBugs$A_B.m}'s. */
    public static final class A {
        public static final class B {
            public static void m(IntArray a) {
                for (@Parallel int i = 0; i < a.getSize(); i++) {
                    a.set(i, a.get(i) + 10);
                }
            }
        }
    }

    public static final class A_B {
        public static void m(IntArray a) {
            for (@Parallel int i = 0; i < a.getSize(); i++) {
                a.set(i, a.get(i) + 20);
            }
        }
    }

    /** TC-10: non-ASCII class and method names give a valid entry point. */
    @Test
    public void testNonAsciiKernelName() throws TornadoExecutionPlanException {
        IntArray a = new IntArray(N);
        a.init(3);
        runOnDevice("tc10", new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", Größe::kernelÄ, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a));
        for (int i = 0; i < N; i++) {
            assertEquals(7, a.get(i), "index " + i);
        }
    }

    /** TC-10: {@code A$B.m} and {@code A_B.m} in one graph keep apart (they used to share an entry name). */
    @Test
    public void testDollarAndUnderscoreKernelNamesDoNotCollide() throws TornadoExecutionPlanException {
        IntArray x = new IntArray(N);
        IntArray y = new IntArray(N);
        runOnDevice("tc10b", new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y) //
                .task("t0", A.B::m, x) //
                .task("t1", A_B::m, y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, x, y));
        for (int i = 0; i < N; i++) {
            assertEquals(10, x.get(i), "A$B.m index " + i);
            assertEquals(20, y.get(i), "A_B.m index " + i);
        }
    }

    // ---------------------------------------------------------------- S-1

    public static void ieeeEdges(FloatArray a, FloatArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            float x = a.get(i);
            out.set(4 * i, 0.0f + x); // a denormal stays a denormal
            out.set(4 * i + 1, x - x); // NaN for infinities and NaN
            out.set(4 * i + 2, x * 0.0f); // NaN for infinities, -0.0 for negatives
            out.set(4 * i + 3, x / x); // NaN for zero, infinities and NaN
        }
    }

    private static FloatArray ieeeInputs() {
        float[] values = { Float.MIN_VALUE, -Float.MIN_VALUE, 1.0e-40f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN, 0.0f, -3.0f };
        FloatArray a = new FloatArray(values.length);
        for (int i = 0; i < values.length; i++) {
            a.set(i, values[i]);
        }
        return a;
    }

    /**
     * S-1: under withStrictFloatingPoint() OpenCL builds without -cl-fast-relaxed-math: denormals,
     * NaNs and signed zeros come out as Java computes them.
     */
    @Test
    public void testStrictFloatingPointKeepsIeeeEdgeCases() throws TornadoExecutionPlanException {
        FloatArray a = ieeeInputs();
        FloatArray out = new FloatArray(4 * a.getSize());
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestTcCompilerBugs::ieeeEdges, a, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withStrictFloatingPoint().execute();
        }
        FloatArray expected = new FloatArray(out.getSize());
        ieeeEdges(a, expected);
        for (int i = 0; i < out.getSize(); i++) {
            // Java fixes which results are NaN, not the bits of a NaN arithmetic produces (x86 and GPUs differ).
            if (Float.isNaN(expected.get(i))) {
                assertTrue(Float.isNaN(out.get(i)), "index " + i + " (input " + a.get(i / 4) + "): " + out.get(i));
            } else {
                assertEquals(Integer.toHexString(Float.floatToRawIntBits(expected.get(i))), Integer.toHexString(Float.floatToRawIntBits(out.get(i))), "index " + i + " (input " + a.get(i / 4) + ")");
            }
        }
    }

    public static void divide(DoubleArray a, DoubleArray b, DoubleArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            out.set(i, a.get(i) / b.get(i));
        }
    }

    /** S-1: strict double division is correctly rounded ((double) 7 / 3 is 2.3333333333333335). */
    @Test
    public void testStrictFloatingPointDivisionIsCorrectlyRounded() throws TornadoExecutionPlanException {
        DoubleArray a = new DoubleArray(N);
        DoubleArray b = new DoubleArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, 7 + i);
            b.set(i, 3);
        }
        DoubleArray out = new DoubleArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestTcCompilerBugs::divide, a, b, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withStrictFloatingPoint().execute();
        }
        for (int i = 0; i < N; i++) {
            assertEquals(Double.doubleToRawLongBits(a.get(i) / b.get(i)), Double.doubleToRawLongBits(out.get(i)), "index " + i);
        }
    }
}
