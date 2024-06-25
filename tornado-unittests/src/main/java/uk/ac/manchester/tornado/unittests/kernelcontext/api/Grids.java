/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to run?
 *
 * <p>
 * <code>
 * tornado-test --threadInfo --printKernel uk.ac.manchester.tornado.unittests.kernelcontext.api.Grids
 * </code>
 * </p>
 */
public class Grids extends TornadoTestBase {

    final int gridSize = 32;
    final int size = 100000;

    private static void psKernel(KernelContext kc, FloatArray tArray, FloatArray vArray, FloatArray results) {
        int idx = kc.localIdx;

        results.set(5 * idx, kc.globalIdx);
        results.set(5 * idx + 1, kc.globalGroupSizeX);
        results.set(5 * idx + 2, kc.localGroupSizeX);
        results.set(5 * idx + 3, kc.localIdx);
        results.set(5 * idx + 4, 9999);
    }

    @Test
    public void testWithCorrectNames() throws TornadoExecutionPlanException {

        FloatArray timesArray = new FloatArray(size);
        FloatArray obsArray = new FloatArray(size);

        FloatArray resArray = new FloatArray(100000);
        resArray.init(0.0f);

        WorkerGrid worker = new WorkerGrid1D(gridSize);
        GridScheduler gridScheduler = new GridScheduler("foo.bar", worker);
        KernelContext context = new KernelContext();
        worker.setLocalWork(gridSize, 1, 1);

        TaskGraph taskGraph = new TaskGraph("foo");

        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, timesArray, obsArray) //
                .task("bar", Grids::psKernel, context, timesArray, obsArray, resArray) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, resArray);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

    }

    @Test(expected = TornadoRuntimeException.class)
    public void testWithIncorrectGraphName() {
        FloatArray timesArray = new FloatArray(size);
        FloatArray obsArray = new FloatArray(size);

        FloatArray resArray = new FloatArray(100000);
        resArray.init(0.0f);

        WorkerGrid worker = new WorkerGrid1D(gridSize);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();
        worker.setLocalWork(gridSize, 1, 1);

        TaskGraph taskGraph = new TaskGraph("foo");

        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, timesArray, obsArray) //
                .task("t0", Grids::psKernel, context, timesArray, obsArray, resArray) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, resArray);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

    }

    @Test(expected = TornadoRuntimeException.class)
    public void testWithIncorrectGraphAndTaskName() {
        FloatArray timesArray = new FloatArray(size);
        FloatArray obsArray = new FloatArray(size);

        FloatArray resArray = new FloatArray(100000);
        resArray.init(0.0f);

        WorkerGrid worker = new WorkerGrid1D(gridSize);
        GridScheduler gridScheduler = new GridScheduler("foo.bar", worker);
        KernelContext context = new KernelContext();
        worker.setLocalWork(gridSize, 1, 1);

        TaskGraph taskGraph = new TaskGraph("t0");

        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, timesArray, obsArray) //
                .task("t0", Grids::psKernel, context, timesArray, obsArray, resArray) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, resArray);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

    }
}
