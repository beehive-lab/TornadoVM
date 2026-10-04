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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.mlx.Mlx;
import uk.ac.manchester.tornado.mlx.MlxReduce;
import uk.ac.manchester.tornado.mlx.provider.MlxLibraryProvider;

/**
 * Unit tests for how MLX library tasks behave inside TornadoVM: they run as TornadoVM/MLX kernels on
 * the task graph's buffers, mix with JIT-compiled tasks in one graph, read buffers produced by
 * another task graph, see new inputs on every execution, and fail on arguments no kernel covers. The
 * operations themselves are tested per category in the other {@code TestMlx*} classes. Skipped
 * unless the default device is on the Metal backend and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlx
 * </code>
 */
public class TestMlx extends MlxTestBase {

    private static final int SIZE = 4096;

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

    public static void addOneInPlace(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, a.get(i) + 1.0f);
        }
    }

    @Test
    public void testRunsAsTornadoVMMlxKernel() throws TornadoExecutionPlanException {
        FloatArray a = FloatArray.fromArray(values(SIZE, -1, 1, 1));
        FloatArray b = FloatArray.fromArray(values(SIZE, -1, 1, 2));
        FloatArray c = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("add", Mlx::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        long before = MlxLibraryProvider.kernelDispatches();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertEquals("kernel dispatches", before + 1, MlxLibraryProvider.kernelDispatches());
        for (int i = 0; i < SIZE; i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0f);
        }
    }

    @Test
    public void testMixedPrePostTasks() throws TornadoExecutionPlanException {
        // JIT -> MLX -> JIT with no host transfers in between: MLX reads what the first kernel wrote,
        // the second kernel reads what MLX wrote.
        FloatArray a = new FloatArray(SIZE);
        FloatArray b = new FloatArray(SIZE);
        FloatArray c = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, c) //
                .task("iota", TestMlx::iota, a) //
                .libraryTask("add", Mlx::add, a, a, b) //
                .task("addOne", TestMlx::addOne, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(2.0f * i + 1.0f, c.get(i), 0f);
        }
    }

    @Test
    public void testChainOfMlxTasks() throws TornadoExecutionPlanException {
        // sqrt(a)^2 - a, each MLX task reading the previous one's output on the device.
        final int n = 1 << 18;
        FloatArray a = FloatArray.fromArray(values(n, 0.5f, 2, 3));
        FloatArray b = new FloatArray(n);
        FloatArray c = new FloatArray(n);
        FloatArray d = new FloatArray(n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .libraryTask("sqrt", Mlx::sqrt, a, b) //
                .libraryTask("square", Mlx::multiply, b, b, c) //
                .libraryTask("subtract", Mlx::subtract, c, a, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, d);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < n; i++) {
            assertEquals("element " + i, 0.0f, d.get(i), 1e-5f * a.get(i));
        }
    }

    @Test
    public void testSharedBufferAcrossTaskGraphs() throws TornadoExecutionPlanException {
        // A JIT task graph produces a on the device; a second task graph consumes it with an MLX task,
        // without the data returning to the host.
        float[] av = values(SIZE, -1, 1, 4);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(values(SIZE, -1, 1, 5));
        FloatArray c = new FloatArray(SIZE);

        TaskGraph producer = new TaskGraph("producer") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("mutate", TestMlx::addOneInPlace, a) //
                .persistOnDevice(a);

        TaskGraph consumer = new TaskGraph("consumer") //
                .consumeFromDevice(producer.getTaskGraphName(), a) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, b) //
                .libraryTask("multiply", Mlx::multiply, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(producer.snapshot(), consumer.snapshot())) {
            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals("element " + i, (av[i] + 1.0f) * b.get(i), c.get(i), 1e-6f);
        }
    }

    @Test
    public void testInputsChangeBetweenExecutions() throws TornadoExecutionPlanException {
        // Inputs are re-sent on every execution; the kernels read the same buffers, so they see the new values.
        FloatArray a = new FloatArray(SIZE);
        FloatArray b = new FloatArray(SIZE);
        FloatArray c = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("add", Mlx::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int iteration = 0; iteration < 5; iteration++) {
                a.init(iteration);
                b.init(10.0f * iteration);
                plan.execute();
                for (int i = 0; i < SIZE; i++) {
                    assertEquals("iteration " + iteration, 11.0f * iteration, c.get(i), 0f);
                }
            }
        }
    }

    @Test
    public void testConsecutivePlans() throws TornadoExecutionPlanException {
        // New arrays in each plan: TornadoVM may hand a later plan buffers at the addresses an earlier
        // plan used, and the result must still come from the new arrays.
        for (int p = 0; p < 4; p++) {
            FloatArray a = new FloatArray(SIZE);
            FloatArray b = new FloatArray(SIZE);
            FloatArray c = new FloatArray(SIZE);
            a.init(p + 1.0f);
            b.init(100.0f * (p + 1));

            TaskGraph taskGraph = new TaskGraph("g" + p) //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                    .libraryTask("add", Mlx::add, a, b, c) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            for (int i = 0; i < SIZE; i++) {
                assertEquals("plan " + p, 101.0f * (p + 1), c.get(i), 0f);
            }
        }
    }

    @Test
    public void testOneElement() throws TornadoExecutionPlanException {
        FloatArray a = FloatArray.fromElements(1.5f);
        FloatArray b = FloatArray.fromElements(-4f);
        FloatArray c = new FloatArray(1);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("add", Mlx::add, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertEquals(-2.5f, c.get(0), 0f);
    }

    @Test
    public void testUnsupportedArgumentsFail() {
        // A sum over a middle axis with inner > 1 has no TornadoVM/MLX kernel: the task fails, naming
        // the operation and its arguments, rather than falling back.
        FloatArray x = FloatArray.fromArray(values(4 * 8 * 3, -1, 1, 6));
        FloatArray output = new FloatArray(4 * 3);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("sum", MlxReduce::sumAxis, x, output, 4, 8, 3) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        RuntimeException e = assertThrows(RuntimeException.class, () -> {
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }
        });
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        assertTrue("message: " + cause.getMessage(), cause.getMessage().contains("MLX sum_axis: no TornadoVM/MLX kernel takes these arguments"));
    }
}
