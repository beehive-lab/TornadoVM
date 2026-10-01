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
package uk.ac.manchester.tornado.unittests.api;

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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Closing a multi-graph plan whose graphs were run one at a time ({@code withGraph(i)}) must free the
 * device buffers of every graph, including a buffer that one graph both uploads and persists and the
 * next one consumes.
 *
 * <p>
 * How to run?
 * </p>
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestFreeSelectedGraphs
 * </code>
 * </p>
 */
public class TestFreeSelectedGraphs extends TornadoTestBase {

    /** 128 MB per array: two arrays per plan stay on the device if only the last selected graph is freed. */
    private static final int SIZE = 32 * 1024 * 1024;

    /** Enough plans to exceed the default 4 GB device heap many times over when buffers are not freed. */
    private static final int PLANS = 40;

    private static final int LOCAL = 256;

    private static void scale(KernelContext context, FloatArray a, FloatArray b) {
        int i = context.globalIdx;
        if (i < a.getSize()) {
            b.set(i, a.get(i) * 2.0f);
        }
    }

    private static void addOne(KernelContext context, FloatArray b, FloatArray c) {
        int i = context.globalIdx;
        if (i < b.getSize()) {
            c.set(i, b.get(i) + 1.0f);
        }
    }

    private static TornadoExecutionPlan producerConsumerPlan(FloatArray a, FloatArray b, FloatArray c) {
        KernelContext context = new KernelContext();
        TaskGraph producer = new TaskGraph("producer") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("scale", TestFreeSelectedGraphs::scale, context, a, b) //
                .persistOnDevice(b);
        TaskGraph consumer = new TaskGraph("consumer") //
                .consumeFromDevice("producer", b) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, c) //
                .task("add", TestFreeSelectedGraphs::addOne, context, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        ImmutableTaskGraph[] graphs = { producer.snapshot(), consumer.snapshot() };
        return new TornadoExecutionPlan(graphs);
    }

    private static GridScheduler grid() {
        WorkerGrid scaleGrid = new WorkerGrid1D(SIZE);
        scaleGrid.setLocalWork(LOCAL, 1, 1);
        WorkerGrid addGrid = new WorkerGrid1D(SIZE);
        addGrid.setLocalWork(LOCAL, 1, 1);
        GridScheduler grid = new GridScheduler();
        grid.addWorkerGrid("producer.scale", scaleGrid);
        grid.addWorkerGrid("consumer.add", addGrid);
        return grid;
    }

    private static FloatArray[] arrays() {
        FloatArray a = new FloatArray(SIZE);
        FloatArray b = new FloatArray(SIZE);
        FloatArray c = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            a.set(i, i % 1024);
        }
        b.init(0.0f);
        c.init(0.0f);
        return new FloatArray[] { a, b, c };
    }

    private static void check(FloatArray a, FloatArray c) {
        for (int i = 0; i < SIZE; i += 4099) {
            assertEquals(a.get(i) * 2.0f + 1.0f, c.get(i), 0.0f);
        }
    }

    /**
     * Build, run graph by graph and close many plans: each close must give back both graphs' buffers,
     * or the device heap runs out after a few plans.
     */
    @Test
    public void testCloseFreesEveryGraph() throws TornadoExecutionPlanException {
        FloatArray[] arrays = arrays();
        GridScheduler grid = grid();
        for (int p = 0; p < PLANS; p++) {
            arrays[2].init(0.0f);
            try (TornadoExecutionPlan plan = producerConsumerPlan(arrays[0], arrays[1], arrays[2])) {
                plan.withGraph(0).withGridScheduler(grid).execute();
                plan.withGraph(1).withGridScheduler(grid).execute();
            }
            check(arrays[0], arrays[2]);
        }
    }

    /**
     * Freeing a plan's device memory twice (explicitly, then on close) must be a no-op the second time.
     */
    @Test
    public void testFreeTwice() throws TornadoExecutionPlanException {
        FloatArray[] arrays = arrays();
        GridScheduler grid = grid();
        try (TornadoExecutionPlan plan = producerConsumerPlan(arrays[0], arrays[1], arrays[2])) {
            plan.withGraph(0).withGridScheduler(grid).execute();
            plan.withGraph(1).withGridScheduler(grid).execute();
            plan.freeDeviceMemory();
        }
        check(arrays[0], arrays[2]);
    }
}
