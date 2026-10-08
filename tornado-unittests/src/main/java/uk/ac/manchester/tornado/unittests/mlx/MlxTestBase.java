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
package uk.ac.manchester.tornado.unittests.mlx;

import static org.junit.Assert.fail;

import java.util.Random;

import org.junit.Before;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.BFloat16;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.mlx.provider.MlxLibraryProvider;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMetalNotSupported;

/**
 * Common set-up for the MLX library-task tests: the Metal/MLX guard, graph execution, input
 * generation and tolerance checks.
 */
public abstract class MlxTestBase extends TornadoTestBase {

    /**
     * MLX tasks need the Metal backend and MLX's mlx.metallib. Unavailable configurations throw the typed
     * *NotSupported exceptions that TornadoTestRunner reports as [UNSUPPORTED].
     */
    @Before
    public void mlxMustBeAvailable() {
        TornadoVMBackendType backend = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backend != TornadoVMBackendType.METAL) {
            assertNotBackend(backend, "MLX library tasks require the Metal backend (default device is " + backend + ")");
        }
        if (!MlxLibraryProvider.isAvailable()) {
            throw new TornadoVMMetalNotSupported("mlx.metallib is not available on this host");
        }
    }

    protected static void run(TaskGraph taskGraph) throws TornadoExecutionPlanException {
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
    }

    /** Uniform values in [lo, hi). */
    protected static float[] values(int n, float lo, float hi, long seed) {
        Random random = new Random(seed);
        float[] v = new float[n];
        for (int i = 0; i < n; i++) {
            v[i] = lo + (hi - lo) * random.nextFloat();
        }
        return v;
    }

    protected static void assertClose(String what, int index, double expected, double actual, double relTol, double absTol) {
        if (Double.isNaN(expected) ? !Double.isNaN(actual) : Math.abs(expected - actual) > absTol + relTol * Math.abs(expected)) {
            fail(what + " element " + index + ": expected " + expected + " but was " + actual);
        }
    }

    protected static void assertAllClose(String what, double[] expected, FloatArray actual, double relTol, double absTol) {
        for (int i = 0; i < expected.length; i++) {
            assertClose(what, i, expected[i], actual.get(i), relTol, absTol);
        }
    }

    protected static void assertAllClose(String what, double[] expected, HalfFloatArray actual, double relTol, double absTol) {
        for (int i = 0; i < expected.length; i++) {
            assertClose(what, i, expected[i], actual.get(i).getFloat32(), relTol, absTol);
        }
    }

    protected static void assertAllClose(String what, double[] expected, BFloat16Array actual, double relTol, double absTol) {
        for (int i = 0; i < expected.length; i++) {
            assertClose(what, i, expected[i], BFloat16.bf16ToFloat(actual.get(i)), relTol, absTol);
        }
    }

    protected static HalfFloatArray half(float[] v) {
        HalfFloatArray a = new HalfFloatArray(v.length);
        for (int i = 0; i < v.length; i++) {
            a.set(i, new HalfFloat(v[i]));
        }
        return a;
    }

    protected static BFloat16Array bf16(float[] v) {
        BFloat16Array a = new BFloat16Array(v.length);
        for (int i = 0; i < v.length; i++) {
            a.set(i, BFloat16.bf16FromFloat(v[i]));
        }
        return a;
    }

    /** The values actually stored in a float16 array, widened. */
    protected static float[] widen(HalfFloatArray a) {
        float[] v = new float[a.getSize()];
        for (int i = 0; i < v.length; i++) {
            v[i] = a.get(i).getFloat32();
        }
        return v;
    }

    /** The values actually stored in a bfloat16 array, widened. */
    protected static float[] widen(BFloat16Array a) {
        float[] v = new float[a.getSize()];
        for (int i = 0; i < v.length; i++) {
            v[i] = BFloat16.bf16ToFloat(a.get(i));
        }
        return v;
    }
}
