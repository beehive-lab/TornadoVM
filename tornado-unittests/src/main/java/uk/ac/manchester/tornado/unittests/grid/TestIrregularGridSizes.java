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
package uk.ac.manchester.tornado.unittests.grid;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Loop bounds with no divisor of at least a warp -- primes here -- used to get blocks of one thread
 * from the default scheduler, which leaves 31 lanes of every warp idle. Such launches now use full
 * blocks with the grid rounded up; these tests check that the extra threads change nothing: every
 * element is written exactly once, nothing past the end is touched, and a reduction over a prime
 * number of elements is exact.
 *
 * <p>
 * How to run?
 * </p>
 *
 * <code>
 * $ tornado-test -V uk.ac.manchester.tornado.unittests.grid.TestIrregularGridSizes
 * </code>
 */
public class TestIrregularGridSizes extends TornadoTestBase {

    // Primes: no divisor besides 1 and themselves, so no block size that divides them exactly.
    private static final int[] PRIMES = { 257, 65_537, 999_983, 15_485_863 };

    private static void incrementWithin(IntArray counts, int n) {
        for (@Parallel int i = 0; i < n; i++) {
            counts.set(i, counts.get(i) + 1);
        }
    }

    private static void sum(IntArray input, @Reduce IntArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    /** Each of n elements is incremented once; the guard elements past n stay untouched. */
    @Test
    public void testEveryElementOnceAtPrimeSizes() throws TornadoExecutionPlanException {
        for (int n : PRIMES) {
            int guard = 1024;
            IntArray counts = new IntArray(n + guard);
            counts.init(0);
            TaskGraph graph = new TaskGraph("s" + n) //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, counts) //
                    .task("t", TestIrregularGridSizes::incrementWithin, counts, n) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, counts);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
                plan.execute();
            }
            for (int i = 0; i < n + guard; i++) {
                assertEquals("n=" + n + " element " + i, i < n ? 1 : 0, counts.get(i));
            }
        }
    }

    /** A reduction over a prime number of elements is exact. */
    @Test
    public void testReductionAtPrimeSize() throws TornadoExecutionPlanException {
        int n = 999_983;
        IntArray input = new IntArray(n);
        int expected = 0;
        for (int i = 0; i < n; i++) {
            input.set(i, (i % 1000) - 500);
            expected += (i % 1000) - 500;
        }
        IntArray result = new IntArray(1);
        TaskGraph graph = new TaskGraph("r") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t", TestIrregularGridSizes::sum, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        assertEquals(expected, result.get(0));
    }
}
