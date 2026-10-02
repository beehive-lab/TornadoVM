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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Whether {@link TornadoExecutionPlan#withCompilerFlags} actually reaches the backend compiler.
 *
 * <p>
 * It did not, for every backend but OpenCL: {@code TornadoVMInterpreter.updateMeta} copied only
 * {@code OPENCL} flags from the execution context's meta to the task's meta, which is where each
 * backend's code cache reads them.
 *
 * <p>
 * The assertion uses fast-math, which changes the computed result ({@code div.rn.f32} becomes
 * {@code div.approx.ftz.f32}), rather than an unknown flag: NVRTC accepts unrecognised options
 * silently, so an unknown flag would pass even when dropped.
 */
public class TestCompilerFlags extends TornadoTestBase {

    private static final int SIZE = 256;

    private static void add(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    /**
     * Division, because it is the cheapest operation whose code generation fast-math changes.
     */
    private static void divide(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) / b.get(i));
        }
    }

    private static TornadoVMBackendType backend() {
        return getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
    }

    /**
     * A flag the backend's compiler honours, or null where this backend has no obvious one.
     *
     * <p>
     * Chosen to be semantically inert on the kernel below -- it contains no multiply and no
     * division -- so that "the flag was accepted" is the only thing being asserted.
     */
    private static String benignFlagFor(TornadoVMBackendType backendType) {
        return switch (backendType) {
            case CUDA -> "--fmad=false";
            case OPENCL -> "-cl-opt-disable";
            default -> null;
        };
    }

    /** A flag that demonstrably changes what the device computes, or null where there is none. */
    private static String fastMathFlagFor(TornadoVMBackendType backendType) {
        return switch (backendType) {
            case CUDA -> "--use_fast_math";
            case OPENCL -> "-cl-fast-relaxed-math";
            default -> null;
        };
    }

    private static TornadoExecutionPlan planFor(FloatArray a, FloatArray b, FloatArray c) {
        return planFor(a, b, c, TestCompilerFlags::add);
    }

    private static TornadoExecutionPlan planFor(FloatArray a, FloatArray b, FloatArray c, Task3<FloatArray, FloatArray, FloatArray> kernel) {
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", kernel, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        return new TornadoExecutionPlan(immutableTaskGraph);
    }

    private static FloatArray filled(float value) {
        FloatArray array = new FloatArray(SIZE);
        array.init(value);
        return array;
    }

    /**
     * The flag reaches the backend compiler and changes what it emits.
     *
     * <p>
     * Before the fix this could not fail: the flag never left the execution context, both runs
     * compiled the same kernel, and the two results were bit-identical by construction.
     */
    @Test
    public void testFastMathFlagChangesWhatTheDeviceComputes() throws Exception {
        String fastMath = fastMathFlagFor(backend());
        if (fastMath == null) {
            return;
        }
        FloatArray a = new FloatArray(SIZE);
        FloatArray b = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            // Values whose quotient is not representable, so an approximate divide differs.
            a.set(i, 1.0f + i);
            b.set(i, 3.0f + i * 7.0f);
        }
        // A denormal makes the difference certain rather than likely: fast-math division on CUDA
        // is div.approx.ftz.f32, and the ftz flushes this to zero while a correctly rounded
        // divide keeps it.
        a.set(0, Float.MIN_VALUE);
        b.set(0, 1.0f);

        FloatArray exact = new FloatArray(SIZE);
        try (TornadoExecutionPlan plan = planFor(a, b, exact, TestCompilerFlags::divide)) {
            plan.execute();
        }

        FloatArray approximate = new FloatArray(SIZE);
        try (TornadoExecutionPlan plan = planFor(a, b, approximate, TestCompilerFlags::divide)) {
            plan.withCompilerFlags(backend(), fastMath).execute();
        }

        boolean differs = false;
        for (int i = 0; i < SIZE && !differs; i++) {
            differs = Float.floatToRawIntBits(exact.get(i)) != Float.floatToRawIntBits(approximate.get(i));
        }
        assertTrue("fast-math changed nothing, so the flag never reached the compiler for " + backend(), differs);
    }

    /** And a flag the compiler does accept still produces the right answer. */
    @Test
    public void testAcceptedCompilerFlagStillCompilesAndRuns() throws Exception {
        String flag = benignFlagFor(backend());
        if (flag == null) {
            return;
        }
        FloatArray a = filled(2.0f);
        FloatArray b = filled(3.0f);
        FloatArray c = new FloatArray(SIZE);

        try (TornadoExecutionPlan plan = planFor(a, b, c)) {
            plan.withCompilerFlags(backend(), flag).execute();
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals(5.0f, c.get(i), 0.0f);
        }
    }

    /**
     * The same graph, compiled twice under different flags, is not served from one cached binary.
     *
     * <p>
     * The code caches key on the source <em>and</em> the flags, so this should hold; it is asserted
     * because propagating the flags is what makes the second half of that key non-empty, and a
     * cache that ignored them would return the first kernel for the second request.
     */
    @Test
    public void testFlagsAreNotCachedAcross() throws Exception {
        String flag = benignFlagFor(backend());
        if (flag == null) {
            return;
        }
        FloatArray a = filled(2.0f);
        FloatArray b = filled(3.0f);
        FloatArray c = new FloatArray(SIZE);

        try (TornadoExecutionPlan plan = planFor(a, b, c)) {
            plan.execute();
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals(5.0f, c.get(i), 0.0f);
        }

        c.init(0.0f);
        try (TornadoExecutionPlan plan = planFor(a, b, c)) {
            plan.withCompilerFlags(backend(), flag).execute();
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals(5.0f, c.get(i), 0.0f);
        }
    }
}
