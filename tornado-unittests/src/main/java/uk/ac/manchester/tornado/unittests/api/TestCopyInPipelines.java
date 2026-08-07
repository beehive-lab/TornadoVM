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

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@link TornadoExecutionPlan#transferToDevice(Object...)} in the pipelines it exists for: chains of
 * task-graphs that hand data to each other through {@code persistOnDevice} and
 * {@code consumeFromDevice}, where the data a stage needs has to be put on the device at a chosen
 * moment rather than by running something.
 *
 * <p>The pipeline shape here is the one an LLM decode loop uses: a small per-step graph, a run of
 * layer graphs chained on the device, and a final graph that copies one result back.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestCopyInPipelines
 * </code>
 */
public class TestCopyInPipelines extends TornadoTestBase {

    private static final int SIZE = 4096;
    private static final int LAYERS = 4;

    public static void scale(FloatArray input, FloatArray output, float alpha) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, alpha * input.get(i));
        }
    }

    public static void scaleInPlace(FloatArray state, FloatArray weights) {
        for (@Parallel int i = 0; i < state.getSize(); i++) {
            state.set(i, state.get(i) * weights.get(i));
        }
    }

    public static void copy(FloatArray input, FloatArray output) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, input.get(i));
        }
    }

    /**
     * A producer persists its result on the device and a consumer picks it up. The producer's
     * input is a {@code FIRST_EXECUTION} object, so between runs the only way to change what the
     * pipeline computes is to upload it explicitly.
     */
    @Test
    public void testUpdateInputOfAPersistingProducer() throws TornadoExecutionPlanException {
        FloatArray weights = new FloatArray(SIZE);
        weights.init(2.0f);
        FloatArray onDeviceState = new FloatArray(SIZE);
        onDeviceState.init(1.0f);
        FloatArray result = new FloatArray(SIZE);

        TaskGraph producer = new TaskGraph("producer") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights, onDeviceState) //
                .task("produce", TestCopyInPipelines::scaleInPlace, onDeviceState, weights) //
                .persistOnDevice(onDeviceState);

        TaskGraph consumer = new TaskGraph("consumer") //
                .consumeFromDevice("producer", onDeviceState) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, result) //
                .task("consume", TestCopyInPipelines::copy, onDeviceState, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(producer.snapshot(), consumer.snapshot())) {
            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
            for (int i = 0; i < SIZE; i++) {
                assertEquals(2.0f, result.get(i), 0.001f);
            }

            // New weights, uploaded explicitly: the device copy of a FIRST_EXECUTION object would
            // otherwise keep the values it was given the first time.
            weights.init(3.0f);
            plan.transferToDevice(weights);

            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
            for (int i = 0; i < SIZE; i++) {
                assertEquals(6.0f, result.get(i), 0.001f);
            }
        }
    }

    /**
     * The device-resident state of a pipeline is overwritten from the host mid-run - the case an
     * application would otherwise fake by running a task whose only purpose is the transfer.
     */
    @Test
    public void testOverwriteAPersistedBufferFromTheHost() throws TornadoExecutionPlanException {
        FloatArray weights = new FloatArray(SIZE);
        weights.init(2.0f);
        FloatArray onDeviceState = new FloatArray(SIZE);
        onDeviceState.init(1.0f);
        FloatArray result = new FloatArray(SIZE);

        TaskGraph producer = new TaskGraph("statefulProducer") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights, onDeviceState) //
                .task("produce", TestCopyInPipelines::scaleInPlace, onDeviceState, weights) //
                .persistOnDevice(onDeviceState);

        TaskGraph consumer = new TaskGraph("statefulConsumer") //
                .consumeFromDevice("statefulProducer", onDeviceState) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, result) //
                .task("consume", TestCopyInPipelines::copy, onDeviceState, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(producer.snapshot(), consumer.snapshot())) {
            // Two rounds: the state doubles each time, so it holds 4.0 on the device.
            plan.withGraph(0).execute();
            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
            for (int i = 0; i < SIZE; i++) {
                assertEquals(4.0f, result.get(i), 0.001f);
            }

            // Reset the device-resident state from the host and carry on.
            onDeviceState.init(10.0f);
            plan.transferToDevice(onDeviceState);

            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
            for (int i = 0; i < SIZE; i++) {
                assertEquals(20.0f, result.get(i), 0.001f);
            }
        }
    }

    /**
     * The shape of an LLM decode loop: a per-step graph that uploads a small input, a chain of
     * layer graphs that pass their state on the device, and a final graph that reads one result
     * back. The layer weights are placed with an explicit upload instead of a warm-up run.
     */
    @Test
    public void testLayeredPipelineWithUpFrontWeightUpload() throws TornadoExecutionPlanException {
        FloatArray stepInput = new FloatArray(SIZE);
        FloatArray activation = new FloatArray(SIZE);
        FloatArray[] layerWeights = new FloatArray[LAYERS];
        for (int layer = 0; layer < LAYERS; layer++) {
            layerWeights[layer] = new FloatArray(SIZE);
            layerWeights[layer].init(2.0f);
        }
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph[] graphs = new ImmutableTaskGraph[LAYERS + 2];

        graphs[0] = new TaskGraph("step") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, stepInput) //
                .task("activate", TestCopyInPipelines::scale, stepInput, activation, 1.0f) //
                .persistOnDevice(activation) //
                .snapshot();

        String producerName = "step";
        for (int layer = 0; layer < LAYERS; layer++) {
            String name = "layer" + layer;
            graphs[1 + layer] = new TaskGraph(name) //
                    .consumeFromDevice(producerName, activation) //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, layerWeights[layer]) //
                    .task("apply", TestCopyInPipelines::scaleInPlace, activation, layerWeights[layer]) //
                    .persistOnDevice(activation) //
                    .snapshot();
            producerName = name;
        }

        graphs[LAYERS + 1] = new TaskGraph("readout") //
                .consumeFromDevice(producerName, activation) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .task("readout", TestCopyInPipelines::copy, activation, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output) //
                .snapshot();

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graphs)) {
            // What an application would otherwise obtain by running the whole pipeline once on
            // dummy data: the weights are on the device before anything runs.
            plan.transferToDevice();

            for (int step = 1; step <= 3; step++) {
                stepInput.init(step);
                for (int graph = 0; graph < graphs.length; graph++) {
                    plan.withGraph(graph).execute();
                }
                float expected = step * (float) Math.pow(2.0, LAYERS);
                for (int i = 0; i < SIZE; i++) {
                    assertEquals(expected, output.get(i), 0.001f);
                }
            }

            // Swap one layer's weights mid-run, the way a model would swap an adapter.
            layerWeights[0].init(4.0f);
            plan.transferToDevice(layerWeights[0]);

            stepInput.init(1.0f);
            for (int graph = 0; graph < graphs.length; graph++) {
                plan.withGraph(graph).execute();
            }
            float expected = 4.0f * (float) Math.pow(2.0, LAYERS - 1);
            for (int i = 0; i < SIZE; i++) {
                assertEquals(expected, output.get(i), 0.001f);
            }
        }
    }

    /** The same pipeline with the plan routing its operations over several streams. */
    @Test
    public void testPipelineWithIntraPlanConcurrency() throws TornadoExecutionPlanException {
        FloatArray weights = new FloatArray(SIZE);
        weights.init(2.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray intermediate = new FloatArray(SIZE);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph producer = new TaskGraph("concurrentProducer") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("produce", TestCopyInPipelines::scale, input, intermediate, 1.0f) //
                .task("weight", TestCopyInPipelines::scaleInPlace, intermediate, weights) //
                .persistOnDevice(intermediate);

        TaskGraph consumer = new TaskGraph("concurrentConsumer") //
                .consumeFromDevice("concurrentProducer", intermediate) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .task("consume", TestCopyInPipelines::copy, intermediate, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(producer.snapshot(), consumer.snapshot())) {
            plan.withIntraPlanConcurrency();
            plan.transferToDevice();

            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
            for (int i = 0; i < SIZE; i++) {
                assertEquals(6.0f, output.get(i), 0.001f);
            }

            weights.init(5.0f);
            plan.transferToDevice(weights);

            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
            for (int i = 0; i < SIZE; i++) {
                assertEquals(15.0f, output.get(i), 0.001f);
            }
        }
    }

    /** An upload between two executions of the same graph is ordered with respect to both. */
    @Test
    public void testUploadBetweenExecutionsIsOrdered() throws TornadoExecutionPlanException {
        FloatArray weights = new FloatArray(SIZE);
        weights.init(2.0f);
        FloatArray input = new FloatArray(SIZE);
        input.init(1.0f);
        FloatArray output = new FloatArray(SIZE);

        TaskGraph taskGraph = new TaskGraph("ordering") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t", TestCopyInPipelines::scale, input, output, 1.0f) //
                .task("w", TestCopyInPipelines::scaleInPlace, output, weights) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int iteration = 1; iteration <= 16; iteration++) {
                weights.init(iteration);
                plan.transferToDevice(weights);
                plan.execute();
                for (int i = 0; i < SIZE; i++) {
                    assertEquals(iteration, output.get(i), 0.001f);
                }
            }
        }
    }
}
