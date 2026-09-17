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

import java.util.Arrays;
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
 * cuDF library tasks: sort, grouped aggregation, running sum and inner join.
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

    @Test
    public void testSortPairs() throws TornadoExecutionPlanException {
        final int n = 4096;
        IntArray keys = new IntArray(n);
        DoubleArray values = new DoubleArray(n);
        for (int i = 0; i < n; i++) {
            keys.set(i, random.nextInt(1000));
            values.set(i, random.nextDouble());
        }
        IntArray outKeys = new IntArray(n);
        DoubleArray outValues = new DoubleArray(n);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys, values) //
                .libraryTask("sort", Cudf::sortPairs, n, keys, values, outKeys, outValues) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outKeys, outValues);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        int[] expected = new int[n];
        for (int i = 0; i < n; i++) {
            expected[i] = keys.get(i);
        }
        Arrays.sort(expected);
        for (int i = 0; i < n; i++) {
            assertEquals(expected[i], outKeys.get(i));
        }
    }

    /**
     * The permutation, which is what an ORDER BY needs rather than sorted key/value pairs.
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
                .libraryTask("order", Cudf::sortedOrder, n, keys, 0, order) //
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
}
