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
import java.util.Arrays;
import java.util.stream.IntStream;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat16;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat2;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat8;
import uk.ac.manchester.tornado.api.types.vectors.Float16;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.vectors.Float8;
import uk.ac.manchester.tornado.examples.utils.Utils;

/**
 * Select in the first argument the desired vector length: {vector2, vector4, vector8, vector16}.
 * This test sets the device index to 2. To change the device index.
 *
 * <p>
 * How to run?
 * </p>
 * Run with the vector types:
 * <code>
 * tornado --threadInfo --enableProfiler silent -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.DFTVector vector8
 * </code>
 * <p>
 * Run with no vector types:
 * <code>
 * tornado --threadInfo --enableProfiler silent -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.DFTVector plain
 * </code>
 * <p>
 * Run with Java Streams:
 * <code>
 * tornado --threadInfo --enableProfiler silent -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.DFTVector stream
 * </code>
 */
public class DFTVector {

    public static final int WARMUP = 100;
    public static final int ITERATIONS = 100;

    public static void computeDFT(FloatArray inReal, FloatArray inImag, FloatArray outReal, FloatArray outImag) {
        int n = inReal.getSize();
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            float sumReal = 0;
            float simImag = 0;
            for (int t = 0; t < n; t++) { // For each input element
                float angle = ((2 * TornadoMath.floatPI() * t * k) / n);
                sumReal += inReal.get(t) * TornadoMath.cos(angle) + inImag.get(t) * TornadoMath.sin(angle);
                simImag += -inReal.get(t) * TornadoMath.sin(angle) + inImag.get(t) * TornadoMath.cos(angle);
            }
            outReal.set(k, sumReal);
            outImag.set(k, simImag);
        }
    }

    public static void computeDFTJavaVectorAPI(float[] inreal, float[] inimag, float[] outreal, float[] outimag) {
        final int n = inreal.length;

        // Pre-compute angles
        // Because AFAIK, there are no APIs to compute Math.cos over a Java Vector Type,
        // we need to pre-compute the angles to be ble to pre-compute cos and sin operations.
        // There are two ways:
        // a) Just pre-compute the angles and then inside the actual DFT, use this
        //    intermediate values to compute the Math.con and Math.sin. This strategy
        //    penalizes the overall execution with Vector API. Thus, option b)
        // b) Pre-compute all Math.cos and Math.sin that will be used for later.
        float[][] cosAngles = new float[n][n];
        float[][] sinAnbles = new float[n][n];
        for (int i = 0; i < cosAngles.length; i++) {
            for (int j = 0; j < cosAngles.length; j++) {
                float angle = (2 * TornadoMath.floatPI() * j * i) / n;
                cosAngles[i][j] = TornadoMath.cos(angle);
                sinAnbles[i][j] = TornadoMath.sin(angle);
            }
        }

        VectorSpecies<Float> speciesPreferred = FloatVector.SPECIES_PREFERRED;
        final int upperBound = FloatVector.SPECIES_PREFERRED.loopBound(inreal.length);
        final int vectorWidth = FloatVector.SPECIES_PREFERRED.length();
        float[] init = new float[vectorWidth];
        Arrays.fill(init, -1);

        for (int k = 0; k < upperBound; k += vectorWidth) {

            //Float2 sumReal = new Float2(0, 0);
            var sumReal = FloatVector.zero(speciesPreferred);

            //Float2 simImag = new Float2(0, 0);
            var simImag = FloatVector.zero(speciesPreferred);

            for (int t = 0; t < upperBound; t += vectorWidth) {
                //float angle = (2 * TornadoMath.floatPI() * t * k) / n;

                // Take the pre-compute cosAngles and sinAngles
                var cosAngle = FloatVector.fromArray(speciesPreferred, cosAngles[k], t);
                var sinAngle = FloatVector.fromArray(speciesPreferred, sinAnbles[k], t);

                //Float2 partA = Float2.mult(inreal.get(t), TornadoMath.cos(angle));
                var va = FloatVector.fromArray(speciesPreferred, inreal, t);
                var res1 = va.mul(cosAngle);

                //Float2 partB = Float2.mult(inimag.get(t), TornadoMath.sin(angle));
                var vb = FloatVector.fromArray(speciesPreferred, inimag, t);
                var res2 = vb.mul(sinAngle);
                //Float2 partC = Float2.add(partA, partB);
                var partC = res1.add(res2);

                //sumReal = Float2.add(sumReal, partC);
                sumReal = sumReal.add(partC);

                var initVector = FloatVector.fromArray(speciesPreferred, inimag, 0);
                va = FloatVector.fromArray(speciesPreferred, inreal, t);
                //Float2 neg = Float2.mult(inreal.get(t), new Float2(-1, -1));
                //Float2 partAImag = Float2.mult(neg, TornadoMath.sin(angle));
                var partAImag = va.mul(initVector).mul(sinAngle);

                //Float2 partBImag = Float2.mult(inimag.get(t), TornadoMath.cos(angle));
                var partBImag = vb.mul(cosAngle);

                //Float2 partCImag = Float2.add(partAImag, partBImag);
                var partCImag = partAImag.add(partBImag);

                //simImag = Float2.add(simImag, partCImag);
                simImag = simImag.add(partCImag);
            }
            sumReal.intoArray(outreal, k);
            simImag.intoArray(outimag, k);
        }
    }

    public static void computeDFTJavaVectorAPIWithStreams(float[] inreal, float[] inimag, float[] outreal, float[] outimag) {
        final int n = inreal.length;

        // Pre-compute angles
        // Because AFAIK, there are no APIs to compute Math.cos over a Java Vector Type,
        // we need to pre-compute the angles to be ble to pre-compute cos and sin operations.
        // There are two ways:
        // a) Just pre-compute the angles and then inside the actual DFT, use this
        //    intermediate values to compute the Math.con and Math.sin. This strategy
        //    penalizes the overall execution with Vector API. Thus, option b)
        // b) Pre-compute all Math.cos and Math.sin that will be used for later.
        float[][] cosAngles = new float[n][n];
        float[][] sinAnbles = new float[n][n];
        for (int i = 0; i < cosAngles.length; i++) {
            for (int j = 0; j < cosAngles.length; j++) {
                float angle = (2 * TornadoMath.floatPI() * j * i) / n;
                cosAngles[i][j] = TornadoMath.cos(angle);
                sinAnbles[i][j] = TornadoMath.sin(angle);
            }
        }

        VectorSpecies<Float> speciesPreferred = FloatVector.SPECIES_PREFERRED;
        final int upperBound = FloatVector.SPECIES_PREFERRED.loopBound(inreal.length);
        final int vectorWidth = FloatVector.SPECIES_PREFERRED.length();
        float[] init = new float[vectorWidth];
        Arrays.fill(init, -1);

        // split iteration space
        ArrayList<Integer> iterationSpace = new ArrayList<>();
        for (int k = 0; k < upperBound; k += vectorWidth) {
            iterationSpace.add(k);
        }

        iterationSpace.stream().parallel().forEach(k -> {
            //Float2 sumReal = new Float2(0, 0);
            var sumReal = FloatVector.zero(speciesPreferred);

            //Float2 simImag = new Float2(0, 0);
            var simImag = FloatVector.zero(speciesPreferred);

            for (int t = 0; t < upperBound; t += vectorWidth) {

                //float angle = (2 * TornadoMath.floatPI() * t * k) / n;
                var cosAngle = FloatVector.fromArray(speciesPreferred, cosAngles[k], t);
                var sinAngle = FloatVector.fromArray(speciesPreferred, sinAnbles[k], t);

                //Float2 partA = Float2.mult(inreal.get(t), TornadoMath.cos(angle));
                var va = FloatVector.fromArray(speciesPreferred, inreal, t);
                var res1 = va.mul(cosAngle);

                //Float2 partB = Float2.mult(inimag.get(t), TornadoMath.sin(angle));
                var vb = FloatVector.fromArray(speciesPreferred, inimag, t);
                var res2 = vb.mul(sinAngle);
                //Float2 partC = Float2.add(partA, partB);
                var partC = res1.add(res2);

                //sumReal = Float2.add(sumReal, partC);
                sumReal = sumReal.add(partC);

                var initVector = FloatVector.fromArray(speciesPreferred, inimag, 0);
                va = FloatVector.fromArray(speciesPreferred, inreal, t);
                //Float2 neg = Float2.mult(inreal.get(t), new Float2(-1, -1));
                //Float2 partAImag = Float2.mult(neg, TornadoMath.sin(angle));
                var partAImag = va.mul(initVector).mul(sinAngle);

                //Float2 partBImag = Float2.mult(inimag.get(t), TornadoMath.cos(angle));
                var partBImag = vb.mul(cosAngle);

                //Float2 partCImag = Float2.add(partAImag, partBImag);
                var partCImag = partAImag.add(partBImag);

                //simImag = Float2.add(simImag, partCImag);
                simImag = simImag.add(partCImag);
            }

            sumReal.intoArray(outreal, k);
            simImag.intoArray(outimag, k);
        });
    }

    public static void computeDFTVector2(VectorFloat2 inreal, VectorFloat2 inimag, VectorFloat2 outreal, VectorFloat2 outimag) {
        int n = inreal.getLength();
        for (@Parallel int k = 0; k < n; k++) {
            Float2 sumReal = new Float2(0, 0);
            Float2 simImag = new Float2(0, 0);
            float base = (2 * TornadoMath.floatPI() * k) / n;
            for (int t = 0; t < n; t++) {
                int tt = t * 2;
                float angle0 = base * tt;
                float angle1 = base * (tt + 1);
                Float2 angleVector = new Float2(angle0, angle1);

                Float2 cosAngleVector = TornadoMath.cos(angleVector);
                Float2 sinAngleVector = TornadoMath.sin(angleVector);
                Float2 partA = Float2.mult(inreal.get(t), cosAngleVector);
                Float2 partB = Float2.mult(inimag.get(t), sinAngleVector);
                Float2 partC = Float2.add(partA, partB);
                sumReal = Float2.add(sumReal, partC);

                Float2 neg = Float2.mult(inreal.get(t), new Float2(-1, -1));
                Float2 partAImag = Float2.mult(neg, sinAngleVector);
                Float2 partBImag = Float2.mult(inimag.get(t), cosAngleVector);
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
            float base = (2 * TornadoMath.floatPI() * k) / n;
            for (int t = 0; t < n; t++) { // For each input element
                int tt = t * 4;
                float angle0 = base * tt;
                float angle1 = base * (tt + 1);
                float angle2 = base * (tt + 2);
                float angle3 = base * (tt + 3);
                Float4 angleVector = new Float4(angle0, angle1, angle2, angle3);

                Float4 cosAngleVector = TornadoMath.cos(angleVector);
                Float4 sinAngleVector = TornadoMath.sin(angleVector);
                Float4 partA = Float4.mult(inreal.get(t), cosAngleVector);
                Float4 partB = Float4.mult(inimag.get(t), sinAngleVector);
                Float4 partC = Float4.add(partA, partB);
                sumReal = Float4.add(sumReal, partC);

                Float4 neg = Float4.mult(inreal.get(t), new Float4(-1, -1, -1, -1));
                Float4 partAImag = Float4.mult(neg, sinAngleVector);
                Float4 partBImag = Float4.mult(inimag.get(t), cosAngleVector);
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
            float base = (2 * TornadoMath.floatPI() * k) / n;
            for (int t = 0; t < n; t++) { // For each input element
                int tt = t * 8;
                float angle0 = base * tt;
                float angle1 = base * (tt + 1);
                float angle2 = base * (tt + 2);
                float angle3 = base * (tt + 3);
                float angle4 = base * (tt + 4);
                float angle5 = base * (tt + 5);
                float angle6 = base * (tt + 6);
                float angle7 = base * (tt + 7);
                Float8 angleVector = new Float8(angle0, angle1, angle2, angle3, angle4, angle5, angle6, angle7);

                Float8 cosAngleVector = TornadoMath.cos(angleVector);
                Float8 sinAngleVector = TornadoMath.sin(angleVector);
                Float8 partA = Float8.mult(inreal.get(t), cosAngleVector);
                Float8 partB = Float8.mult(inimag.get(t), sinAngleVector);
                Float8 partC = Float8.add(partA, partB);
                sumReal = Float8.add(sumReal, partC);

                Float8 neg = Float8.mult(inreal.get(t), new Float8(-1, -1, -1, -1, -1, -1, -1, -1));
                Float8 partAImag = Float8.mult(neg, sinAngleVector);
                Float8 partBImag = Float8.mult(inimag.get(t), cosAngleVector);
                Float8 partCImag = Float8.add(partAImag, partBImag);
                simImag = Float8.add(simImag, partCImag);

            }
            outreal.set(k, sumReal);
            outimag.set(k, simImag);
        }
    }

    public static void computeDFTVector16(VectorFloat16 inreal, VectorFloat16 inimag, VectorFloat16 outreal, VectorFloat16 outimag) {
        int n = inreal.getLength();
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            Float16 sumReal = new Float16(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            Float16 sumImag = new Float16(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            float base = (2 * TornadoMath.floatPI() * k) / n;
            for (int t = 0; t < n; t++) { // For each input element
                int tt = t * 16;
                float angle0 = base * tt;
                float angle1 = base * (tt + 1);
                float angle2 = base * (tt + 2);
                float angle3 = base * (tt + 3);
                float angle4 = base * (tt + 4);
                float angle5 = base * (tt + 5);
                float angle6 = base * (tt + 6);
                float angle7 = base * (tt + 7);
                float angle8 = base * (tt + 8);
                float angle9 = base * (tt + 9);
                float angle10 = base * (tt + 10);
                float angle11 = base * (tt + 11);
                float angle12 = base * (tt + 12);
                float angle13 = base * (tt + 13);
                float angle14 = base * (tt + 14);
                float angle15 = base * (tt + 15);
                Float16 angleVector = new Float16(angle0, angle1, angle2, angle3, angle4, angle5, angle6, angle7, angle8, angle9, angle10, angle11, angle12, angle13, angle14, angle15);

                Float16 cosAngleVector = TornadoMath.cos(angleVector);
                Float16 sinAngleVector = TornadoMath.sin(angleVector);
                Float16 partA = Float16.mult(inreal.get(t), cosAngleVector);
                Float16 partB = Float16.mult(inimag.get(t), sinAngleVector);
                Float16 partC = Float16.add(partA, partB);
                sumReal = Float16.add(sumReal, partC);

                Float16 neg = Float16.mult(inreal.get(t), new Float16(-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1));
                Float16 partAImag = Float16.mult(neg, sinAngleVector);
                Float16 partBImag = Float16.mult(inimag.get(t), cosAngleVector);
                Float16 partCImag = Float16.add(partAImag, partBImag);
                sumImag = Float16.add(sumImag, partCImag);
            }

            outreal.set(k, sumReal);
            outimag.set(k, sumImag);
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
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

            long[] kernelTimersLong = kernelTimers.stream().mapToLong(Long::longValue).toArray();
            long[] totalTimersLong = totalTimers.stream().mapToLong(Long::longValue).toArray();
            System.out.println("Stats KernelTime");
            Utils.computeStatistics(kernelTimersLong);
            System.out.println("Stats TotalTime");
            Utils.computeStatistics(totalTimersLong);
        } catch (TornadoExecutionPlanException e) {
            e.printStackTrace();
        }
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
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
        } catch (TornadoExecutionPlanException e) {
            e.printStackTrace();
        }
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
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
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
        } catch (TornadoExecutionPlanException e) {

        }
    }

    private static void runWithVectorTypes16(int size, TornadoDevice device) {
        size = size / 2;
        VectorFloat16 inReal = new VectorFloat16(size);
        VectorFloat16 inImag = new VectorFloat16(size);
        VectorFloat16 outReal = new VectorFloat16(size);
        VectorFloat16 outImag = new VectorFloat16(size);

        for (int i = 0; i < size; i++) {
            float valA = 1 / (float) (i + 2);
            float valB = 1 / (float) (i + 2);
            inReal.set(i, new Float16(valA, valA, valA, valA, valA, valA, valA, valA, valA, valA, valA, valA, valA, valA, valA, valA));
            inImag.set(i, new Float16(valB, valB, valB, valB, valB, valB, valB, valB, valB, valB, valB, valB, valB, valB, valB, valB));
        }

        TaskGraph taskGraph = new TaskGraph("compute") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inReal, inImag) //
                .task("withVectors16", DFTVector::computeDFTVector16, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
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
        } catch (TornadoExecutionPlanException e) {

        }
    }

    private static void computeWithStreams(final int size, FloatArray inreal, FloatArray inimag, FloatArray outreal, FloatArray outimag) {
        int n = inreal.getSize();
        IntStream.range(0, size).parallel().forEach(k -> {
            float sumReal = 0;
            float simImag = 0;
            for (int t = 0; t < n; t++) { // For each input element
                float angle = (2 * TornadoMath.floatPI() * t * k) / n;
                sumReal += inreal.get(t) * TornadoMath.cos(angle) + inimag.get(t) * TornadoMath.sin(angle);
                simImag += -inreal.get(t) * TornadoMath.sin(angle) + inimag.get(t) * TornadoMath.cos(angle);
            }
            outreal.set(k, sumReal);
            outimag.set(k, simImag);
        });
    }

    private static void runWithJavaStreams(int size) {
        size *= 4;
        FloatArray inReal = new FloatArray(size);
        FloatArray inImag = new FloatArray(size);
        FloatArray outReal = new FloatArray(size);
        FloatArray outImag = new FloatArray(size);

        for (int i = 0; i < size; i++) {
            inReal.set(i, 1 / (float) (i + 2));
            inImag.set(i, 1 / (float) (i + 2));
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
        FloatArray inReal = new FloatArray(size);
        FloatArray inImag = new FloatArray(size);
        FloatArray outReal = new FloatArray(size);
        FloatArray outImag = new FloatArray(size);

        for (int i = 0; i < size; i++) {
            inReal.set(i, 1 / (float) (i + 2));
            inImag.set(i, 1 / (float) (i + 2));
        }

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, inReal, inImag) //
                .task("t0", DFTVector::computeDFT, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withPreCompilation().withDevice(device);

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
        } catch (TornadoExecutionPlanException e) {
            e.printStackTrace();
        }
    }

    private static void runWithJavaVectorAPI(int size) {
        size = size * 4;
        float[] inReal = new float[size];
        float[] inImag = new float[size];
        float[] outReal = new float[size];
        float[] outImag = new float[size];
        for (int i = 0; i < size; i++) {
            inReal[i] = 1 / (float) (i + 2);
            inImag[i] = 1 / (float) (i + 2);
        }

        for (int i = 0; i < WARMUP; i++) {
            computeDFTJavaVectorAPI(inReal, inImag, outReal, outImag);
        }

        ArrayList<Long> kernelTimersVectors = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            computeDFTJavaVectorAPI(inReal, inImag, outReal, outImag);
            long end = System.nanoTime();
            kernelTimersVectors.add((end - start));
        }

        long[] kernelTimersVectorsLong = kernelTimersVectors.stream().mapToLong(Long::longValue).toArray();
        System.out.println("Stats");
        Utils.computeStatistics(kernelTimersVectorsLong);
    }

    private static void runWithJavaVectorAPIStreamAPI(int size) {
        size = size * 4;
        float[] inReal = new float[size];
        float[] inImag = new float[size];
        float[] outReal = new float[size];
        float[] outImag = new float[size];
        for (int i = 0; i < size; i++) {
            inReal[i] = 1 / (float) (i + 2);
            inImag[i] = 1 / (float) (i + 2);
        }

        for (int i = 0; i < WARMUP; i++) {
            computeDFTJavaVectorAPIWithStreams(inReal, inImag, outReal, outImag);
        }

        ArrayList<Long> kernelTimersVectors = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            computeDFTJavaVectorAPIWithStreams(inReal, inImag, outReal, outImag);
            long end = System.nanoTime();
            kernelTimersVectors.add((end - start));
        }

        long[] kernelTimersVectorsLong = kernelTimersVectors.stream().mapToLong(Long::longValue).toArray();
        System.out.println("Stats");
        Utils.computeStatistics(kernelTimersVectorsLong);
    }

    public static void main(String[] args) {
        String version = "vector4";
        if (args.length > 0) {
            try {
                version = args[0];
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        int size = 8192;
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
        } else if (version.startsWith("vector16")) {
            runWithVectorTypes16(size, device);
        } else if (version.startsWith("stream")) {
            runWithJavaStreams(size);
        } else if (version.startsWith("plain")) {
            runWithoutVectorTypes(size, device);
        } else if (version.startsWith("javaVector")) {
            runWithJavaVectorAPI(size);
        } else if (version.startsWith("javaStreamsVector")) {
            runWithJavaVectorAPIStreamAPI(size);
        } else {
            throw new RuntimeException("Option not found");
        }
    }
}
