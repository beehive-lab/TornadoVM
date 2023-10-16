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
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.collections.types.Float2;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.Float8;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat2;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat4;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat8;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.examples.utils.Utils;

/**
 * Select in the first argument the desired vector length: {vector2, vector4, vector8}.
 * This test sets the device index to 2. To change the device index.
 *
 * <p>
 * How to run?
 * </p>
 * Run with the vector types:
 * <code>
 * tornado --threadInfo --enableProfiler silent -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.DFTVector vector8
 * </code>
 *
 * Run with no vector types:
 * <code>
 * tornado --threadInfo --enableProfiler silent -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.DFTVector plain
 * </code>
 *
 * Run with Java Streams:
 * <code>
 * tornado --threadInfo --enableProfiler silent -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.DFTVector stream
 * </code>
 *
 */
public class DFTVector {

    public static final int WARMUP = 100;
    public static final int ITERATIONS = 100;

    public static void computeDFT(float[] inreal, float[] inimag, float[] outreal, float[] outimag) {
        int n = inreal.length;
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            float sumReal = 0;
            float simImag = 0;
            for (int t = 0; t < n; t++) { // For each input element
                float angle = ((2 * TornadoMath.floatPI() * t * k) / n);
                sumReal += inreal[t] * TornadoMath.cos(angle) + inimag[t] * TornadoMath.sin(angle);
                simImag += -inreal[t] * TornadoMath.sin(angle) + inimag[t] * TornadoMath.cos(angle);
            }
            outreal[k] = sumReal;
            outimag[k] = simImag;
        }
    }

    public static void computeDFTVector2(VectorFloat2 inreal, VectorFloat2 inimag, VectorFloat2 outreal, VectorFloat2 outimag) {
        int n = inreal.getLength();
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            Float2 sumReal = new Float2(0, 0);
            Float2 simImag = new Float2(0, 0);
            for (int t = 0; t < n; t++) { // For each input element
                float angle = (float) ((2 * TornadoMath.floatPI() * t * k) / n);

                Float2 partA = Float2.mult(inreal.get(t), TornadoMath.cos(angle));
                Float2 partB = Float2.mult(inimag.get(t), TornadoMath.sin(angle));
                Float2 partC = Float2.add(partA, partB);
                sumReal = Float2.add(sumReal, partC);

                Float2 neg = Float2.mult(inreal.get(t), new Float2(-1, -1));
                Float2 partAImag = Float2.mult(neg, TornadoMath.sin(angle));
                Float2 partBImag = Float2.mult(inimag.get(t), TornadoMath.cos(angle));
                Float2 partCImag = Float2.add(partAImag, partBImag);
                simImag = Float2.add(simImag, partCImag);

            }
            outreal.set(k, sumReal);
            outimag.set(k, simImag);
        }
    }

    public static void computeDFTVector4(VectorFloat4 inreal, VectorFloat4 inimag, VectorFloat4 outreal, VectorFloat4 outimag) {
        int n = inreal.getLength();
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            Float4 sumReal = new Float4(0, 0, 0, 0);
            Float4 simImag = new Float4(0, 0, 0, 0);
            for (int t = 0; t < n; t++) { // For each input element
                float angle = (2 * TornadoMath.floatPI() * t * k) / n;

                Float4 partA = Float4.mult(inreal.get(t), TornadoMath.cos(angle));
                Float4 partB = Float4.mult(inimag.get(t), TornadoMath.sin(angle));
                Float4 partC = Float4.add(partA, partB);
                sumReal = Float4.add(sumReal, partC);

                Float4 neg = Float4.mult(inreal.get(t), new Float4(-1, -1, -1, -1));
                Float4 partAImag = Float4.mult(neg, TornadoMath.sin(angle));
                Float4 partBImag = Float4.mult(inimag.get(t), TornadoMath.cos(angle));
                Float4 partCImag = Float4.add(partAImag, partBImag);
                simImag = Float4.add(simImag, partCImag);

            }
            outreal.set(k, sumReal);
            outimag.set(k, simImag);
        }
    }

    public static void computeDFTVector8(VectorFloat8 inreal, VectorFloat8 inimag, VectorFloat8 outreal, VectorFloat8 outimag) {
        int n = inreal.getLength();
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            Float8 sumReal = new Float8(0, 0, 0, 0, 0, 0, 0, 0);
            Float8 simImag = new Float8(0, 0, 0, 0, 0, 0, 0, 0);
            for (int t = 0; t < n; t++) { // For each input element
                float angle = (float) ((2 * TornadoMath.floatPI() * t * k) / n);

                Float8 partA = Float8.mult(inreal.get(t), TornadoMath.cos(angle));
                Float8 partB = Float8.mult(inimag.get(t), TornadoMath.sin(angle));
                Float8 partC = Float8.add(partA, partB);
                sumReal = Float8.add(sumReal, partC);

                Float8 neg = Float8.mult(inreal.get(t), new Float8(-1, -1, -1, -1, -1, -1, -1, -1));
                Float8 partAImag = Float8.mult(neg, TornadoMath.sin(angle));
                Float8 partBImag = Float8.mult(inimag.get(t), TornadoMath.cos(angle));
                Float8 partCImag = Float8.add(partAImag, partBImag);
                simImag = Float8.add(simImag, partCImag);

            }
            outreal.set(k, sumReal);
            outimag.set(k, simImag);
        }
    }

    private static void runWithVectorTypes4(int size, TornadoDevice device) {
        VectorFloat4 inReal = new VectorFloat4(size);
        VectorFloat4 inImag = new VectorFloat4(size);
        VectorFloat4 outReal = new VectorFloat4(size);
        VectorFloat4 outImag = new VectorFloat4(size);

        for (int i = 0; i < size; i++) {
            float valA = 1 / (float) (i + 2);
            float valB = 1 / (float) (i + 2);
            inReal.set(i, new Float4(valA, valA, valA, valA));
            inImag.set(i, new Float4(valB, valB, valB, valB));
        }

        TaskGraph taskGraph = new TaskGraph("compute") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inReal, inImag) //
                .task("withVectors4", DFTVector::computeDFTVector4, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDevice(device).withWarmUp();

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

    private static void runWithVectorTypes2(int size, TornadoDevice device) {
        size = size * 2;
        VectorFloat2 inReal = new VectorFloat2(size);
        VectorFloat2 inImag = new VectorFloat2(size);
        VectorFloat2 outReal = new VectorFloat2(size);
        VectorFloat2 outImag = new VectorFloat2(size);

        for (int i = 0; i < size; i++) {
            float valA = 1 / (float) (i + 2);
            float valB = 1 / (float) (i + 2);
            inReal.set(i, new Float2(valA, valA));
            inImag.set(i, new Float2(valB, valB));
        }

        TaskGraph taskGraph = new TaskGraph("compute") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inReal, inImag) //
                .task("withVectors2", DFTVector::computeDFTVector2, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDevice(device).withWarmUp();

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

    private static void runWithVectorTypes8(int size, TornadoDevice device) {
        size = size / 2;
        VectorFloat8 inReal = new VectorFloat8(size);
        VectorFloat8 inImag = new VectorFloat8(size);
        VectorFloat8 outReal = new VectorFloat8(size);
        VectorFloat8 outImag = new VectorFloat8(size);

        for (int i = 0; i < size; i++) {
            float valA = 1 / (float) (i + 2);
            float valB = 1 / (float) (i + 2);
            inReal.set(i, new Float8(valA, valA, valA, valA, valA, valA, valA, valA));
            inImag.set(i, new Float8(valB, valB, valB, valB, valB, valB, valB, valB));
        }

        TaskGraph taskGraph = new TaskGraph("compute") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inReal, inImag) //
                .task("withVectors8", DFTVector::computeDFTVector8, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDevice(device).withWarmUp();

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

    private static void computeWithStreams(final int size, float[] inreal, float[] inimag, float[] outreal, float[] outimag) {
        int n = inreal.length;
        IntStream.range(0, size).parallel().forEach(k -> {
            float sumReal = 0;
            float simImag = 0;
            for (int t = 0; t < n; t++) { // For each input element
                float angle = (float) ((2 * TornadoMath.floatPI() * t * k) / n);
                sumReal += inreal[t] * TornadoMath.cos(angle) + inimag[t] * TornadoMath.sin(angle);
                simImag += -inreal[t] * TornadoMath.sin(angle) + inimag[t] * TornadoMath.cos(angle);
            }
            outreal[k] = sumReal;
            outimag[k] = simImag;
        });
    }

    private static void runWithJavaStreams(int size) {
        size *= 4;
        float[] inReal = new float[size];
        float[] inImag = new float[size];
        float[] outReal = new float[size];
        float[] outImag = new float[size];

        for (int i = 0; i < size; i++) {
            inReal[i] = 1 / (float) (i + 2);
            inImag[i] = 1 / (float) (i + 2);
        }

        for (int i = 0; i < WARMUP; i++) {
            computeWithStreams(size, inReal, inImag, outReal, outImag);
        }

        ArrayList<Long> kernelTimersVectors = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            computeWithStreams(size, inReal, inImag, outReal, outImag);
            long end = System.nanoTime();
            kernelTimersVectors.add((end - start));
        }

        long[] kernelTimersVectorsLong = kernelTimersVectors.stream().mapToLong(Long::longValue).toArray();
        System.out.println("Stats");
        Utils.computeStatistics(kernelTimersVectorsLong);
    }

    private static void runWithoutVectorTypes(int size, TornadoDevice device) {
        size *= 4;
        float[] inReal = new float[size];
        float[] inImag = new float[size];
        float[] outReal = new float[size];
        float[] outImag = new float[size];

        for (int i = 0; i < size; i++) {
            inReal[i] = 1 / (float) (i + 2);
            inImag[i] = 1 / (float) (i + 2);
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inReal, inImag) //
                .task("t0", DFTVector::computeDFT, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withWarmUp().withDevice(device);

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

    public static void main(String[] args) {
        String version = "vector4";
        if (args.length > 0) {
            try {
                version = args[0];
            } catch (NumberFormatException ignored) {
            }
        }

        int size = 8192;
        if (args.length > 1) {
            try {
                size = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        TornadoDevice device = TornadoExecutionPlan.getDevice(0, 2);

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
