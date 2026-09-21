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
package uk.ac.manchester.tornado.unittests.cudf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.cudf.Cudf;
import uk.ac.manchester.tornado.cudf.provider.CudfLibraryProvider;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * cuDF library tasks: sort order, grouped aggregation, running sum and inner join.
 *
 * <p>
 * Skipped unless the {@code libtornado-cudf.so} shim has been built, which is the usual case --
 * see {@code tornado-cudf/README.md}. Skipping rather than failing is the same contract every
 * other library test here has: the binding is only exercisable where the library exists.
 */
public class TestCudf extends TornadoTestBase {

    private static final Random random = new Random(42);

    @Before
    public void cudfMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "cuDF library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        if (!CudfLibraryProvider.isAvailable()) {
            throw new TornadoVMCUDANotSupported("the cuDF shim (libtornado-cudf.so) is not built on this host");
        }
    }

    /** A per-row map, so every test also proves a generated kernel and cuDF share a task graph. */
    public static void scale(DoubleArray in, DoubleArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) * 2.0);
        }
    }

    /** The same, over keys: doubling preserves the order, so a sort of the result is still checkable. */
    public static void doubleKeys(IntArray in, IntArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) * 2);
        }
    }

    /** Applies a permutation, which is the per-row half of an ORDER BY and reads what cuDF wrote. */
    public static void gather(IntArray order, DoubleArray values, DoubleArray out) {
        for (@Parallel int i = 0; i < order.getSize(); i++) {
            out.set(i, values.get(order.get(i)));
        }
    }

    /** Reads what cuDF wrote and writes somewhere else, to close the round trip. */
    public static void shift(DoubleArray in, DoubleArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) + 1.0);
        }
    }

    /**
     * The permutation, which is what an ORDER BY needs rather than sorted rows.
     *
     * <p>Asserted by applying it: a permutation that is the identity, or reversed, or off by one,
     * all look like plausible index arrays. What says it is right is that the values gathered
     * through it come out ascending, and that equal keys keep the order they arrived in.
     */
    @Test
    public void testSortedOrder() throws TornadoExecutionPlanException {
        final int n = 4096;
        IntArray keys = new IntArray(n);
        // Deliberately few distinct keys, so most comparisons are ties and stability is exercised
        // on nearly every element rather than on a handful.
        for (int i = 0; i < n; i++) {
            keys.set(i, random.nextInt(16));
        }
        IntArray order = new IntArray(n);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys) //
                .libraryTask("order", Cudf::sortedOrder, n, keys, order) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, order);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        boolean[] seen = new boolean[n];
        int previousKey = Integer.MIN_VALUE;
        int previousPosition = -1;
        for (int i = 0; i < n; i++) {
            int position = order.get(i);
            assertTrue("position " + position + " out of range", position >= 0 && position < n);
            assertFalse("position " + position + " appears twice", seen[position]);
            seen[position] = true;

            int key = keys.get(position);
            assertTrue("keys are not ascending at " + i, key >= previousKey);
            if (key == previousKey) {
                assertTrue("equal keys were reordered; the sort is not stable", position > previousPosition);
            }
            previousKey = key;
            previousPosition = position;
        }
    }

    @Test
    public void testGroupSum() throws TornadoExecutionPlanException {
        final int n = 8192;
        IntArray keys = new IntArray(n);
        DoubleArray values = new DoubleArray(n);
        Map<Integer, Double> reference = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int key = random.nextInt(64);
            double value = random.nextInt(100);
            keys.set(i, key);
            values.set(i, value);
            // The kernel below doubles every value before cuDF sees it, so the reference does too.
            reference.merge(key, value * 2.0, Double::sum);
        }
        DoubleArray scaled = new DoubleArray(n);
        IntArray outKeys = new IntArray(n);
        DoubleArray outSums = new DoubleArray(n);
        IntArray outGroups = new IntArray(1);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys, values) //
                .task("scale", TestCudf::scale, values, scaled) //
                .libraryTask("agg", Cudf::groupSum, n, keys, scaled, outKeys, outSums, outGroups) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outKeys, outSums, outGroups);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        int groups = outGroups.get(0);
        assertEquals("one row per distinct key", reference.size(), groups);
        for (int i = 0; i < groups; i++) {
            Double expected = reference.get(outKeys.get(i));
            assertTrue("unexpected group key " + outKeys.get(i), expected != null);
            assertEquals(expected, outSums.get(i), 1e-9);
        }
    }

    @Test
    public void testRunningSum() throws TornadoExecutionPlanException {
        final int n = 4096;
        DoubleArray values = new DoubleArray(n);
        for (int i = 0; i < n; i++) {
            values.set(i, random.nextInt(10));
        }
        DoubleArray out = new DoubleArray(n);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, values) //
                .libraryTask("scan", Cudf::runningSum, n, values, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        double running = 0.0;
        for (int i = 0; i < n; i++) {
            running += values.get(i);
            assertEquals(running, out.get(i), 1e-9);
        }
    }

    @Test
    public void testInnerJoin() throws TornadoExecutionPlanException {
        final int leftCount = 2048;
        final int rightCount = 1024;
        final int capacity = 1 << 16;
        IntArray left = new IntArray(leftCount);
        IntArray right = new IntArray(rightCount);
        for (int i = 0; i < leftCount; i++) {
            left.set(i, random.nextInt(256));
        }
        for (int i = 0; i < rightCount; i++) {
            right.set(i, random.nextInt(256));
        }
        IntArray outLeft = new IntArray(capacity);
        IntArray outRight = new IntArray(capacity);
        IntArray outCount = new IntArray(1);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, left, right) //
                .libraryTask("join", Cudf::innerJoin, leftCount, left, rightCount, right, capacity, outLeft, outRight, outCount) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outLeft, outRight, outCount);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        // Every pair must agree on the key, and there must be as many pairs as the nested loop
        // would have produced.
        long expected = 0;
        for (int i = 0; i < leftCount; i++) {
            for (int j = 0; j < rightCount; j++) {
                if (left.get(i) == right.get(j)) {
                    expected++;
                }
            }
        }
        int matched = outCount.get(0);
        assertEquals(expected, matched);
        for (int i = 0; i < matched; i++) {
            assertEquals(left.get(outLeft.get(i)), right.get(outRight.get(i)));
        }
    }

    /**
     * One row, for each primitive that scans: the degenerate input every scan gets wrong in a
     * different way -- an off-by-one loop bound reads nothing, a prefix sum seeded with the first
     * element doubles it -- and which a random 4096-row input can never distinguish.
     */
    @Test
    public void testSortedOrderSingleRow() throws TornadoExecutionPlanException {
        IntArray keys = new IntArray(1);
        keys.set(0, 7);
        IntArray order = new IntArray(1);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys) //
                .libraryTask("order", Cudf::sortedOrder, 1, keys, order) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, order);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        assertEquals("the only permutation of one row is the identity", 0, order.get(0));
    }

    @Test
    public void testRunningSumSingleRow() throws TornadoExecutionPlanException {
        DoubleArray values = new DoubleArray(1);
        values.set(0, 3.5);
        DoubleArray out = new DoubleArray(1);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, values) //
                .libraryTask("scan", Cudf::runningSum, 1, values, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        assertEquals("an inclusive scan of one row is that row", 3.5, out.get(0), 1e-9);
    }

    /**
     * Every key the same. The group count collapses to one, which is where a grouped aggregation
     * that secretly emits one row per input row still produces the right sums and only the count
     * gives it away.
     */
    @Test
    public void testGroupSumSingleGroup() throws TornadoExecutionPlanException {
        final int n = 4096;
        IntArray keys = new IntArray(n);
        DoubleArray values = new DoubleArray(n);
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            double value = random.nextInt(100);
            keys.set(i, 11);
            values.set(i, value);
            total += value;
        }
        IntArray outKeys = new IntArray(n);
        DoubleArray outSums = new DoubleArray(n);
        IntArray outGroups = new IntArray(1);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys, values) //
                .libraryTask("agg", Cudf::groupSum, n, keys, values, outKeys, outSums, outGroups) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outKeys, outSums, outGroups);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        assertEquals("one distinct key is one group", 1, outGroups.get(0));
        assertEquals(11, outKeys.get(0));
        assertEquals(total, outSums.get(0), 1e-9);
    }

    /**
     * Every key distinct -- the other end of the same axis, where the number of groups equals the
     * number of rows and each sum is a single value passed through.
     */
    @Test
    public void testGroupSumAllDistinctKeys() throws TornadoExecutionPlanException {
        final int n = 2048;
        IntArray keys = new IntArray(n);
        DoubleArray values = new DoubleArray(n);
        Map<Integer, Double> reference = new HashMap<>();
        for (int i = 0; i < n; i++) {
            double value = random.nextInt(100);
            keys.set(i, i);
            values.set(i, value);
            reference.put(i, value);
        }
        IntArray outKeys = new IntArray(n);
        DoubleArray outSums = new DoubleArray(n);
        IntArray outGroups = new IntArray(1);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys, values) //
                .libraryTask("agg", Cudf::groupSum, n, keys, values, outKeys, outSums, outGroups) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outKeys, outSums, outGroups);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        assertEquals("n distinct keys are n groups", n, outGroups.get(0));
        for (int i = 0; i < n; i++) {
            Double expected = reference.get(outKeys.get(i));
            assertTrue("unexpected group key " + outKeys.get(i), expected != null);
            assertEquals(expected, outSums.get(i), 1e-9);
        }
    }

    /**
     * Maximum fan-out: one key on both sides, so the result is the full cartesian product. The
     * random join above exercises duplicate keys statistically; this one pins the bound-pair
     * arithmetic, which is where a join that takes lower bound for upper emits leftCount pairs
     * instead of leftCount * rightCount and still looks like a join.
     */
    @Test
    public void testInnerJoinAllKeysEqual() throws TornadoExecutionPlanException {
        final int leftCount = 64;
        final int rightCount = 32;
        final int capacity = leftCount * rightCount;
        IntArray left = new IntArray(leftCount);
        IntArray right = new IntArray(rightCount);
        for (int i = 0; i < leftCount; i++) {
            left.set(i, 5);
        }
        for (int i = 0; i < rightCount; i++) {
            right.set(i, 5);
        }
        IntArray outLeft = new IntArray(capacity);
        IntArray outRight = new IntArray(capacity);
        IntArray outCount = new IntArray(1);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, left, right) //
                .libraryTask("join", Cudf::innerJoin, leftCount, left, rightCount, right, capacity, outLeft, outRight, outCount) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outLeft, outRight, outCount);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        assertEquals("every left row matches every right row", capacity, outCount.get(0));
        boolean[][] seen = new boolean[leftCount][rightCount];
        for (int i = 0; i < capacity; i++) {
            int l = outLeft.get(i);
            int r = outRight.get(i);
            assertFalse("pair (" + l + "," + r + ") appears twice", seen[l][r]);
            seen[l][r] = true;
        }
    }

    /**
     * A join too large for the buffers it was given must fail, not truncate: a short join is a
     * wrong answer that no row count catches, because the caller reads the count the join wrote.
     *
     * <p>Also the only test of the error path as a whole -- the shim stores the message, returns a
     * non-zero status, and the binding reads it back through {@code tornado_cudf_last_error} to
     * build the exception. Asserting the message reached Java is what says the whole chain works.
     */
    @Test
    public void testInnerJoinCapacityExceeded() {
        final int leftCount = 64;
        final int rightCount = 32;
        // Room for a hundredth of the 2048 pairs this join produces.
        final int capacity = 16;
        IntArray left = new IntArray(leftCount);
        IntArray right = new IntArray(rightCount);
        for (int i = 0; i < leftCount; i++) {
            left.set(i, 5);
        }
        for (int i = 0; i < rightCount; i++) {
            right.set(i, 5);
        }
        IntArray outLeft = new IntArray(capacity);
        IntArray outRight = new IntArray(capacity);
        IntArray outCount = new IntArray(1);
        outCount.set(0, -1);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, left, right) //
                .libraryTask("join", Cudf::innerJoin, leftCount, left, rightCount, right, capacity, outLeft, outRight, outCount) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outLeft, outRight, outCount);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
            fail("a join of " + (leftCount * rightCount) + " pairs into a buffer of " + capacity + " must not report success");
        } catch (Exception expected) {
            String chain = "";
            for (Throwable t = expected; t != null; t = t.getCause()) {
                chain += String.valueOf(t.getMessage()) + " ";
            }
            assertTrue("the shim's own message should reach Java, not just a status code; got: " + chain, chain.contains("capacity"));
        }
    }

    /**
     * Generated kernel then cuDF, asserting the buffer between them.
     *
     * <p>The composition is the reason for the module, and it is also the thing most likely to be
     * quietly wrong: if the library task read a stale host copy rather than what the kernel had
     * just written on the device, a doubling would simply be missing and the sums would still look
     * like sums. So the intermediate comes back too and is checked against what the kernel owed,
     * separately from the aggregation over it.
     */
    @Test
    public void testJitThenCudf() throws TornadoExecutionPlanException {
        final int n = 8192;
        IntArray keys = new IntArray(n);
        DoubleArray values = new DoubleArray(n);
        Map<Integer, Double> reference = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int key = random.nextInt(64);
            double value = random.nextInt(100);
            keys.set(i, key);
            values.set(i, value);
            reference.merge(key, value * 2.0, Double::sum);
        }
        DoubleArray scaled = new DoubleArray(n);
        IntArray outKeys = new IntArray(n);
        DoubleArray outSums = new DoubleArray(n);
        IntArray outGroups = new IntArray(1);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys, values) //
                .task("scale", TestCudf::scale, values, scaled) //
                .libraryTask("agg", Cudf::groupSum, n, keys, scaled, outKeys, outSums, outGroups) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, scaled, outKeys, outSums, outGroups);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < n; i++) {
            assertEquals("the kernel's own output at " + i, values.get(i) * 2.0, scaled.get(i), 1e-9);
        }

        int groups = outGroups.get(0);
        assertEquals("one row per distinct key", reference.size(), groups);
        for (int i = 0; i < groups; i++) {
            Double expected = reference.get(outKeys.get(i));
            assertTrue("unexpected group key " + outKeys.get(i), expected != null);
            assertEquals("cuDF summed what the kernel wrote", expected, outSums.get(i), 1e-9);
        }
    }

    /**
     * Generated kernel, cuDF, generated kernel -- the round trip, with every buffer between them
     * asserted.
     *
     * <p>The middle stage writes a permutation and the stage after it reads that permutation as
     * indices, so this exercises the direction the test above does not: a kernel consuming what a
     * library task produced. Ordering doubled keys and gathering the values through the result has
     * to agree, row for row, with the host sorting the same rows by the same keys.
     */
    @Test
    public void testJitThenCudfThenJit() throws TornadoExecutionPlanException {
        final int n = 4096;
        IntArray keys = new IntArray(n);
        DoubleArray values = new DoubleArray(n);
        for (int i = 0; i < n; i++) {
            // Few distinct keys again, so the gather has to respect a stable order rather than a
            // total one.
            keys.set(i, random.nextInt(16));
            values.set(i, random.nextDouble());
        }
        IntArray doubled = new IntArray(n);
        IntArray order = new IntArray(n);
        DoubleArray gathered = new DoubleArray(n);
        DoubleArray shifted = new DoubleArray(n);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys, values) //
                .task("double", TestCudf::doubleKeys, keys, doubled) //
                .libraryTask("order", Cudf::sortedOrder, n, doubled, order) //
                .task("gather", TestCudf::gather, order, values, gathered) //
                .task("shift", TestCudf::shift, gathered, shifted) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, doubled, order, gathered, shifted);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < n; i++) {
            assertEquals("the first kernel's output at " + i, keys.get(i) * 2, doubled.get(i));
        }

        // What the host would have produced: a stable sort of the rows by key, which is the order
        // the permutation has to describe.
        Integer[] hostOrder = new Integer[n];
        for (int i = 0; i < n; i++) {
            hostOrder[i] = i;
        }
        Arrays.sort(hostOrder, Comparator.comparingInt(a -> keys.get(a)));

        for (int i = 0; i < n; i++) {
            assertEquals("permutation differs from a stable host sort at " + i, hostOrder[i].intValue(), order.get(i));
            assertEquals("the second kernel gathered the wrong value at " + i, values.get(hostOrder[i]), gathered.get(i), 1e-9);
            assertEquals("the third kernel read what the second wrote at " + i, values.get(hostOrder[i]) + 1.0, shifted.get(i), 1e-9);
        }
    }
}
