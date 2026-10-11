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
 * A task-graph that consumes some objects from a named producer ({@code consumeFromDevice(name, ...)})
 * and others from the previously executed graph ({@code consumeFromDevice(...)}). The unnamed ones
 * must still be taken from the previous graph: naming the producer of one object does not change
 * where the others come from.
 *
 * <p>The shape is a layer of an inference plan: its weights come from a graph that prepares them
 * once (here, one that scales them in place), and its activation from the graph that ran just
 * before it.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestConsumeNamedAndUnnamed
 * </code>
 */
public class TestConsumeNamedAndUnnamed extends TornadoTestBase {

    private static final int SIZE = 4096;
    private static final int LOCAL = 128;

    public static void scaleInPlace(KernelContext context, FloatArray data, float alpha, int size) {
        int i = context.globalIdx;
        if (i < size) {
            data.set(i, data.get(i) * alpha);
        }
    }

    public static void addInPlace(KernelContext context, FloatArray data, float value, int size) {
        int i = context.globalIdx;
        if (i < size) {
            data.set(i, data.get(i) + value);
        }
    }

    public static void multiply(KernelContext context, FloatArray x, FloatArray w, FloatArray out, int size) {
        int i = context.globalIdx;
        if (i < size) {
            out.set(i, x.get(i) * w.get(i));
        }
    }

    private static WorkerGrid grid() {
        WorkerGrid grid = new WorkerGrid1D(SIZE);
        grid.setLocalWork(LOCAL, 1, 1);
        return grid;
    }

    private static GridScheduler scheduler() {
        GridScheduler scheduler = new GridScheduler();
        scheduler.addWorkerGrid("weights.prepare", grid());
        scheduler.addWorkerGrid("activation.update", grid());
        scheduler.addWorkerGrid("layer.multiply", grid());
        return scheduler;
    }

    /** Weights prepared once by their own graph: 2 * 2 = 4 on the device. */
    private static TaskGraph weightsGraph(FloatArray w) {
        return new TaskGraph("weights") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, w) //
                .task("prepare", TestConsumeNamedAndUnnamed::scaleInPlace, new KernelContext(), w, 2.0f, SIZE) //
                .persistOnDevice(w);
    }

    /** The activation, updated on every run: 1 + 1 = 2 on the device after the first one. */
    private static TaskGraph activationGraph(FloatArray x) {
        return new TaskGraph("activation") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .task("update", TestConsumeNamedAndUnnamed::addInPlace, new KernelContext(), x, 1.0f, SIZE) //
                .persistOnDevice(x);
    }

    /** The weights by name, the activation from the graph that ran before. */
    private static TaskGraph layerGraph(FloatArray x, FloatArray w, FloatArray out) {
        return new TaskGraph("layer") //
                .consumeFromDevice(x) //
                .consumeFromDevice("weights", w) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, out) //
                .task("multiply", TestConsumeNamedAndUnnamed::multiply, new KernelContext(), x, w, out, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
    }

    private static void assertAll(FloatArray out, float expected) {
        for (int i = 0; i < SIZE; i++) {
            assertEquals(expected, out.get(i), 0.001f);
        }
    }

    /** Plan order weights, activation, layer: the named producer runs first. */
    @Test
    public void testNamedProducerBeforeTheConsumer() throws TornadoExecutionPlanException {
        FloatArray w = new FloatArray(SIZE);
        FloatArray x = new FloatArray(SIZE);
        FloatArray out = new FloatArray(SIZE);
        w.init(2.0f);
        x.init(1.0f);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(weightsGraph(w).snapshot(), activationGraph(x).snapshot(), layerGraph(x, w, out).snapshot())) {
            plan.withGridScheduler(scheduler());
            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
            plan.withGraph(2).execute();
            assertAll(out, 2.0f * 4.0f);

            // The activation moves on; the weights stay prepared.
            plan.withGraph(1).execute();
            plan.withGraph(2).execute();
            assertAll(out, 3.0f * 4.0f);
        }
    }

    /**
     * Plan order activation, layer, weights: the named producer is the last graph of the plan, as
     * a graph appended after the layers to prepare their weights would be, and runs before them.
     */
    @Test
    public void testNamedProducerAppendedAfterTheConsumer() throws TornadoExecutionPlanException {
        FloatArray w = new FloatArray(SIZE);
        FloatArray x = new FloatArray(SIZE);
        FloatArray out = new FloatArray(SIZE);
        w.init(2.0f);
        x.init(1.0f);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(activationGraph(x).snapshot(), layerGraph(x, w, out).snapshot(), weightsGraph(w).snapshot())) {
            plan.withGridScheduler(scheduler());
            plan.withGraph(2).execute();
            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
            assertAll(out, 2.0f * 4.0f);

            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
            assertAll(out, 3.0f * 4.0f);
        }
    }
}
