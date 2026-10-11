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
package uk.ac.manchester.tornado.unittests.reductions;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * A task graph holding a {@code @Reduce} task is rewritten into a generated graph before it runs. The
 * worker grids set for the other tasks of the graph must carry over: a {@code KernelContext} task
 * that loses its grid runs with a default launch and computes the wrong thing.
 *
 * <p>How to run:</p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.reductions.TestReductionWithGridScheduler
 * </code>
 */
public class TestReductionWithGridScheduler extends TornadoTestBase {

    private static final int SIZE = 1 << 16;
    private static final int BLOCK = 256;

    private static void sum(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    /** Sums each block of {@link #BLOCK} elements in local memory: correct only with that local size. */
    private static void blockSum(KernelContext context, FloatArray input, FloatArray partial) {
        float[] tile = context.allocateFloatLocalArray(BLOCK);
        int local = context.localIdx;
        tile[local] = input.get(context.globalIdx);
        for (int stride = context.localGroupSizeX / 2; stride > 0; stride /= 2) {
            context.localBarrier();
            if (local < stride) {
                tile[local] += tile[local + stride];
            }
        }
        if (local == 0) {
            partial.set(context.groupIdx, tile[0]);
        }
    }

    @Test
    public void testGridKeptNextToReduction() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(1.0f);
        FloatArray result = new FloatArray(1);
        FloatArray partial = new FloatArray(SIZE / BLOCK);

        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(BLOCK, 1, 1);
        GridScheduler grid = new GridScheduler("grid.blocks", worker);

        TaskGraph taskGraph = new TaskGraph("grid") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("sum", TestReductionWithGridScheduler::sum, input, result) //
                .task("blocks", TestReductionWithGridScheduler::blockSum, new KernelContext(), input, partial) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result, partial);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        assertEquals(SIZE, result.get(0), 0.0f);
        for (int g = 0; g < partial.getSize(); g++) {
            assertEquals("block " + g, BLOCK, partial.get(g), 0.0f);
        }
    }
}
