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
package uk.ac.manchester.tornado.unittests.atomics;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Mixes a {@code @Reduce} reduction task with an {@code AtomicInteger} atomics task in the SAME
 * task graph -- distinct from {@code fails.CodeFail#codeFail03}, which bails out because it fuses
 * TWO {@code @Reduce} outputs into ONE kernel. Here each construct is its own task/kernel, sharing
 * a task graph; nothing else in the suite exercises a reduction and an atomic side-by-side like
 * this.
 *
 * <p>
 * Getting here surfaced a real, non-obvious TornadoVM behavior worth calling out: an
 * {@code AtomicInteger} update whose return value is never consumed (e.g. a bare
 * {@code counter.incrementAndGet();} statement with the result discarded) gets silently dropped
 * from the compiled graph -- the atomic node has no consumer, so `transferToHost` for that atomic
 * never gets wired up, and the whole plan bails out with an opaque
 * "[TornadoVM] Error - Recover option disabled" (confirmed via {@code --debug}: the task-graph
 * dump shows no "copy out" step for the atomic at all). Every working example in
 * {@code TestAtomics} (e.g. {@code atomic04}) already consumes the return value by writing it
 * into an array; {@link #countAboveThreshold} follows that same requirement, consuming the result
 * even though the increment itself is conditional.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.atomics.TestAtomicsWithReduction
 * </code>
 */
public class TestAtomicsWithReduction extends TornadoTestBase {

    private static void sumReduction(IntArray input, @Reduce IntArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    private static void countAboveThreshold(IntArray input, AtomicInteger counter, IntArray marker, int threshold) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            if (input.get(i) > threshold) {
                marker.set(i, counter.incrementAndGet());
            } else {
                marker.set(i, -1);
            }
        }
    }

    @Test
    public void testReductionAndAtomicInSameTaskGraph() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int threshold = 500;

        IntArray input = new IntArray(size);
        for (int i = 0; i < size; i++) {
            input.set(i, i);
        }

        IntArray sum = new IntArray(1);
        AtomicInteger counter = new AtomicInteger(0);
        IntArray marker = new IntArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, counter) //
                .task("t0", TestAtomicsWithReduction::sumReduction, input, sum) //
                .task("t1", TestAtomicsWithReduction::countAboveThreshold, input, counter, marker, threshold) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, sum, counter, marker);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        int expectedSum = 0;
        int expectedCount = 0;
        for (int i = 0; i < size; i++) {
            expectedSum += i;
            if (i > threshold) {
                expectedCount++;
            }
        }

        assertEquals(expectedSum, sum.get(0));
        assertEquals(expectedCount, counter.get());
    }

}
