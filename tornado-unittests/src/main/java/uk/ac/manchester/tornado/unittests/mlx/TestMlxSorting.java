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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.Mlx;

/**
 * Unit tests for the MLX sorting library tasks: sort, argsort, partition and argpartition, over a
 * whole array or along the middle axis of {@code [outer, len, inner]}, and top-k. Each test checks
 * the task against a sequential Java reference. Skipped unless the default device is on the Metal
 * backend and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxSorting
 * </code>
 */
public class TestMlxSorting extends MlxTestBase {

    private static final int SIZE = 1000;
    private static final int OUTER = 7;
    private static final int LEN = 1500;

    /** Slice (o, j) of x viewed as [outer, len, inner]. */
    private static float[] slice(float[] x, int o, int j, int len, int inner) {
        float[] s = new float[len];
        for (int i = 0; i < len; i++) {
            s[i] = x[(o * len + i) * inner + j];
        }
        return s;
    }

    private static float[] sortedJava(float[] x) {
        float[] s = x.clone();
        Arrays.sort(s);
        return s;
    }

    /** x[o, idx[i], j] for the indices of slice (o, j), checking they are a permutation. */
    private static float[] gather(float[] x, IntArray indices, int o, int j, int len, int inner) {
        float[] s = new float[len];
        boolean[] seen = new boolean[len];
        for (int i = 0; i < len; i++) {
            int k = indices.get((o * len + i) * inner + j);
            assertTrue("index " + k + " out of range", k >= 0 && k < len);
            assertTrue("index " + k + " repeated", !seen[k]);
            seen[k] = true;
            s[i] = x[(o * len + k) * inner + j];
        }
        return s;
    }

    /** got holds original's kth-smallest value at kth, smaller values before it and larger after. */
    private static void assertPartitioned(String what, float[] original, float[] got, int kth) {
        float[] sorted = sortedJava(original);
        assertEquals(what + " kth element", sorted[kth], got[kth], 0f);
        for (int i = 0; i < got.length; i++) {
            assertTrue(what + " element " + i + " on the wrong side of kth", i < kth ? got[i] <= got[kth] : got[i] >= got[kth]);
        }
        assertArrayEquals(what + " is not a permutation", sorted, sortedJava(got), 0f);
    }

    /** The k largest values of x[from, to), ascending. */
    private static float[] topkJava(float[] x, int from, int to, int k) {
        float[] sorted = Arrays.copyOfRange(x, from, to);
        Arrays.sort(sorted);
        return Arrays.copyOfRange(sorted, sorted.length - k, sorted.length);
    }

    @Test
    public void testSort() throws TornadoExecutionPlanException {
        float[] xv = values(SIZE, -100, 100, 1);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("sort", Mlx::sort, x, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertArrayEquals(sortedJava(xv), output.toHeapArray(), 0f);
    }

    @Test
    public void testArgsort() throws TornadoExecutionPlanException {
        float[] xv = values(SIZE, -100, 100, 2);
        FloatArray x = FloatArray.fromArray(xv);
        IntArray output = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("argsort", Mlx::argsort, x, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertArrayEquals(sortedJava(xv), gather(xv, output, 0, 0, SIZE, 1), 0f);
    }

    @Test
    public void testPartition() throws TornadoExecutionPlanException {
        final int kth = SIZE / 3;
        float[] xv = values(SIZE, -100, 100, 3);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("partition", Mlx::partition, x, output, kth) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertPartitioned("partition", xv, output.toHeapArray(), kth);
    }

    @Test
    public void testArgpartition() throws TornadoExecutionPlanException {
        final int kth = SIZE / 3;
        float[] xv = values(SIZE, -100, 100, 4);
        FloatArray x = FloatArray.fromArray(xv);
        IntArray output = new IntArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("argpartition", Mlx::argpartition, x, output, kth) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertPartitioned("argpartition", xv, gather(xv, output, 0, 0, SIZE, 1), kth);
    }

    @Test
    public void testSortAxis() throws TornadoExecutionPlanException {
        float[] xv = values(OUTER * LEN, -100, 100, 5);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("sort", Mlx::sortAxis, x, output, OUTER, LEN, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] got = output.toHeapArray();
        for (int o = 0; o < OUTER; o++) {
            assertArrayEquals("row " + o, sortedJava(slice(xv, o, 0, LEN, 1)), slice(got, o, 0, LEN, 1), 0f);
        }
    }

    @Test
    public void testArgsortAxis() throws TornadoExecutionPlanException {
        float[] xv = values(OUTER * LEN, -100, 100, 6);
        FloatArray x = FloatArray.fromArray(xv);
        IntArray output = new IntArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("argsort", Mlx::argsortAxis, x, output, OUTER, LEN, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int o = 0; o < OUTER; o++) {
            assertArrayEquals("row " + o, sortedJava(slice(xv, o, 0, LEN, 1)), gather(xv, output, o, 0, LEN, 1), 0f);
        }
    }

    @Test
    public void testPartitionAxis() throws TornadoExecutionPlanException {
        final int kth = LEN / 3;
        float[] xv = values(OUTER * LEN, -100, 100, 7);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("partition", Mlx::partitionAxis, x, output, OUTER, LEN, 1, kth) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] got = output.toHeapArray();
        for (int o = 0; o < OUTER; o++) {
            assertPartitioned("row " + o, slice(xv, o, 0, LEN, 1), slice(got, o, 0, LEN, 1), kth);
        }
    }

    @Test
    public void testArgpartitionAxis() throws TornadoExecutionPlanException {
        final int kth = LEN / 3;
        float[] xv = values(OUTER * LEN, -100, 100, 8);
        FloatArray x = FloatArray.fromArray(xv);
        IntArray output = new IntArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("argpartition", Mlx::argpartitionAxis, x, output, OUTER, LEN, 1, kth) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int o = 0; o < OUTER; o++) {
            assertPartitioned("row " + o, slice(xv, o, 0, LEN, 1), gather(xv, output, o, 0, LEN, 1), kth);
        }
    }

