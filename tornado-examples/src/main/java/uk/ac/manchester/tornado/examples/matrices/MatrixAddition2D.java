/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.matrices;

import java.util.Random;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat4;
import uk.ac.manchester.tornado.api.types.vectors.Float4;

/**
 * Full example to show to matrix addition with non vector types
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.matrices.MatrixAddition2D
 * </code>
 *
 */
public class MatrixAddition2D {
    // CHECKSTYLE:OFF

    private static final int WARMING_UP_ITERATIONS = 25;

    private static void matrixAddition(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                C.set(i, j, A.get(i, j) + B.get(j, j));
            }
        }
    }

    private static void matrixAddition(Matrix2DFloat4 A, Matrix2DFloat4 B, Matrix2DFloat4 C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                C.set(i, j, Float4.add(A.get(i, j), B.get(j, j)));
            }
        }
    }

    private static void reset() {
        for (int i = 0; i < TornadoRuntimeProvider.getTornadoRuntime().getNumBackends(); i++) {
            final TornadoBackend driver = TornadoRuntimeProvider.getTornadoRuntime().getBackend(i);
            driver.getDefaultDevice().clean();
        }
    }

    public static void main(String[] args) {

        int size = 512;
        if (args.length >= 1) {
            try {
                size = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        System.out.println("Computing Matrix Addition of " + size + "x" + size);

        Matrix2DFloat matrixA = new Matrix2DFloat(size, size);
        Matrix2DFloat matrixB = new Matrix2DFloat(size, size);
        Matrix2DFloat matrixC = new Matrix2DFloat(size, size);
        Matrix2DFloat resultSeq = new Matrix2DFloat(size, size);

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrixA.set(i, j, r.nextFloat());
                matrixB.set(i, j, r.nextFloat());
            }
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixA, matrixB) //
                .task("t0", MatrixAddition2D::matrixAddition, matrixA, matrixB, matrixC, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixC);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);

        // 1. Warm up Tornado
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            executor.execute();
        }

        // 2. Run parallel on the GPU with Tornado
        long start = System.nanoTime();
        executor.execute();
        long end = System.nanoTime();

        reset();

        size /= 2;

        Matrix2DFloat4 matrixAV = new Matrix2DFloat4(size, size);
        Matrix2DFloat4 matrixBV = new Matrix2DFloat4(size, size);
        Matrix2DFloat4 matrixCV = new Matrix2DFloat4(size, size);

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrixAV.set(i, j, new Float4(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()));
                matrixBV.set(i, j, new Float4(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()));
            }
        }

        TaskGraph taskGraph1 = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrixAV, matrixBV) //
                .task("t1", MatrixAddition2D::matrixAddition, matrixAV, matrixBV, matrixCV, (size * 2)) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, matrixCV);

        ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
        TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1);

        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            executor1.execute();
        }

        long startVector = System.nanoTime();
        executor1.execute();
        long endVector = System.nanoTime();

        // Run sequential
        // 1. Warm up sequential
        for (int i = 0; i < WARMING_UP_ITERATIONS; i++) {
            matrixAddition(matrixA, matrixB, resultSeq, size);
        }

        // 2. Run the sequential code
        long startSequential = System.nanoTime();
        matrixAddition(matrixA, matrixB, resultSeq, size);
        long endSequential = System.nanoTime();
        double speedup = (double) (endSequential - startSequential) / (double) (end - start);
        double speedupVector = (double) (endSequential - startSequential) / (double) (endVector - startVector);

        System.out.println("\tCPU Sequential Total Time = " + (endSequential - startSequential) + " ns");
        System.out.println("\tGPU TornadoVM  Total Time = " + (end - start) + " ns");
        System.out.println("\tGPU TornadoVM  Vector Total Time = " + (endVector - startVector) + " ns");
        System.out.println("\tSpeedup Seq/TornadoVM: " + speedup + "x");
        System.out.println("\tSpeedup Seq/TornadoVM Vector: " + speedupVector + "x");
    }

}
// CHECKSTYLE:ON
