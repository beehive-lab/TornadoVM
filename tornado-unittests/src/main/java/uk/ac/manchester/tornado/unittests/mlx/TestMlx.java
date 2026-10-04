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

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.mlx.Mlx;
import uk.ac.manchester.tornado.mlx.provider.MlxLibraryProvider;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMetalNotSupported;

/**
 * Apple MLX library tasks ({@code apple/mlx}) on the Metal backend.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlx
 * </code>
 */
public class TestMlx extends TornadoTestBase {

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

    private static FloatArray randomFloats(int n, Random random) {
        FloatArray array = new FloatArray(n);
        for (int i = 0; i < n; i++) {
            array.set(i, random.nextFloat() * 2 - 1);
        }
        return array;
    }

    public static void iota(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, i);
        }
    }

    public static void addOne(FloatArray a, FloatArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, a.get(i) + 1.0f);
        }
    }

    private static void addFloat(int n) throws TornadoExecutionPlanException {
        Random random = new Random(n);
        FloatArray a = randomFloats(n, random);
        FloatArray b = randomFloats(n, random);
        FloatArray c = new FloatArray(n);

        long dispatches = MlxLibraryProvider.kernelDispatches();
        TaskGraph taskGraph = new TaskGraph("mlxAdd") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .libraryTask("add", Mlx::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        for (int i = 0; i < n; i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.0f);
        }
        assertEquals("the task did not run as an in-place MLX kernel", dispatches + 1, MlxLibraryProvider.kernelDispatches());
    }

    @Test
    public void testAddFloatOneElement() throws TornadoExecutionPlanException {
        addFloat(1);
    }

    @Test
    public void testAddFloat() throws TornadoExecutionPlanException {
        addFloat(1000);
    }

    @Test
    public void testAddFloatLarge() throws TornadoExecutionPlanException {
        addFloat(1 << 22);
    }

    /** A result copied out by several threads whose last chunk is shorter than the others. */
    @Test
    public void testAddFloatLargeOddSize() throws TornadoExecutionPlanException {
        addFloat((1 << 22) + 13);
    }

    @Test
    public void testAddHalfFloat() throws TornadoExecutionPlanException {
        final int n = 1027;
        HalfFloatArray a = new HalfFloatArray(n);
        HalfFloatArray b = new HalfFloatArray(n);
        HalfFloatArray c = new HalfFloatArray(n);
        for (int i = 0; i < n; i++) {
            a.set(i, new HalfFloat(i * 0.25f));
            b.set(i, new HalfFloat(-i * 0.125f));
        }
        TaskGraph taskGraph = new TaskGraph("mlxAddHalf") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .libraryTask("add", Mlx::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        for (int i = 0; i < n; i++) {
            float expected = new HalfFloat(a.get(i).getFloat32() + b.get(i).getFloat32()).getFloat32();
            assertEquals(expected, c.get(i).getFloat32(), 0.0f);
        }
    }

    /**
     * JIT -> MLX -> JIT with no host transfers in between: MLX reads what the first kernel wrote,
     * the second kernel reads what MLX wrote.
     */
    @Test
    public void testJitMlxJit() throws TornadoExecutionPlanException {
        final int n = 4096;
        FloatArray a = new FloatArray(n);
        FloatArray b = new FloatArray(n);
        FloatArray c = new FloatArray(n);
        TaskGraph taskGraph = new TaskGraph("jitMlxJit") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, c) //
                .task("iota", TestMlx::iota, a) //
                .libraryTask("add", Mlx::add, a, a, b) //
                .task("addOne", TestMlx::addOne, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
        for (int i = 0; i < n; i++) {
            assertEquals(2.0f * i + 1.0f, c.get(i), 0.0f);
        }
    }

    /**
     * Inputs are re-sent on every execution; MLX's cached wrapper must see the new values
     * because it is the same memory, not a copy.
     */
    @Test
    public void testInputsChangeBetweenExecutions() throws TornadoExecutionPlanException {
        final int n = 2048;
        FloatArray a = new FloatArray(n);
        FloatArray b = new FloatArray(n);
        FloatArray c = new FloatArray(n);
        TaskGraph taskGraph = new TaskGraph("mlxRepeat") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("add", Mlx::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int iteration = 0; iteration < 5; iteration++) {
                a.init(iteration);
                b.init(10.0f * iteration);
                executionPlan.execute();
                for (int i = 0; i < n; i++) {
                    assertEquals(11.0f * iteration, c.get(i), 0.0f);
                }
            }
        }
    }

    /**
     * Consecutive execution plans with new arrays: TornadoVM may hand the second plan buffers at
     * the addresses the first plan used. A wrapper from the first plan must never be reused.
     */
    @Test
    public void testConsecutivePlans() throws TornadoExecutionPlanException {
        final int n = 4096;
        for (int plan = 0; plan < 4; plan++) {
            FloatArray a = new FloatArray(n);
            FloatArray b = new FloatArray(n);
            FloatArray c = new FloatArray(n);
            a.init(plan + 1.0f);
            b.init(100.0f * (plan + 1));
            TaskGraph taskGraph = new TaskGraph("mlxPlan" + plan) //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                    .libraryTask("add", Mlx::add, a, b, c) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                executionPlan.execute();
            }
            for (int i = 0; i < n; i++) {
                assertEquals("plan " + plan, 101.0f * (plan + 1), c.get(i), 0.0f);
            }
        }
    }
}
