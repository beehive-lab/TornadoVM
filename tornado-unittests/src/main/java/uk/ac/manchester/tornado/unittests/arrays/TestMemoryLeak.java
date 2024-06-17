package uk.ac.manchester.tornado.unittests.arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 */
public class TestMemoryLeak extends TornadoTestBase {
    // CHECKSTYLE:OFF

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
        final int numElements = 262144; // 2MB

        while (true) {
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
        final int numElements = 262144; // 2MB

        while (true) {
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
        final int numElements = 262144; // 2MB
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

        while (true) {
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
                executionPlan.execute();
            } catch (TornadoExecutionPlanException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void test_cached_task_graph_and_input_output() {
        final int numElements = 262144; // 2MB
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

        while (true) {
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
                executionPlan.execute();
            } catch (TornadoExecutionPlanException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void test_cached_everything_primitive() {
        final int numElements = 262144; // 2MB
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
            while (true) {
                executionPlan.execute();
            }
        } catch (TornadoExecutionPlanException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test_cached_everything() {
        final int numElements = 262144; // 2MB
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
            while (true) {
                executionPlan.execute();
            }
        } catch (TornadoExecutionPlanException e) {
            throw new RuntimeException(e);
        }
    }

    // CHECKSTYLE:ON
}

