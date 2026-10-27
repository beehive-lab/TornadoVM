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
package uk.ac.manchester.tornado.unittests.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Plans dropped without {@code close()}, whose arrays the GC collects while later plans run. On
 * CUDA the host memory of an array transferred on every execution is pinned; the pin of a dropped
 * plan's array outlived the array's memory, and a new array allocated over it was copied while part
 * of its range was still registered: {@code cuMemcpyHtoDAsync} failed with
 * {@code CUDA_ERROR_INVALID_VALUE} (beehive-lab/TornadoVM#1295).
 *
 * <p>
 * How to run:
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.memory.TestDroppedPlans
 * </code>
 */
public class TestDroppedPlans extends TornadoTestBase {

    private static final int ROUNDS = 300;

    public static void scale(KernelContext context, FloatArray input, FloatArray output) {
        int i = context.globalIdx;
        if (i < input.getSize()) {
            output.set(i, input.get(i) * 2f);
        }
    }

    /** A new plan, with new arrays of a new size, every round; the previous ones are dropped and collected. */
    @Test
    public void testDroppedPlansWithPinnedArrays() {
        runRounds(false);
    }

    /** Every other plan is closed, which releases every pin of the device while dropped plans' arrays are collected. */
    @Test
    public void testClosedAndDroppedPlansWithPinnedArrays() {
        runRounds(true);
    }

    private static void runRounds(boolean closeEveryOther) {
        for (int r = 0; r < ROUNDS; r++) {
            int size = 1024 + (r * 37) % 4096;
            FloatArray input = new FloatArray(size);
            FloatArray output = new FloatArray(size);
            input.init(r);
            TaskGraph graph = new TaskGraph("dropped") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                    .task("scale", TestDroppedPlans::scale, new KernelContext(), input, output) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot()); // dropped, unless closed below
            plan.withGridScheduler(new GridScheduler("dropped.scale", new WorkerGrid1D(size))).execute();
            for (int i = 0; i < size; i++) {
                assertEquals(2f * r, output.get(i), "round " + r + ", index " + i);
            }
            if (closeEveryOther && r % 2 == 0) {
                try {
                    plan.close();
                } catch (TornadoExecutionPlanException e) {
                    throw new AssertionError(e);
                }
            }
            if (r % 2 == 1) {
                System.gc();
            }
        }
    }
}
