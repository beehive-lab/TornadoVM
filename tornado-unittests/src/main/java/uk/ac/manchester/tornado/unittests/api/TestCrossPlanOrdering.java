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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Ordering of device work between execution plans that share buffers through
 * {@code consumeFromDevice(producer, ...)}. The kernels are long enough that a consumer started
 * before its producer finished, or a producer started while its consumer still reads, gives wrong
 * results.
 *
 * <p>
 * Run it also with {@code -Dtornado.vmflush=false}: {@code execute()} then returns before the
 * device has finished, so only the ordering between the plans' queues keeps the results right.
 * </p>
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestCrossPlanOrdering
 * tornado-test -V --jvm="-Dtornado.vmflush=false" uk.ac.manchester.tornado.unittests.api.TestCrossPlanOrdering
 * </code>
 */
public class TestCrossPlanOrdering extends TornadoTestBase {

    private static final int N = 8192;
    private static final int LOCAL = 128;
    private static final int ITERATIONS = 20_000;

    /** a[i] = i + iterations * scale, after a long loop. */
    public static void produce(KernelContext context, FloatArray a, FloatArray scale, int n, int iterations) {
        int i = context.globalIdx;
        if (i < n) {
            float spin = 0.0f;
            for (int k = 0; k < iterations; k++) {
                spin += 1.0e-7f;
            }
            a.set(i, i + scale.get(0) * iterations + (spin - spin));
        }
    }

    /**
     * b[i] = a[i] read before a long loop + a[i] read after it. Equal to 2 * a[i] only if nobody
     * wrote a meanwhile.
     */
    public static void consume(KernelContext context, FloatArray a, FloatArray b, int n, int iterations) {
        int i = context.globalIdx;
        if (i < n) {
            float first = a.get(i);
            float spin = 0.0f;
            for (int k = 0; k < iterations; k++) {
                spin += 1.0e-7f;
            }
            float last = a.get(i);
            b.set(i, first + last + (spin - spin));
        }
    }

    private static GridScheduler grid(String name) {
        WorkerGrid worker = new WorkerGrid1D(N);
        worker.setLocalWork(LOCAL, 1, 1);
        return new GridScheduler(name + ".t0", worker);
    }

    private static float expected(int i, int scale) {
        return 2.0f * (i + (float) ITERATIONS * scale);
    }

    private static void assertRound(FloatArray b, int scale) {
        for (int i = 0; i < N; i++) {
            assertEquals(expected(i, scale), b.get(i), 1e-1f, "index " + i + ", scale " + scale);
        }
    }

    /** The consumer reads what the producer wrote in the same round, never the round before. */
    @Test
    public void testReadAfterWrite() throws Exception {
        FloatArray a = new FloatArray(N);
        FloatArray scale = new FloatArray(1);
        FloatArray b = new FloatArray(N);
        KernelContext context = new KernelContext();

        ImmutableTaskGraph producer = new TaskGraph("rawProducer") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, scale) //
                .task("t0", TestCrossPlanOrdering::produce, context, a, scale, N, ITERATIONS) //
                .persistOnDevice(a) //
                .snapshot();
        ImmutableTaskGraph consumer = new TaskGraph("rawConsumer") //
                .consumeFromDevice(producer, a) //
                .task("t0", TestCrossPlanOrdering::consume, context, a, b, N, ITERATIONS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b) //
                .snapshot();

