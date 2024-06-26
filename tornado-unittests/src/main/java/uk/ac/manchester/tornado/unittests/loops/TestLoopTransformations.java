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

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
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
    // CHECKSTYLE:ON
}
