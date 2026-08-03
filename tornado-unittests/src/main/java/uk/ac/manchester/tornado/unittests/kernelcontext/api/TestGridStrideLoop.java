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
package uk.ac.manchester.tornado.unittests.kernelcontext.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The grid-stride / persistent-thread loop is one of the most idiomatic CUDA patterns (used
 * whenever data is larger than the launched grid): {@code for (i = globalIdx; i < N; i +=
 * globalGroupSizeX)}. Nothing in the suite launches fewer threads than elements and strides a
 * KernelContext-driven loop across the remainder -- the only similar-looking {@code += WARP_SIZE}
 * loops in the MMA tests are intra-warp lane strides, not this persistent-thread idiom.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestGridStrideLoop
 * </code>
 */
public class TestGridStrideLoop extends TornadoTestBase {

    private static final int LAUNCHED_THREADS = 128;
    private static final int DATA_SIZE = LAUNCHED_THREADS * 17 + 5; // deliberately not a clean multiple

    private static void gridStrideDouble(KernelContext ctx, IntArray in, IntArray out) {
        for (int i = ctx.globalIdx; i < out.getSize(); i += ctx.globalGroupSizeX) {
            out.set(i, in.get(i) * 2);
        }
    }

    @Test
    public void testGridStrideLoopCoversDataLargerThanGrid() throws TornadoExecutionPlanException {
        IntArray in = new IntArray(DATA_SIZE);
        for (int i = 0; i < DATA_SIZE; i++) {
            in.set(i, i);
        }
        IntArray out = new IntArray(DATA_SIZE);
        out.init(-1); // sentinel: every element must be overwritten by some thread's stride

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(LAUNCHED_THREADS);
        GridScheduler grid = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestGridStrideLoop::gridStrideDouble, context, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid).execute();
        }

        for (int i = 0; i < DATA_SIZE; i++) {
            assertEquals("element " + i + " should have been visited by a strided thread", i * 2, out.get(i));
        }
    }

}
