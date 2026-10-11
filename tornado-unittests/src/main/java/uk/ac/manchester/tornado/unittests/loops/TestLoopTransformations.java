/*
 * Copyright (c) 2020, 2022, 2024, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.loops;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.loops.TestLoopTransformations
 * </code>
 */
public class TestLoopTransformations extends TornadoTestBase {
    // CHECKSTYLE:OFF

    private static void matrixVectorMultiplication(final FloatArray A, final FloatArray B, final FloatArray C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            float sum = 0.0f;
            for (int j = 0; j < size; j++) {
                sum += A.get((i * size) + j) * B.get(j);
            }
            C.set(i, sum);
        }
    }

    private static void matrixTranspose(final FloatArray A, FloatArray B, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                B.set((i * size) + j, A.get((j * size) + i));
            }
        }
    }

    @Test
    public void testPartialUnrollDefault() throws TornadoExecutionPlanException {
        int size = 512;

        FloatArray matrixA = new FloatArray(size * size);
        FloatArray matrixB = new FloatArray(size * size);
        FloatArray matrixC = new FloatArray(size * size);
        FloatArray resultSeq = new FloatArray(size * size);

        Random r = new Random();

        IntStream.range(0, size * size).parallel().forEach(idx -> {
            matrixA.set(idx, r.nextFloat());
        });

        IntStream.range(0, size).parallel().forEach(idx -> {
            matrixB.set(idx, r.nextFloat());
        });

        TornadoRuntimeProvider.setProperty("tornado.experimental.partial.unroll", "True");

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", TestLoopTransformations::matrixVectorMultiplication, matrixA, matrixB, matrixC, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        matrixVectorMultiplication(matrixA, matrixB, resultSeq, size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                assertEquals(matrixC.get(i * size + j), resultSeq.get(i * size + j), 0.01f);
            }
        }
    }

    @Test
    public void testPartialUnrollNvidia32() throws TornadoExecutionPlanException {
        int size = 512;

        FloatArray matrixA = new FloatArray(size * size);
        FloatArray matrixB = new FloatArray(size * size);
        FloatArray matrixC = new FloatArray(size * size);
        FloatArray resultSeq = new FloatArray(size * size);

        Random r = new Random();

        IntStream.range(0, size * size).parallel().forEach(idx -> {
            matrixA.set(idx, r.nextFloat());
        });

        IntStream.range(0, size).parallel().forEach(idx -> {
            matrixB.set(idx, r.nextFloat());
        });

        TornadoRuntimeProvider.setProperty("tornado.experimental.partial.unroll", "True");

        for (int i = 0; i < TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getNumDevices(); i++) {
            if (TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getDevice(i).getPlatformName().toLowerCase().contains("nvidia")) {
                TornadoBackend driver = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0);
                driver.setDefaultDevice(i);
                TornadoRuntimeProvider.setProperty("tornado.unroll.factor", "32");
                System.setProperty("tornado.unroll.factor", "32");
            }
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", TestLoopTransformations::matrixVectorMultiplication, matrixA, matrixB, matrixC, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        matrixVectorMultiplication(matrixA, matrixB, resultSeq, size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                assertEquals(matrixC.get(i * size + j), resultSeq.get(i * size + j), 0.01f);
            }
        }
    }

    @Test
    public void testPartialUnrollParallelLoops() throws TornadoExecutionPlanException {
        final int N = 256;
        FloatArray matrixA = new FloatArray(N * N);
        FloatArray matrixB = new FloatArray(N * N);
        FloatArray resultSeq = new FloatArray(N * N);

        TornadoRuntimeProvider.setProperty("tornado.experimental.partial.unroll", "True");

        Random r = new Random();
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA.set(idx, r.nextFloat());
            matrixB.set(idx, r.nextFloat());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA) //
                .task("t0", TestLoopTransformations::matrixTranspose, matrixA, matrixB, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixB); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                resultSeq.set((i * N) + j, matrixA.get((j * N) + i));
            }
        }

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(resultSeq.get(i * N + j), matrixB.get(i * N + j), 0.1);
            }
        }
    }
    /**
     * A loop-variant division of a division by loop invariants, {@code (u / a) / b}: the shape loop
     * reassociation used to pick up and fail on ("unhandled node in reassociation"), because the
     * backends' division node reported itself associative. The divisors are loaded from an array,
     * so they are loop invariants rather than constants.
     */
    public static void nestedDivision(KernelContext context, IntArray divisors, IntArray out) {
        int a = divisors.get(0);
        int b = divisors.get(1);
        int acc = 0;
        for (int u = context.groupIdx; u < out.getSize(); u += context.globalGroupSizeX / context.localGroupSizeX) {
            acc += (u / a) / b * context.localGroupSizeX;
        }
        out.set(context.globalIdx, acc + context.localIdx);
    }

    @Test
    public void testNestedDivisionInLoop() throws TornadoExecutionPlanException {
        final int groups = 8;
        final int local = 32;
        final int n = groups * local;
        IntArray divisors = IntArray.fromElements(3, 5);
        IntArray out = new IntArray(n);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, divisors) //
                .task("t0", TestLoopTransformations::nestedDivision, new KernelContext(), divisors, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        WorkerGrid worker = new WorkerGrid1D(n);
        worker.setLocalWork(local, 1, 1);
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(new GridScheduler("s0.t0", worker)).execute();
        }

        for (int i = 0; i < n; i++) {
            int expected = 0;
            for (int u = i / local; u < n; u += groups) {
                expected += (u / 3) / 5 * local;
            }
            assertEquals(expected + i % local, out.get(i));
        }
    }
    // CHECKSTYLE:ON
}
