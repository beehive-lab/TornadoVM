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
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.cudf.Cudf;
import uk.ac.manchester.tornado.cudf.enums.CudfAggregation;
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
        // The shim first, because it costs nothing. CudfLibraryProvider.isAvailable() is an FFM
        // symbol lookup and touches no device, while getDefaultDevice() acquires one -- so asking
        // in the other order makes every test on a host without libcudf acquire a device and then
        // abandon it. That is most hosts, and with 23 tests in this class it was 23 acquire-and-
        // abort cycles per CUDA test run: enough device churn to leave the next test class unable
        // to allocate. Cheapest refusal first, and nothing here needs a device to decide.
        if (!CudfLibraryProvider.isAvailable()) {
            throw new TornadoVMCUDANotSupported("the cuDF shim (libtornado-cudf.so) is not built on this host");
        }
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "cuDF library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
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

    /** A predicate as a per-row map, which is the half of a filter a kernel can do. */
    public static void positive(DoubleArray in, ByteArray mask) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            mask.set(i, in.get(i) > 0.0 ? (byte) 1 : (byte) 0);
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

    // ---------------------------------------------------------------------------------------
    // groupAggregate: the general form of groupSum.
    // ---------------------------------------------------------------------------------------

    /** Runs one groupAggregate and hands back {outKeys, outResults, groupCount}. */
    private Object[] groupAggregate(int n, int columns, CudfAggregation aggregation, IntArray keys, DoubleArray values) throws TornadoExecutionPlanException {
        IntArray outKeys = new IntArray(n);
        DoubleArray outResults = new DoubleArray(columns * n);
        IntArray outGroups = new IntArray(1);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys, values) //
                .libraryTask("agg", Cudf::groupAggregate, n, columns, aggregation.code(), keys, values, outKeys, outResults, outGroups) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outKeys, outResults, outGroups);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        return new Object[] { outKeys, outResults, outGroups.get(0) };
    }

    /**
     * Three value columns in one pass, which is the shape a k-means iteration needs: d coordinate
     * sums per cluster, not d separate group-bys over the same keys.
     *
     * <p>Each column is given a different scale, so a packing mistake -- reading column 1 where
     * column 2 was meant, or striding by the group count instead of by n -- produces the wrong
     * totals rather than plausible ones.
     */
    @Test
    public void testGroupAggregateMultipleColumns() throws TornadoExecutionPlanException {
        final int n = 4096;
        final int columns = 3;
        IntArray keys = new IntArray(n);
        DoubleArray values = new DoubleArray(columns * n);
        Map<Integer, double[]> reference = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int key = random.nextInt(32);
            keys.set(i, key);
            double[] totals = reference.computeIfAbsent(key, k -> new double[columns]);
            for (int c = 0; c < columns; c++) {
                double value = random.nextInt(50) * (c + 1);
                values.set(c * n + i, value);
                totals[c] += value;
            }
        }

        Object[] out = groupAggregate(n, columns, CudfAggregation.SUM, keys, values);
        IntArray outKeys = (IntArray) out[0];
        DoubleArray outResults = (DoubleArray) out[1];
        int groups = (Integer) out[2];

        assertEquals("one row per distinct key", reference.size(), groups);
        for (int i = 0; i < groups; i++) {
            double[] expected = reference.get(outKeys.get(i));
            assertTrue("unexpected group key " + outKeys.get(i), expected != null);
            for (int c = 0; c < columns; c++) {
                assertEquals("column " + c + " of group " + outKeys.get(i), expected[c], outResults.get(c * n + i), 1e-9);
            }
        }
    }

    /**
     * MIN, MAX, MEAN and COUNT over the same data, each against a host reference.
     *
     * <p>All four share one dispatch path and differ only in the aggregation object the shim builds,
     * so the thing worth testing is that the code selects the one it claims -- a MIN that quietly
     * computed MAX would pass any test that only checked the shape of the output.
     */
    @Test
    public void testGroupAggregateMinMaxMeanCount() throws TornadoExecutionPlanException {
        final int n = 4096;
        IntArray keys = new IntArray(n);
        DoubleArray values = new DoubleArray(n);
        Map<Integer, Double> mins = new HashMap<>();
        Map<Integer, Double> maxes = new HashMap<>();
        Map<Integer, Double> sums = new HashMap<>();
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int key = random.nextInt(32);
            double value = random.nextInt(1000) - 500;
            keys.set(i, key);
            values.set(i, value);
            mins.merge(key, value, Math::min);
            maxes.merge(key, value, Math::max);
            sums.merge(key, value, Double::sum);
            counts.merge(key, 1, Integer::sum);
        }

        for (CudfAggregation aggregation : new CudfAggregation[] { CudfAggregation.MIN, CudfAggregation.MAX, CudfAggregation.MEAN, CudfAggregation.COUNT }) {
            Object[] out = groupAggregate(n, 1, aggregation, keys, values);
            IntArray outKeys = (IntArray) out[0];
            DoubleArray outResults = (DoubleArray) out[1];
            int groups = (Integer) out[2];
            assertEquals(aggregation + ": one row per distinct key", mins.size(), groups);

            for (int i = 0; i < groups; i++) {
                int key = outKeys.get(i);
                double actual = outResults.get(i);
                switch (aggregation) {
                    case MIN -> assertEquals("MIN of group " + key, mins.get(key), actual, 1e-9);
                    case MAX -> assertEquals("MAX of group " + key, maxes.get(key), actual, 1e-9);
                    case MEAN -> assertEquals("MEAN of group " + key, sums.get(key) / counts.get(key), actual, 1e-9);
                    case COUNT -> assertEquals("COUNT of group " + key, counts.get(key).doubleValue(), actual, 1e-9);
                    default -> fail("unreachable");
                }
            }
        }
    }

    /**
     * The general form and the narrow one have to agree, or one of them is wrong and nothing else
     * would say which.
     */
    @Test
    public void testGroupAggregateAgreesWithGroupSum() throws TornadoExecutionPlanException {
        final int n = 4096;
        IntArray keys = new IntArray(n);
        DoubleArray values = new DoubleArray(n);
        for (int i = 0; i < n; i++) {
            keys.set(i, random.nextInt(32));
            values.set(i, random.nextInt(100));
        }

        IntArray narrowKeys = new IntArray(n);
        DoubleArray narrowSums = new DoubleArray(n);
        IntArray narrowGroups = new IntArray(1);
        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys, values) //
                .libraryTask("agg", Cudf::groupSum, n, keys, values, narrowKeys, narrowSums, narrowGroups) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, narrowKeys, narrowSums, narrowGroups);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        Object[] out = groupAggregate(n, 1, CudfAggregation.SUM, keys, values);
        IntArray generalKeys = (IntArray) out[0];
        DoubleArray generalSums = (DoubleArray) out[1];
        int groups = (Integer) out[2];

        assertEquals(narrowGroups.get(0), groups);
        Map<Integer, Double> narrow = new HashMap<>();
        for (int i = 0; i < groups; i++) {
            narrow.put(narrowKeys.get(i), narrowSums.get(i));
        }
        for (int i = 0; i < groups; i++) {
            assertEquals("groupAggregate disagrees with groupSum on key " + generalKeys.get(i), narrow.get(generalKeys.get(i)), generalSums.get(i), 1e-9);
        }
    }

    // ---------------------------------------------------------------------------------------
    // reduce: the whole-column shape, which is not a group-by.
    // ---------------------------------------------------------------------------------------

    @Test
    public void testReduce() throws TornadoExecutionPlanException {
        final int n = 4096;
        DoubleArray values = new DoubleArray(n);
        double sum = 0.0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double value = random.nextInt(2000) - 1000;
            values.set(i, value);
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        double[] expected = { sum, min, max, sum / n };
        CudfAggregation[] operations = { CudfAggregation.SUM, CudfAggregation.MIN, CudfAggregation.MAX, CudfAggregation.MEAN };
        for (int op = 0; op < operations.length; op++) {
            DoubleArray out = new DoubleArray(1);
            TaskGraph graph = new TaskGraph("cudf") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, values) //
                    .libraryTask("reduce", Cudf::reduce, n, operations[op].code(), values, out) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
                plan.execute();
            }
            assertEquals(operations[op].toString(), expected[op], out.get(0), 1e-6);
        }
    }

    /**
     * COUNT over a whole column is n, which the caller already holds, so the shim refuses rather
     * than launching work to return a number the call site knows.
     */
    @Test
    public void testReduceRefusesCount() {
        final int n = 64;
        DoubleArray values = new DoubleArray(n);
        DoubleArray out = new DoubleArray(1);
        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, values) //
                .libraryTask("reduce", Cudf::reduce, n, CudfAggregation.COUNT.code(), values, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
            fail("an ungrouped COUNT should be refused, not answered");
        } catch (Exception expected) {
            assertTrue("the refusal should say why; got: " + causeChain(expected), causeChain(expected).contains("COUNT"));
        }
    }

    // ---------------------------------------------------------------------------------------
    // selectedIndices: stream compaction, the filter a kernel cannot finish.
    // ---------------------------------------------------------------------------------------

    /**
     * A generated kernel computes the predicate and cuDF compacts it, which is the whole argument
     * for the module in one graph: the map half is what TornadoVM compiles well, and moving the
     * survivors together is the half no @Parallel loop expresses.
     */
    @Test
    public void testSelectedIndices() throws TornadoExecutionPlanException {
        final int n = 4096;
        DoubleArray values = new DoubleArray(n);
        for (int i = 0; i < n; i++) {
            values.set(i, random.nextInt(200) - 100);
        }
        ByteArray mask = new ByteArray(n);
        IntArray indices = new IntArray(n);
        IntArray count = new IntArray(1);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, values) //
                .task("predicate", TestCudf::positive, values, mask) //
                .libraryTask("filter", Cudf::selectedIndices, n, n, mask, indices, count) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, mask, indices, count);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        int expected = 0;
        for (int i = 0; i < n; i++) {
            boolean keep = values.get(i) > 0.0;
            assertEquals("the kernel's own mask at " + i, keep ? 1 : 0, mask.get(i));
            if (keep) {
                expected++;
            }
        }
        assertEquals("survivor count", expected, count.get(0));

        int previous = -1;
        int seen = 0;
        for (int i = 0; i < count.get(0); i++) {
            int position = indices.get(i);
            assertTrue("positions must ascend; " + position + " followed " + previous, position > previous);
            assertTrue("kept a row the predicate rejected: " + position, values.get(position) > 0.0);
            previous = position;
            seen++;
        }
        assertEquals(expected, seen);
    }

    /** Nothing survives, and nothing survives is not the same as nothing happened. */
    @Test
    public void testSelectedIndicesKeepsNone() throws TornadoExecutionPlanException {
        final int n = 256;
        ByteArray mask = new ByteArray(n);
        IntArray indices = new IntArray(n);
        IntArray count = new IntArray(1);
        count.set(0, -1);
        for (int i = 0; i < n; i++) {
            mask.set(i, (byte) 0);
        }

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, mask) //
                .libraryTask("filter", Cudf::selectedIndices, n, n, mask, indices, count) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, indices, count);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        assertEquals("an all-zero mask keeps nothing", 0, count.get(0));
    }

    /** Too many survivors for the buffer must fail, for the same reason a short join must. */
    @Test
    public void testSelectedIndicesCapacityExceeded() {
        final int n = 256;
        final int capacity = 8;
        ByteArray mask = new ByteArray(n);
        IntArray indices = new IntArray(capacity);
        IntArray count = new IntArray(1);
        for (int i = 0; i < n; i++) {
            mask.set(i, (byte) 1);
        }

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, mask) //
                .libraryTask("filter", Cudf::selectedIndices, n, capacity, mask, indices, count) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, indices, count);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
            fail("keeping " + n + " rows in a buffer of " + capacity + " must not report success");
        } catch (Exception expected) {
            assertTrue("the shim's message should reach Java; got: " + causeChain(expected), causeChain(expected).contains("capacity"));
        }
    }

    // ---------------------------------------------------------------------------------------
    // sortedOrderMulti: several keys, either direction.
    // ---------------------------------------------------------------------------------------

    /**
     * Descending over one key, which sortedOrder cannot express at all.
     *
     * <p>Reversing a stable ascending sort on the host is not the same answer -- it reverses the
     * ties too -- so this checks descending keys with ties keeping their arrival order, which is
     * what a SQL ORDER BY ... DESC means.
     */
    @Test
    public void testSortedOrderMultiDescending() throws TornadoExecutionPlanException {
        final int n = 2048;
        IntArray keys = new IntArray(n);
        for (int i = 0; i < n; i++) {
            keys.set(i, random.nextInt(16));
        }
        IntArray order = new IntArray(n);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys) //
                .libraryTask("order", Cudf::sortedOrderMulti, n, 1, 0b1, keys, order) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, order);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        int previousKey = Integer.MAX_VALUE;
        int previousPosition = -1;
        for (int i = 0; i < n; i++) {
            int position = order.get(i);
            int key = keys.get(position);
            assertTrue("keys are not descending at " + i, key <= previousKey);
            if (key == previousKey) {
                assertTrue("equal keys were reordered; a descending sort is still stable", position > previousPosition);
            }
            previousKey = key;
            previousPosition = position;
        }
    }

    /** Two keys, the first ascending and the second descending -- the ORDER BY a, b DESC shape. */
    @Test
    public void testSortedOrderMultiTwoKeys() throws TornadoExecutionPlanException {
        final int n = 2048;
        IntArray keys = new IntArray(2 * n);
        for (int i = 0; i < n; i++) {
            keys.set(i, random.nextInt(8));          // primary, ascending
            keys.set(n + i, random.nextInt(8));      // secondary, descending
        }
        IntArray order = new IntArray(n);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys) //
                .libraryTask("order", Cudf::sortedOrderMulti, n, 2, 0b10, keys, order) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, order);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        Integer[] hostOrder = new Integer[n];
        for (int i = 0; i < n; i++) {
            hostOrder[i] = i;
        }
        Arrays.sort(hostOrder, Comparator.<Integer> comparingInt(a -> keys.get(a)).thenComparing(Comparator.<Integer> comparingInt(a -> keys.get(n + a)).reversed()));

        for (int i = 0; i < n; i++) {
            assertEquals("two-key ordering differs from a stable host sort at " + i, hostOrder[i].intValue(), order.get(i));
        }
    }

    /** With one ascending key the general form has to be the narrow one, or one of them is wrong. */
    @Test
    public void testSortedOrderMultiAgreesWithSortedOrder() throws TornadoExecutionPlanException {
        final int n = 2048;
        IntArray keys = new IntArray(n);
        for (int i = 0; i < n; i++) {
            keys.set(i, random.nextInt(16));
        }
        IntArray narrow = new IntArray(n);
        IntArray general = new IntArray(n);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys) //
                .libraryTask("narrow", Cudf::sortedOrder, n, keys, narrow) //
                .libraryTask("general", Cudf::sortedOrderMulti, n, 1, 0, keys, general) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, narrow, general);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < n; i++) {
            assertEquals("sortedOrderMulti disagrees with sortedOrder at " + i, narrow.get(i), general.get(i));
        }
    }

    /** Flattens an exception chain, since a schedule-time failure reaches the caller wrapped. */
    private static String causeChain(Throwable t) {
        StringBuilder chain = new StringBuilder();
        for (Throwable current = t; current != null; current = current.getCause()) {
            chain.append(current.getMessage()).append(' ');
        }
        return chain.toString();
    }
}
