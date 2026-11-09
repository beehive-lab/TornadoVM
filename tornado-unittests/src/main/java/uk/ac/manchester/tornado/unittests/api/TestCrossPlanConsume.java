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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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
 * A task-graph of one execution plan reads buffers another plan left on the device
 * ({@code consumeFromDevice(producer, ...)}), without a copy through the host.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestCrossPlanConsume
 * </code>
 */
public class TestCrossPlanConsume extends TornadoTestBase {

    private static final int N = 4096;
    private static final int LOCAL = 128;

    public static void produce(KernelContext context, FloatArray a, FloatArray scale, int n) {
        int i = context.globalIdx;
        if (i < n) {
            a.set(i, i * scale.get(0));
        }
    }

    public static void consume(KernelContext context, FloatArray a, FloatArray b, int n) {
        int i = context.globalIdx;
        if (i < n) {
            b.set(i, a.get(i) + 1.0f);
        }
    }

    private static GridScheduler grid(String name) {
        WorkerGrid worker = new WorkerGrid1D(N);
        worker.setLocalWork(LOCAL, 1, 1);
        return new GridScheduler(name + ".t0", worker);
    }

    /** The consumer reads each new value the producer computes, from the producer's buffer. */
    @Test
    public void testConsumeFromAnotherPlan() throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(N);
        FloatArray scale = new FloatArray(1);
        FloatArray b = new FloatArray(N);
        KernelContext context = new KernelContext();

        TaskGraph producer = new TaskGraph("crossProducer") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, scale) //
                .task("t0", TestCrossPlanConsume::produce, context, a, scale, N) //
                .persistOnDevice(a);
        ImmutableTaskGraph producerGraph = producer.snapshot();

        TaskGraph consumer = new TaskGraph("crossConsumer") //
                .consumeFromDevice(producerGraph, a) //
                .task("t0", TestCrossPlanConsume::consume, context, a, b, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        try (TornadoExecutionPlan first = new TornadoExecutionPlan(producerGraph)) {
            first.withGridScheduler(grid("crossProducer"));
            try (TornadoExecutionPlan second = new TornadoExecutionPlan(consumer.snapshot())) {
                second.withGridScheduler(grid("crossConsumer"));
                for (int round = 1; round <= 3; round++) {
                    scale.set(0, round);
                    first.execute();
                    second.execute();
                    for (int i = 0; i < N; i++) {
                        assertEquals(i * round + 1.0f, b.get(i), 1e-3f);
                    }
                }
            }
            // the consumer's plan is closed: the producer still owns its buffer
            scale.set(0, 5);
            first.execute().transferToHost(a);
            for (int i = 0; i < N; i++) {
                assertEquals(i * 5.0f, a.get(i), 1e-3f);
            }
        }
    }

    /** The producer's array was never copied to the host by the consumer. */
    @Test
    public void testNothingGoesThroughTheHost() throws TornadoExecutionPlanException {
        FloatArray a = new FloatArray(N);
        FloatArray scale = new FloatArray(1);
        FloatArray b = new FloatArray(N);
        KernelContext context = new KernelContext();
        scale.set(0, 3);

        TaskGraph producer = new TaskGraph("hostlessProducer") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, scale) //
                .task("t0", TestCrossPlanConsume::produce, context, a, scale, N) //
                .persistOnDevice(a);
        ImmutableTaskGraph producerGraph = producer.snapshot();
        TaskGraph consumer = new TaskGraph("hostlessConsumer") //
                .consumeFromDevice(producerGraph, a) //
                .task("t0", TestCrossPlanConsume::consume, context, a, b, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        try (TornadoExecutionPlan first = new TornadoExecutionPlan(producerGraph); TornadoExecutionPlan second = new TornadoExecutionPlan(consumer.snapshot())) {
            first.withGridScheduler(grid("hostlessProducer"));
            second.withGridScheduler(grid("hostlessConsumer"));
            first.execute();
            second.execute();
            for (int i = 0; i < N; i++) {
                assertEquals(0.0f, a.get(i), 0.0f);
                assertEquals(i * 3.0f + 1.0f, b.get(i), 1e-3f);
            }
        }
    }
}
