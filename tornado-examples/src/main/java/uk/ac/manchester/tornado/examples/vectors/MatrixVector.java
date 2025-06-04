/*
 * Copyright (c) 2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.vectors;

import java.util.ArrayList;
import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat4;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.examples.utils.Utils;

/**
 * This test sets the device index to 2. To change the device index.
 *
 * <p>
 * How to run?
 * </p>
 * Run with the vector types:
 * <code>
 * tornado --threadInfo --enableProfiler silent -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.MatrixVector vector
 * </code>
 *
 * Run with no vector types:
 * <code>
 * tornado --threadInfo --enableProfiler silent -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.MatrixVector plain
 * </code>
 *
 * Run with Java Streams:
 * <code>
 * tornado --threadInfo --enableProfiler silent -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.MatrixVector stream
 * </code>
 *
 */
public class MatrixVector {

    public static final int WARMUP = 100;
    public static final int ITERATIONS = 100;

    private static void compute(Matrix2DFloat matrix, VectorFloat vector, VectorFloat output) {
        for (@Parallel int i = 0; i < matrix.getNumRows(); i++) {
            float sum = 0.0f;
            for (int j = 0; j < matrix.getNumColumns(); j++) {
                sum += matrix.get(i, j) * vector.get(j);
            }
            output.set(i, sum);
        }
    }

    private static void computeWithVectors(Matrix2DFloat4 matrix, VectorFloat4 vector, VectorFloat output) {
        for (@Parallel int i = 0; i < matrix.getNumRows(); i++) {
            float sum = 0;
            for (int j = 0; j < matrix.getNumColumns(); j++) {
                sum += Float4.sum(Float4.mult(matrix.get(i, j), vector.get(j)));
            }
            output.set(i, sum);
        }
    }

    private static void runWithVectorTypes(int size, TornadoDevice device) {

        Matrix2DFloat4 matrix2DFloat = new Matrix2DFloat4(size, size);

        // Vector must be of size N
        VectorFloat4 vectorFloat = new VectorFloat4(size);

        // Output
        VectorFloat result = new VectorFloat(size);

        Random r = new Random();
        final int s = size;

        // Init Data
        IntStream.range(0, size).forEach(idx -> vectorFloat.set(idx, new Float4(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat())));
        IntStream.range(0, size).forEach(idx -> IntStream.range(0, s) //
                .forEach(jdx -> //
                matrix2DFloat.set(idx, jdx, new Float4(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()))));

        TaskGraph taskGraph = new TaskGraph("computeVectors") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, vectorFloat, matrix2DFloat) //
                .task("witVectors", MatrixVector::computeWithVectors, matrix2DFloat, vectorFloat, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDevice(device).withPreCompilation();

        for (int i = 0; i < WARMUP; i++) {
            executionPlan.execute();
        }

        ArrayList<Long> kernelTimers = new ArrayList<>();
        ArrayList<Long> totalTimers = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            TornadoExecutionResult executionResult = executionPlan.execute();
            kernelTimers.add(executionResult.getProfilerResult().getDeviceKernelTime());
            totalTimers.add(executionResult.getProfilerResult().getTotalTime());
        }

        executionPlan.freeDeviceMemory();

        long[] kernelTimersLong = kernelTimers.stream().mapToLong(Long::longValue).toArray();
        long[] totalTimersLong = totalTimers.stream().mapToLong(Long::longValue).toArray();
        System.out.println("Stats KernelTime");
        Utils.computeStatistics(kernelTimersLong);
        System.out.println("Stats TotalTime");
        Utils.computeStatistics(totalTimersLong);
    }

    private static void runWithoutVectorTypes(int size, TornadoDevice device) {
        final int s = size * 4;
        size = size * 4;
        Matrix2DFloat matrix2DFloat = new Matrix2DFloat(size, size);

        // Vector must be of size N
        VectorFloat vectorFloat = new VectorFloat(size);

        // Output
        VectorFloat result = new VectorFloat(size);

        Random r = new Random();

        // Init Data
        IntStream.range(0, size).forEach(idx -> vectorFloat.set(idx, r.nextFloat()));
        IntStream.range(0, size).forEach(idx -> IntStream.range(0, s) //
                .forEach(jdx -> //
                matrix2DFloat.set(idx, jdx, r.nextFloat())));

        TaskGraph taskGraph = new TaskGraph("compute") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, vectorFloat, matrix2DFloat) //
                .task("noVectors", MatrixVector::compute, matrix2DFloat, vectorFloat, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDevice(device).withPreCompilation();

        for (int i = 0; i < WARMUP; i++) {
            executionPlan.execute();
        }

        ArrayList<Long> kernelTimers = new ArrayList<>();
        ArrayList<Long> totalTimers = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            TornadoExecutionResult executionResult = executionPlan.execute();
            kernelTimers.add(executionResult.getProfilerResult().getDeviceKernelTime());
            totalTimers.add(executionResult.getProfilerResult().getTotalTime());
        }

        executionPlan.freeDeviceMemory();

        long[] kernelTimersLong = kernelTimers.stream().mapToLong(Long::longValue).toArray();
        long[] totalTimersLong = totalTimers.stream().mapToLong(Long::longValue).toArray();
        System.out.println("Stats KernelTime");
        Utils.computeStatistics(kernelTimersLong);
        System.out.println("Stats TotalTime");
        Utils.computeStatistics(totalTimersLong);
    }

    private static void computeWithStreams(final int size, Matrix2DFloat matrix, VectorFloat vector, VectorFloat output) {
        IntStream.range(0, size).parallel().forEach(i -> {
            float sum = 0.0f;
            for (int j = 0; j < matrix.getNumColumns(); j++) {
                sum += matrix.get(i, j) * vector.get(j);
            }
            output.set(i, sum);
        });
    }

    private static void runWithJavaStreams(int size) {
        size = size * 4;
        Matrix2DFloat matrix2DFloat = new Matrix2DFloat(size, size);

        // Vector must be of size N
        VectorFloat vectorFloat = new VectorFloat(size);

        // Output
        VectorFloat result = new VectorFloat(size);

        Random r = new Random();

        // Init Data
        IntStream.range(0, size).forEach(idx -> vectorFloat.set(idx, r.nextFloat()));
        final int s = size;
        IntStream.range(0, size).forEach(idx -> IntStream.range(0, s) //
                .forEach(jdx -> //
                matrix2DFloat.set(idx, jdx, r.nextFloat())));

        for (int i = 0; i < WARMUP; i++) {
            computeWithStreams(size, matrix2DFloat, vectorFloat, result);
        }

        ArrayList<Long> kernelTimersVectors = new ArrayList<>();
        // Execution of vector types version
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            computeWithStreams(size, matrix2DFloat, vectorFloat, result);
            long end = System.nanoTime();
            kernelTimersVectors.add((end - start));
        }

        long[] kernelTimersVectorsLong = kernelTimersVectors.stream().mapToLong(Long::longValue).toArray();
        System.out.println("Stats");
        Utils.computeStatistics(kernelTimersVectorsLong);
    }

    public static void main(String[] args) {
        String version = "vector";
        if (args.length > 0) {
            try {
                version = args[0];
            } catch (NumberFormatException ignored) {
            }
        }

        int size = 2048;
        if (args.length > 1) {
            try {
                size = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        TornadoDevice device = TornadoExecutionPlan.getDevice(0, 0);

        if (version.startsWith("vector")) {
            runWithVectorTypes(size, device);
        } else if (version.startsWith("stream")) {
            runWithJavaStreams(size);
        } else {
            runWithoutVectorTypes(size, device);
        }
    }
}