        try (TornadoExecutionPlan first = new TornadoExecutionPlan(producer); TornadoExecutionPlan second = new TornadoExecutionPlan(consumer)) {
            first.withGridScheduler(grid("rawProducer"));
            second.withGridScheduler(grid("rawConsumer"));
            for (int round = 1; round <= 5; round++) {
                scale.set(0, round);
                first.execute();
                second.execute();
                assertRound(b, round);
            }
        }
    }

    /** The producer's next write waits until the consumer has finished reading the previous one. */
    @Test
    public void testWriteAfterRead() throws Exception {
        FloatArray a = new FloatArray(N);
        FloatArray scale = new FloatArray(1);
        FloatArray b = new FloatArray(N);
        KernelContext context = new KernelContext();

        ImmutableTaskGraph producer = new TaskGraph("warProducer") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, scale) //
                .task("t0", TestCrossPlanOrdering::produce, context, a, scale, N, ITERATIONS) //
                .persistOnDevice(a) //
                .snapshot();
        ImmutableTaskGraph consumer = new TaskGraph("warConsumer") //
                .consumeFromDevice(producer, a) //
                .task("t0", TestCrossPlanOrdering::consume, context, a, b, N, ITERATIONS) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, b) //
                .snapshot();

        try (TornadoExecutionPlan first = new TornadoExecutionPlan(producer); TornadoExecutionPlan second = new TornadoExecutionPlan(consumer)) {
            first.withGridScheduler(grid("warProducer"));
            second.withGridScheduler(grid("warConsumer"));
            for (int round = 1; round <= 5; round++) {
                scale.set(0, round);
                first.execute();
                TornadoExecutionResult read = second.execute();
                // overwrite a while the consumer may still be reading it
                scale.set(0, round + 100);
                first.execute();
                read.transferToHost(b);
                assertRound(b, round);
            }
        }
    }

    /** Producer and consumer run on two host threads, each with its own device queue. */
    @Test
    public void testTwoThreads() throws Exception {
        FloatArray a = new FloatArray(N);
        FloatArray scale = new FloatArray(1);
        FloatArray b = new FloatArray(N);
        KernelContext context = new KernelContext();
        final int rounds = 10;

        ImmutableTaskGraph producer = new TaskGraph("threadProducer") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, scale) //
                .task("t0", TestCrossPlanOrdering::produce, context, a, scale, N, ITERATIONS) //
                .persistOnDevice(a) //
                .snapshot();
        ImmutableTaskGraph consumer = new TaskGraph("threadConsumer") //
                .consumeFromDevice(producer, a) //
                .task("t0", TestCrossPlanOrdering::consume, context, a, b, N, ITERATIONS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b) //
                .snapshot();

        Semaphore produced = new Semaphore(0);
        Semaphore consumed = new Semaphore(0);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try (TornadoExecutionPlan first = new TornadoExecutionPlan(producer); TornadoExecutionPlan second = new TornadoExecutionPlan(consumer)) {
            first.withGridScheduler(grid("threadProducer"));
            second.withGridScheduler(grid("threadConsumer"));
            Future<?> producing = threads.submit(() -> {
                for (int round = 1; round <= rounds; round++) {
                    scale.set(0, round);
                    first.execute();
                    // execute() has only enqueued the work: the consumer's queue has to wait for it
                    produced.release();
                    consumed.acquire();
                }
                return null;
            });
            Future<?> consuming = threads.submit(() -> {
                for (int round = 1; round <= rounds; round++) {
                    produced.acquire();
                    try {
                        second.execute();
                        assertRound(b, round);
                    } finally {
                        consumed.release();
                    }
                }
                return null;
            });
            consuming.get(2, TimeUnit.MINUTES);
            producing.get(2, TimeUnit.MINUTES);
        } finally {
            threads.shutdownNow();
        }
    }

    /** A consumer that runs before its producer has a buffer to bind fails instead of reading garbage. */
    @Test
    public void testConsumerBeforeProducerFails() throws Exception {
        FloatArray a = new FloatArray(N);
        FloatArray scale = new FloatArray(1);
        FloatArray b = new FloatArray(N);
        KernelContext context = new KernelContext();

        ImmutableTaskGraph producer = new TaskGraph("earlyProducer") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, scale) //
                .task("t0", TestCrossPlanOrdering::produce, context, a, scale, N, 1) //
                .persistOnDevice(a) //
                .snapshot();
        ImmutableTaskGraph consumer = new TaskGraph("earlyConsumer") //
                .consumeFromDevice(producer, a) //
                .task("t0", TestCrossPlanOrdering::consume, context, a, b, N, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b) //
                .snapshot();

        try (TornadoExecutionPlan first = new TornadoExecutionPlan(producer); TornadoExecutionPlan second = new TornadoExecutionPlan(consumer)) {
            first.withGridScheduler(grid("earlyProducer"));
            second.withGridScheduler(grid("earlyConsumer"));
            assertThrows(RuntimeException.class, second::execute);
        }
    }
}
