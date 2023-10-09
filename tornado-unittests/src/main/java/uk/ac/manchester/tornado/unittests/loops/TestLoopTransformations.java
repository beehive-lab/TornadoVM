/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.loops.TestLoopTransformations
 * </code>
 */
public class TestLoopTransformations extends TornadoTestBase {
    // CHECKSTYLE:OFF

    private static void matrixVectorMultiplication(final float[] A, final float[] B, final float[] C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            float sum = 0.0f;
            for (int j = 0; j < size; j++) {
                sum += A[(i * size) + j] * B[j];
            }
            C[i] = sum;
        }
    }

    private static void matrixTranspose(final float[] A, float[] B, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                B[(i * size) + j] = A[(j * size) + i];
            }
        }
    }

    @Test
    public void testPartialUnrollDefault() {
        int size = 512;

        float[] matrixA = new float[size * size];
        float[] matrixB = new float[size * size];
        float[] matrixC = new float[size * size];
        float[] resultSeq = new float[size * size];

        Random r = new Random();

        IntStream.range(0, size * size).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
        });

        IntStream.range(0, size).parallel().forEach(idx -> {
            matrixB[idx] = r.nextFloat();
        });

        TornadoRuntime.setProperty("tornado.experimental.partial.unroll", "True");

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", TestLoopTransformations::matrixVectorMultiplication, matrixA, matrixB, matrixC, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        matrixVectorMultiplication(matrixA, matrixB, resultSeq, size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                assertEquals(matrixC[i * size + j], resultSeq[i * size + j], 0.01f);
            }
        }
    }

    @Test
    public void testPartialUnrollNvidia32() {
        int size = 512;

        float[] matrixA = new float[size * size];
        float[] matrixB = new float[size * size];
        float[] matrixC = new float[size * size];
        float[] resultSeq = new float[size * size];

        Random r = new Random();

        IntStream.range(0, size * size).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
        });

        IntStream.range(0, size).parallel().forEach(idx -> {
            matrixB[idx] = r.nextFloat();
        });

        TornadoRuntime.setProperty("tornado.experimental.partial.unroll", "True");

        for (int i = 0; i < TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount(); i++) {
            if (TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(i).getPlatformName().toLowerCase().contains("nvidia")) {
                TornadoDriver driver = TornadoRuntime.getTornadoRuntime().getDriver(0);
                driver.setDefaultDevice(i);
                TornadoRuntime.setProperty("tornado.unroll.factor", "32");
                System.setProperty("tornado.unroll.factor", "32");
            }
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", TestLoopTransformations::matrixVectorMultiplication, matrixA, matrixB, matrixC, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        matrixVectorMultiplication(matrixA, matrixB, resultSeq, size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                assertEquals(matrixC[i * size + j], resultSeq[i * size + j], 0.01f);
            }
        }
    }

    @Test
    public void testPartialUnrollParallelLoops() {
        final int N = 256;
        float[] matrixA = new float[N * N];
        float[] matrixB = new float[N * N];
        float[] resultSeq = new float[N * N];

        TornadoRuntime.setProperty("tornado.experimental.partial.unroll", "True");

        Random r = new Random();
        IntStream.range(0, N * N).parallel().forEach(idx -> {
            matrixA[idx] = r.nextFloat();
            matrixB[idx] = r.nextFloat();
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA) //
                .task("t0", TestLoopTransformations::matrixTranspose, matrixA, matrixB, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixB); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                resultSeq[(i * N) + j] = matrixA[(j * N) + i];
            }
        }

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(resultSeq[i * N + j], matrixB[i * N + j], 0.1);
            }
        }
    }
    // CHECKSTYLE:ON
}
