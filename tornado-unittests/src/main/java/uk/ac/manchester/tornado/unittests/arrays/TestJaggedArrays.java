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
package uk.ac.manchester.tornado.unittests.arrays;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * TornadoVM's array types are always flat/off-heap (no {@code int[][]} jagged-array type in the
 * kernel type system), so ragged/jagged host data must be represented as a flattened buffer plus
 * a row-offset table (CSR-style) -- this is the standard way variable-length-per-thread data is
 * expressed. This exercises a per-thread loop trip-count that varies with the parallel index,
 * which is a different code shape than the fixed/rectangular-2D loops covered elsewhere.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestJaggedArrays
 * </code>
 */
public class TestJaggedArrays extends TornadoTestBase {

    public static void sumRaggedRows(IntArray flat, IntArray rowOffsets, IntArray rowSums) {
        for (@Parallel int row = 0; row < rowSums.getSize(); row++) {
            int start = rowOffsets.get(row);
            int end = rowOffsets.get(row + 1);
            int sum = 0;
            for (int i = start; i < end; i++) {
                sum += flat.get(i);
            }
            rowSums.set(row, sum);
        }
    }

    @Test
    public void testRaggedRowSums() throws TornadoExecutionPlanException {
        // Jagged source data: rows of different lengths, mirroring a real int[][] with
        // non-uniform row sizes.
        int[][] jagged = new int[][] { { 1, 2, 3 }, { 4 }, { 5, 6 }, {}, { 7, 8, 9, 10, 11 } };

        int numRows = jagged.length;
        int totalElements = 0;
        for (int[] row : jagged) {
            totalElements += row.length;
        }

        IntArray rowOffsets = new IntArray(numRows + 1);
        IntArray flat = new IntArray(totalElements);
        int cursor = 0;
        for (int r = 0; r < numRows; r++) {
            rowOffsets.set(r, cursor);
            for (int value : jagged[r]) {
                flat.set(cursor++, value);
            }
        }
        rowOffsets.set(numRows, cursor);

        IntArray rowSums = new IntArray(numRows);
        IntArray expected = new IntArray(numRows);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, flat, rowOffsets) //
                .task("t0", TestJaggedArrays::sumRaggedRows, flat, rowOffsets, rowSums) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, rowSums);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        sumRaggedRows(flat, rowOffsets, expected);
        for (int r = 0; r < numRows; r++) {
            assertEquals(expected.get(r), rowSums.get(r));
        }
        // Sanity-check against the original jagged source directly (not just the device/host
        // parity check above).
        assertEquals(6, rowSums.get(0));
        assertEquals(4, rowSums.get(1));
        assertEquals(11, rowSums.get(2));
        assertEquals(0, rowSums.get(3));
        assertEquals(45, rowSums.get(4));
    }

    @Test
    public void testRaggedRowSumsRandom() throws TornadoExecutionPlanException {
        Random r = new Random(17);
        int numRows = 64;
        int[][] jagged = new int[numRows][];
        int totalElements = 0;
        for (int i = 0; i < numRows; i++) {
            jagged[i] = new int[r.nextInt(20)]; // row lengths 0..19, deliberately non-uniform
            for (int j = 0; j < jagged[i].length; j++) {
                jagged[i][j] = r.nextInt(100);
            }
            totalElements += jagged[i].length;
        }

        IntArray rowOffsets = new IntArray(numRows + 1);
        IntArray flat = new IntArray(totalElements);
        int cursor = 0;
        for (int row = 0; row < numRows; row++) {
            rowOffsets.set(row, cursor);
            for (int value : jagged[row]) {
                flat.set(cursor++, value);
            }
        }
        rowOffsets.set(numRows, cursor);

        IntArray rowSums = new IntArray(numRows);
        IntArray expected = new IntArray(numRows);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, flat, rowOffsets) //
                .task("t0", TestJaggedArrays::sumRaggedRows, flat, rowOffsets, rowSums) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, rowSums);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        sumRaggedRows(flat, rowOffsets, expected);
        for (int row = 0; row < numRows; row++) {
            assertEquals(expected.get(row), rowSums.get(row));
        }
    }
}
