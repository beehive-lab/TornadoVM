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

package uk.ac.manchester.tornado.examples.vectors;

import java.util.ArrayList;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat2;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat8;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.vectors.Float8;
import uk.ac.manchester.tornado.examples.utils.Utils;

/**
 * Test Using the Profiler and Vector Types in TornadoVM. This test sets the device index to 2. To change the device index.
 *
 * <p>
 * How to run? Select in the first argument the desired vector length: {vector2, vector4, vector8}.
 * </p>
 * Run with the vector types:
 * <code>
 * tornado --threadInfo --enableProfiler console -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.VectorAddTest vector8
 * </code>
 *
 * Run with no vector types:
 * <code>
 * tornado --threadInfo --enableProfiler console -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.VectorAddTest plain
 * </code>
 *
 * Run with Java Streams:
 * <code>
 * tornado --threadInfo --enableProfiler console -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.VectorAddTest stream
 * </code>
 */
public class VectorAddTest {

    public static final int WARMUP = 100;
    public static final int ITERATIONS = 100;

    private static void computeAdd(FloatArray a, FloatArray b, FloatArray results) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            results.set(i, a.get(i) + b.get(i));
        }
    }

    private static void computeAddWithVectors2(VectorFloat2 a, VectorFloat2 b, VectorFloat2 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float2.add(a.get(i), b.get(i)));
        }
    }

    private static void computeAddWithVectors4(VectorFloat4 a, VectorFloat4 b, VectorFloat4 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float4.add(a.get(i), b.get(i)));
        }
    }

    private static void computeAddWithVectors8(VectorFloat8 a, VectorFloat8 b, VectorFloat8 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float8.add(a.get(i), b.get(i)));
        }
    }

    private static void runWithVectorTypes4(int size, TornadoDevice device) {
        final VectorFloat4 a = new VectorFloat4(size);
        final VectorFloat4 b = new VectorFloat4(size);
        final VectorFloat4 results = new VectorFloat4(size);

        for (int i = 0; i < a.getLength(); i++) {
            a.set(i, new Float4(i, i, i, i));
            b.set(i, new Float4(2 * i, 2 * i, 2 * i, 2 * i));
        }

        TaskGraph taskGraph = new TaskGraph("compute") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("addWithVectors4", VectorAddTest::computeAddWithVectors4, a, b, results) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, results);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executorPlan.withDevice(device).withPreCompilation();

        ArrayList<Long> kernelTimers = new ArrayList<>();
        ArrayList<Long> totalTimers = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            TornadoExecutionResult executionResult = executorPlan.execute();
            kernelTimers.add(executionResult.getProfilerResult().getDeviceKernelTime());
            totalTimers.add(executionResult.getProfilerResult().getTotalTime());
        }

        executorPlan.freeDeviceMemory();

        long[] kernelTimersLong = kernelTimers.stream().mapToLong(Long::longValue).toArray();
        long[] totalTimersLong = totalTimers.stream().mapToLong(Long::longValue).toArray();
        System.out.println("Stats KernelTime");
        Utils.computeStatistics(kernelTimersLong);
        System.out.println("Stats TotalTime");
        Utils.computeStatistics(totalTimersLong);
    }

    private static void runWithVectorTypes2(int size, TornadoDevice device) {
        size = size * 2;
        final VectorFloat2 a = new VectorFloat2(size);
        final VectorFloat2 b = new VectorFloat2(size);
        final VectorFloat2 results = new VectorFloat2(size);

        for (int i = 0; i < a.getLength(); i++) {
            a.set(i, new Float2(i, i));
            b.set(i, new Float2(2 * i, 2 * i));
        }

        TaskGraph taskGraph = new TaskGraph("compute") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("addWithVectors2", VectorAddTest::computeAddWithVectors2, a, b, results) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, results);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDevice(device).withPreCompilation();

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

    private static void runWithVectorTypes8(int size, TornadoDevice device) {
        size = size / 2;
        final VectorFloat8 a = new VectorFloat8(size);
        final VectorFloat8 b = new VectorFloat8(size);
        final VectorFloat8 results = new VectorFloat8(size);

        for (int i = 0; i < a.getLength(); i++) {
            a.set(i, new Float8(i, i, i, i, i, i, i, i));
            b.set(i, new Float8(2 * i, 2 * i, 2 * i, 2 * i, 2 * i, 2 * i, 2 * i, 2 * i));
        }

        TaskGraph taskGraph = new TaskGraph("compute") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("addWithVectors8", VectorAddTest::computeAddWithVectors8, a, b, results) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, results);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executorPlan.withDevice(device).withPreCompilation();

        ArrayList<Long> kernelTimers = new ArrayList<>();
        ArrayList<Long> totalTimers = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            TornadoExecutionResult executionResult = executorPlan.execute();
            kernelTimers.add(executionResult.getProfilerResult().getDeviceKernelTime());
            totalTimers.add(executionResult.getProfilerResult().getTotalTime());
        }

        executorPlan.freeDeviceMemory();

        long[] kernelTimersLong = kernelTimers.stream().mapToLong(Long::longValue).toArray();
        long[] totalTimersLong = totalTimers.stream().mapToLong(Long::longValue).toArray();
        System.out.println("Stats KernelTime");
        Utils.computeStatistics(kernelTimersLong);
        System.out.println("Stats TotalTime");
        Utils.computeStatistics(totalTimersLong);
    }

    private static void runWithoutVectorTypes(int size, TornadoDevice device) {
        FloatArray af = new FloatArray(size * 4);
        FloatArray bf = new FloatArray(size * 4);
        FloatArray rf = new FloatArray(size * 4);

        for (int i = 0; i < af.getSize(); i++) {
            af.set(i, i);
            bf.set(i, 2.0f * i);
        }

        TaskGraph taskGraphNonVector = new TaskGraph("nonVector") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, af, bf) //
                .task("computeWithPrimitiveArray", VectorAddTest::computeAdd, af, bf, rf) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, rf);

        ImmutableTaskGraph immutableTaskGraph2 = taskGraphNonVector.snapshot();
        TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph2);
        executorPlan.withDevice(device).withPreCompilation();

        for (int i = 0; i < WARMUP; i++) {
            executorPlan.execute();
        }

        ArrayList<Long> kernelTimers = new ArrayList<>();
        ArrayList<Long> totalTimers = new ArrayList<>();
        // Execution with no vector types
        for (int i = 0; i < ITERATIONS; i++) {
            TornadoExecutionResult executionResult = executorPlan.execute();
            kernelTimers.add(executionResult.getProfilerResult().getDeviceKernelTime());
            totalTimers.add(executionResult.getProfilerResult().getTotalTime());
        }

        executorPlan.freeDeviceMemory();

        long[] kernelTimersLong = kernelTimers.stream().mapToLong(Long::longValue).toArray();
        long[] totalTimersLong = totalTimers.stream().mapToLong(Long::longValue).toArray();
        System.out.println("Stats KernelTime");
        Utils.computeStatistics(kernelTimersLong);
        System.out.println("Stats TotalTime");
        Utils.computeStatistics(totalTimersLong);
    }

    private static void computeWithStreams(final int size, FloatArray a, FloatArray b, FloatArray results) {
        IntStream.range(0, size).parallel().forEach(i -> results.set(i, a.get(i) + b.get(i)));
    }

    private static void runWithJavaStreams(int size) {
        size = size * 4;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray results = new FloatArray(size);

        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, i);
            b.set(i, 2.0f * i);
        }

        for (int i = 0; i < WARMUP; i++) {
            computeWithStreams(size, a, b, results);
        }

        ArrayList<Long> kernelTimersVectors = new ArrayList<>();
        // Execution of vector types version
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            computeWithStreams(size, a, b, results);
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

        int size = 16777216;
        if (args.length > 1) {
            try {
                size = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        TornadoDevice device = TornadoExecutionPlan.getDevice(0, 0);

        if (version.startsWith("vector4")) {
            runWithVectorTypes4(size, device);
        } else if (version.startsWith("vector2")) {
            runWithVectorTypes2(size, device);
        } else if (version.startsWith("vector8")) {
            runWithVectorTypes8(size, device);
        } else if (version.startsWith("stream")) {
            runWithJavaStreams(size);
        } else if (version.startsWith("plain")) {
            runWithoutVectorTypes(size, device);
        } else {
            throw new RuntimeException("Option not found");
        }
    }
}
