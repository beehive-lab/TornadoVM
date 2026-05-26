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
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Regression test for an LIR-generation crash when a {@link HalfFloat} value
 * is read back from a kernel-local {@code HalfFloat[]} (allocated via
 * {@link KernelContext#allocateHalfFloatLocalArray(int)}) and converted to
 * {@code float} via {@link HalfFloat#getFloat32()}.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestHalfFloatLocalArray
 * </code>
 */
public class TestHalfFloatLocalArray extends TornadoTestBase {

    public static void halfLocalArrayToFloat(KernelContext context, FloatArray out, int size) {
        HalfFloat[] local = context.allocateHalfFloatLocalArray(size);
        if (context.localIdx == 0) {
            local[0] = new HalfFloat(1.5f);
        }
        context.localBarrier();
        if (context.localIdx == 0) {
            out.set(0, local[0].getFloat32());
        }
    }

    @Test
    public void testHalfFloatLocalArrayToFloat() throws TornadoExecutionPlanException {
        KernelContext context = new KernelContext();
        int size = 16;
        FloatArray out = new FloatArray(size);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        worker.setLocalWork(size, 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, context, out) //
                .task("t0", TestHalfFloatLocalArray::halfLocalArrayToFloat, context, out, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .withPreCompilation() //
                    .execute();
        }

        assertEquals(1.5f, out.get(0), 0.0f);
    }
}
