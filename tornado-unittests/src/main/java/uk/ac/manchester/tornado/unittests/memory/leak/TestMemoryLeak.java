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
package uk.ac.manchester.tornado.unittests.memory.leak;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 */
public class TestMemoryLeak extends TornadoTestBase {
    // CHECKSTYLE:OFF

    public static final int TEST_TIME_MINUTES = 5;

    public static void vectorAddLong(LongArray a, LongArray b, LongArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void vectorAddLongPrimitive(long[] a, long[] b, long[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    @Test
    public void test_no_cached_hot_loop() {
        Instant end = Instant.now().plus(Duration.ofMinutes(TEST_TIME_MINUTES));
        final int numElements = 2097152; // 8MB
        while (Instant.now().isBefore(end)) {
            LongArray a = new LongArray(numElements);
            LongArray b = new LongArray(numElements);
            LongArray c = new LongArray(numElements);

            IntStream.range(0, numElements).parallel().forEach(i -> {
                a.set(i, i);
                b.set(i, i);
            });

            TaskGraph taskGraph = new TaskGraph("s0") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                    .task("t0", TestMemoryLeak::vectorAddLong, a, b, c) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

            ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
                executionPlan.execute();
            } catch (TornadoExecutionPlanException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void test_no_cached_hot_loop_primitive() {
        Instant end = Instant.now().plus(Duration.ofMinutes(TEST_TIME_MINUTES));
        final int numElements = 2097152; // 8MB

        while (Instant.now().isBefore(end)) {
            long[] a = new long[numElements];
            long[] b = new long[numElements];
            long[] c = new long[numElements];

            IntStream.range(0, numElements).parallel().forEach(i -> {
                a[i] = i;
                b[i] = i;
            });

            TaskGraph taskGraph = new TaskGraph("s0") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                    .task("t0", TestMemoryLeak::vectorAddLongPrimitive, a, b, c) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

            ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
                executionPlan.execute();
            } catch (TornadoExecutionPlanException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void test_cached_task_graph_and_input_output_primitive() {
        Instant end = Instant.now().plus(Duration.ofMinutes(TEST_TIME_MINUTES));
        final int numElements = 2097152; // 8MB
        long[] a = new long[numElements];
        long[] b = new long[numElements];
        long[] c = new long[numElements];

        IntStream.range(0, numElements).parallel().forEach(i -> {
            a[i] = i;
            b[i] = i;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMemoryLeak::vectorAddLongPrimitive, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        while (Instant.now().isBefore(end)) {
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
                executionPlan.execute();
            } catch (TornadoExecutionPlanException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void test_cached_task_graph_and_input_output() {
        Instant end = Instant.now().plus(Duration.ofMinutes(TEST_TIME_MINUTES));
        final int numElements = 2097152; // 8MB
        LongArray a = new LongArray(numElements);
        LongArray b = new LongArray(numElements);
        LongArray c = new LongArray(numElements);

        IntStream.range(0, numElements).parallel().forEach(i -> {
            a.set(i, i);
            b.set(i, i);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMemoryLeak::vectorAddLong, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        while (Instant.now().isBefore(end)) {
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
                executionPlan.execute();
            } catch (TornadoExecutionPlanException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void test_cached_everything_primitive() {
        Instant end = Instant.now().plus(Duration.ofMinutes(TEST_TIME_MINUTES));
        final int numElements = 2097152; // 8MB
        long[] a = new long[numElements];
        long[] b = new long[numElements];
        long[] c = new long[numElements];

        IntStream.range(0, numElements).parallel().forEach(i -> {
            a[i] = i;
            b[i] = i;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMemoryLeak::vectorAddLongPrimitive, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            while (Instant.now().isBefore(end)) {
                executionPlan.execute();
            }
        } catch (TornadoExecutionPlanException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test_cached_everything() {
        Instant end = Instant.now().plus(Duration.ofMinutes(TEST_TIME_MINUTES));
        final int numElements = 2097152; // 8MB
        LongArray a = new LongArray(numElements);
        LongArray b = new LongArray(numElements);
        LongArray c = new LongArray(numElements);

        IntStream.range(0, numElements).parallel().forEach(i -> {
            a.set(i, i);
            b.set(i, i);
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMemoryLeak::vectorAddLong, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            while (Instant.now().isBefore(end)) {
                executionPlan.execute();
            }
        } catch (TornadoExecutionPlanException e) {
            throw new RuntimeException(e);
        }
    }

    // CHECKSTYLE:ON
}
