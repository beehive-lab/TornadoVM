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

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat4;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.examples.utils.Utils;

/**
 *
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado --threadInfo --enableProfiler silent -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.DFTVector
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
                float angle = (float) ((2 * Math.PI * t * k) / n);
                sumReal += inreal[t] * TornadoMath.cos(angle) + inimag[t] * TornadoMath.sin(angle);
                simImag += -inreal[t] * TornadoMath.sin(angle) + inimag[t] * TornadoMath.cos(angle);
            }
            outreal[k] = sumReal;
            outimag[k] = simImag;
        }
    }

    public static void computeDFTVector(VectorFloat4 inreal, VectorFloat4 inimag, VectorFloat4 outreal, VectorFloat4 outimag) {
        int n = inreal.getLength();
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            Float4 sumReal = new Float4();
            Float4 simImag = new Float4();
            for (int t = 0; t < n; t++) { // For each input element
                float angle = (float) ((2 * Math.PI * t * k) / n);

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

    private static void runWithVectorTypes(int size, TornadoDevice device) {
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
                .task("withVectors", DFTVector::computeDFTVector, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withWarmUp().withDevice(device);

        for (int i = 0; i < WARMUP; i++) {
            executionPlan.execute();
        }

        ArrayList<Long> kernelTimers = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            TornadoExecutionResult executionResult = executionPlan.execute();
            kernelTimers.add(executionResult.getProfilerResult().getDeviceKernelTime());
        }

        executionPlan.freeDeviceMemory();

        long[] kernelTimersLong = kernelTimers.stream().mapToLong(Long::longValue).toArray();
        System.out.println("Stats");
        Utils.computeStatistics(kernelTimersLong);
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
        for (int i = 0; i < ITERATIONS; i++) {
            TornadoExecutionResult executionResult = executionPlan.execute();
            kernelTimers.add(executionResult.getProfilerResult().getDeviceKernelTime());
        }

        executionPlan.freeDeviceMemory();

        long[] kernelTimersLong = kernelTimers.stream().mapToLong(Long::longValue).toArray();
        System.out.println("Stats");
        Utils.computeStatistics(kernelTimersLong);
    }

    public static void main(String[] args) {
        boolean runWithVectors = true;
        if (args.length > 0) {
            try {
                runWithVectors = Boolean.parseBoolean(args[0]);
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
        if (runWithVectors) {
            runWithVectorTypes(size, device);
        } else {
            runWithoutVectorTypes(size, device);
        }
    }
}
