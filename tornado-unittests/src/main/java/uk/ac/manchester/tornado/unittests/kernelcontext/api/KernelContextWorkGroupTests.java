/*
 * Copyright (c) 2021, 2022, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.WorkerGrid3D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run.
 * </p>
 * <code>
 * tornado-test --threadInfo --printKernel -V uk.ac.manchester.tornado.unittests.kernelcontext.api.KernelContextWorkGroupTests
 * </code>
 */
public class KernelContextWorkGroupTests extends TornadoTestBase {

    private static void apiTestGlobalGroupSizeX(KernelContext context, IntArray data) {
        data.set(0, context.globalGroupSizeX);
    }

    private static void apiTestGlobalGroupSizeY(KernelContext context, IntArray data) {
        data.set(0, context.globalGroupSizeY);
    }

    private static void apiTestGlobalGroupSizeZ(KernelContext context, IntArray data) {
        data.set(0, context.globalGroupSizeZ);
    }

    private static void apiTestLocalGroupSizeX(KernelContext context, IntArray data) {
        data.set(0, context.localGroupSizeX);
    }

    private static void apiTestLocalGroupSizeY(KernelContext context, IntArray data) {
        data.set(0, context.localGroupSizeY);
    }

    private static void apiTestLocalGroupSizeZ(KernelContext context, IntArray data) {
        data.set(0, context.localGroupSizeZ);
    }

    @Test
    public void test01() throws TornadoExecutionPlanException {
        KernelContext context = new KernelContext();
        GridScheduler grid = new GridScheduler();
        WorkerGrid worker = new WorkerGrid1D(16);
        grid.addWorkerGrid("s0.t0", worker);

        IntArray data = new IntArray(16);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", KernelContextWorkGroupTests::apiTestGlobalGroupSizeX, context, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid) //
                    .execute();
        }

        assertEquals(16, data.get(0));
    }

    @Test
    public void test02() throws TornadoExecutionPlanException {
        KernelContext context = new KernelContext();
        GridScheduler grid = new GridScheduler();
        WorkerGrid worker = new WorkerGrid2D(16, 8);
        grid.addWorkerGrid("s0.t0", worker);

        IntArray data = new IntArray(16);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", KernelContextWorkGroupTests::apiTestGlobalGroupSizeY, context, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid) //
                    .execute();
        }

        assertEquals(8, data.get(0));
    }

    @Test
    public void test03() throws TornadoExecutionPlanException {
        KernelContext context = new KernelContext();
        GridScheduler grid = new GridScheduler();
        WorkerGrid worker = new WorkerGrid3D(16, 8, 4);
        grid.addWorkerGrid("s0.t0", worker);

        IntArray data = new IntArray(16);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", KernelContextWorkGroupTests::apiTestGlobalGroupSizeZ, context, data)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid) //
                    .execute();
        }
        assertEquals(4, data.get(0));
    }

    @Test
    public void test04() throws TornadoExecutionPlanException {
        KernelContext context = new KernelContext();
        GridScheduler grid = new GridScheduler();
        WorkerGrid worker = new WorkerGrid1D(1024);
        worker.setLocalWork(8, 1, 1);
        grid.addWorkerGrid("s0.t0", worker);

        IntArray data = new IntArray(1024);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", KernelContextWorkGroupTests::apiTestLocalGroupSizeX, context, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid) //
                    .execute();
        }
        assertEquals(worker.getLocalWork()[0], data.get(0));
    }

    @Test
    public void test05() throws TornadoExecutionPlanException {
        KernelContext context = new KernelContext();
        GridScheduler grid = new GridScheduler();
        WorkerGrid worker = new WorkerGrid2D(1024, 512);
        worker.setLocalWork(1, 8, 1);
        grid.addWorkerGrid("s0.t0", worker);

        IntArray data = new IntArray(1024);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", KernelContextWorkGroupTests::apiTestLocalGroupSizeY, context, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid) //
                    .execute();
        }
        assertEquals(worker.getLocalWork()[1], data.get(0));
    }

    @Test
    public void test06() throws TornadoExecutionPlanException {
        KernelContext context = new KernelContext();
        GridScheduler grid = new GridScheduler();
        WorkerGrid worker = new WorkerGrid3D(1, 1, 1024);
        worker.setLocalWork(1, 1, 8);
        grid.addWorkerGrid("s0.t0", worker);

        IntArray data = new IntArray(1024);
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", KernelContextWorkGroupTests::apiTestLocalGroupSizeZ, context, data) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(grid) //
                    .execute();
        }
        assertEquals(worker.getLocalWork()[2], data.get(0));
    }
}