    @Test
    public void testTopk() throws TornadoExecutionPlanException {
        final int k = 10;
        float[] xv = values(5000, -10, 10, 9);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(k);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("topk", Mlx::topk, x, output, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        // MLX returns the k largest values in no particular order.
        assertArrayEquals(topkJava(xv, 0, xv.length, k), sortedJava(output.toHeapArray()), 0f);
    }

    @Test
    public void testTopkRows() throws TornadoExecutionPlanException {
        final int rows = 4;
        final int cols = 777;
        final int k = 5;
        float[] xv = values(rows * cols, -10, 10, 10);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(rows * k);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("topk", Mlx::topkRows, x, output, rows, cols, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] got = output.toHeapArray();
        for (int r = 0; r < rows; r++) {
            assertArrayEquals("row " + r, topkJava(xv, r * cols, (r + 1) * cols, k), sortedJava(Arrays.copyOfRange(got, r * k, (r + 1) * k)), 0f);
        }
    }

    // ---------------------------------------------------------------- other sizes, layouts and types

    @Test
    public void testSortLargerThanOneBlock() throws TornadoExecutionPlanException {
        final int n = 100_000;
        float[] xv = values(n, -100, 100, 11);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("sort", Mlx::sort, x, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertArrayEquals(sortedJava(xv), output.toHeapArray(), 0f);
    }

    @Test
    public void testSortAxisStrided() throws TornadoExecutionPlanException {
        final int outer = 4;
        final int len = 300;
        final int inner = 9;
        float[] xv = values(outer * len * inner, -100, 100, 12);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("sort", Mlx::sortAxis, x, output, outer, len, inner) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] got = output.toHeapArray();
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                assertArrayEquals("slice " + o + "," + j, sortedJava(slice(xv, o, j, len, inner)), slice(got, o, j, len, inner), 0f);
            }
        }
    }

    @Test
    public void testSortHalf() throws TornadoExecutionPlanException {
        final int n = 777;
        HalfFloatArray x = half(values(n, -10, 10, 13));
        HalfFloatArray output = new HalfFloatArray(n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("sort", Mlx::sort, x, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        float[] expected = sortedJava(widen(x));
        for (int i = 0; i < n; i++) {
            assertEquals("element " + i, expected[i], output.get(i).getFloat32(), 0f);
        }
    }

    @Test
    public void testSortInt() throws TornadoExecutionPlanException {
        final int n = 777;
        int[] xv = new int[n];
        for (int i = 0; i < n; i++) {
            xv[i] = (i * 7919) % 1000 - 500;
        }
        IntArray x = IntArray.fromArray(xv);
        IntArray output = new IntArray(n);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("sort", Mlx::sort, x, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        int[] expected = xv.clone();
        Arrays.sort(expected);
        assertArrayEquals(expected, output.toHeapArray());
    }
}
