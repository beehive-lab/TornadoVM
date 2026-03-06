/*
 * Copyright (c) 2021-2022 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.kernelcontext.matrices;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * The unit-tests in this class implement the Matrix Multiplication to check the functional operation of some {@link KernelContext} features, such as global thread identifiers, local thread
 * identifiers, barriers and allocation of local memory.
 * </p>
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationKernelContext
 * </code>
 */
public class TestMatrixMultiplicationKernelContext extends TornadoTestBase {
    // CHECKSTYLE:OFF

    private static final int TS = 4;

    public static void matrixMultiplicationJava(FloatArray a, FloatArray b, FloatArray c, int size) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += a.get(i * size + k) * b.get(k * size + j);
                }
                c.set(i * size + j, sum);
            }
        }
    }

    public static void matrixMultiplication1D(KernelContext context, FloatArray a, FloatArray b, FloatArray c, int size) {
        int idx = context.globalIdx;

        for (int jdx = 0; jdx < size; jdx++) {
            float sum = 0.0f;
            for (int k = 0; k < size; k++) {
                sum += a.get((idx * size) + k) * b.get((k * size) + jdx);
            }
            c.set((idx * size) + jdx, sum);
        }
    }

    public static void matrixMultiplication2D01(KernelContext context, FloatArray a, FloatArray b, FloatArray c, int size) {
        int idx = context.globalIdx;
        int jdx = context.globalIdy;
        float sum = 0.0f;

        for (int k = 0; k < size; k++) {
            sum += a.get((k * size) + idx) * b.get((jdx * size) + k);
        }
        c.set((idx * size) + jdx, sum);
    }

    public static void matrixMultiplication2D02(KernelContext context, final FloatArray A, final FloatArray B, final FloatArray C, final int size) {
        int row = context.localIdx;
        int col = context.localIdy;
        int globalRow = TS * context.groupIdx + row;
        int globalCol = TS * context.groupIdy + col;

        float[] aSub = context.allocateFloatLocalArray(TS * TS);
        float[] bSub = context.allocateFloatLocalArray(TS * TS);

        float sum = 0.0f;

        // Loop over all tiles
        int numTiles = size / TS;
        for (int tileIndex = 0; tileIndex < numTiles; tileIndex++) {

            // Load one tile of A and B into local memory
            int tiledRow = TS * tileIndex + row;
            int tiledCol = TS * tileIndex + col;
            aSub[col * TS + row] = A.get(tiledCol * size + globalRow);
            bSub[col * TS + row] = B.get(globalCol * size + tiledRow);

            // Synchronise to make sure the tile is loaded
            context.localBarrier();

            // Perform the computation for a single tile
            for (int k = 0; k < TS; k++) {
                sum += aSub[k * TS + row] * bSub[col * TS + k];
            }
            // Synchronise before loading the next tile
            context.localBarrier();
        }

        // Store the final result in C
        C.set((globalCol * size) + globalRow, sum);
    }

    @Test
    public void mxm1DKernelContext() throws TornadoExecutionPlanException {
        final int size = 16;
        FloatArray a = new FloatArray(size * size);
        FloatArray b = new FloatArray(size * size);
        FloatArray cJava = new FloatArray(size * size);
        FloatArray cTornado = new FloatArray(size * size);

        Random r = new Random();
        IntStream.range(0, size * size).forEach(i -> {
            a.set(i, r.nextFloat());
            b.set(i, r.nextFloat());
        });

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMatrixMultiplicationKernelContext::matrixMultiplication1D, context, a, b, cTornado, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        matrixMultiplicationJava(a, b, cJava, size);

        for (int i = 0; i < size * size; i++) {
            assertEquals(cJava.get(i), cTornado.get(i), 0.01f);
        }
    }

    @Test
    public void mxm2DKernelContext01() throws TornadoExecutionPlanException {
        final int size = 16;
        FloatArray a = new FloatArray(size * size);
        FloatArray b = new FloatArray(size * size);
        FloatArray cJava = new FloatArray(size * size);
        FloatArray cTornado = new FloatArray(size * size);

        Random r = new Random();
        IntStream.range(0, size * size).forEach(i -> {
            a.set(i, r.nextFloat());
            b.set(i, r.nextFloat());
        });

        WorkerGrid worker = new WorkerGrid2D(size, size);
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.addWorkerGrid("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMatrixMultiplicationKernelContext::matrixMultiplication2D01, context, a, b, cTornado, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        matrixMultiplicationJava(a, b, cJava, size);

        for (int i = 0; i < size * size; i++) {
            assertEquals(cJava.get(i), cTornado.get(i), 0.01f);
        }
    }

    @Test
    public void mxm2DKernelContext02() throws TornadoExecutionPlanException {
        final int size = 16;
        FloatArray a = new FloatArray(size * size);
        FloatArray b = new FloatArray(size * size);
        FloatArray cJava = new FloatArray(size * size);
        FloatArray cTornado = new FloatArray(size * size);

        Random r = new Random();
        IntStream.range(0, size * size).forEach(i -> {
            a.set(i, r.nextFloat());
            b.set(i, r.nextFloat());
        });

        WorkerGrid worker = new WorkerGrid2D(size, size);
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.addWorkerGrid("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestMatrixMultiplicationKernelContext::matrixMultiplication2D02, context, a, b, cTornado, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, cTornado);
        worker.setLocalWork(TS, TS, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        matrixMultiplicationJava(a, b, cJava, size);

        for (int i = 0; i < size * size; i++) {
            assertEquals(cJava.get(i), cTornado.get(i), 0.1f);
        }
    }
    // CHECKSTYLE:ON
}
