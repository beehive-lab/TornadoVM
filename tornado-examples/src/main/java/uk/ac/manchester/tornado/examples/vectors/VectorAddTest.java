/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat4;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.examples.utils.Utils;

/**
 * Test Using the Profiler and Vector Types in TornadoVM
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado --threadInfo --enableProfiler console -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.VectorAddTest
 * </code>
 *
 */
public class VectorAddTest {

    public static final int WARMUP = 100;
    public static final int ITERATIONS = 100;

    private static void computeAdd(float[] a, float[] b, float[] results) {
        for (@Parallel int i = 0; i < a.length; i++) {
            results[i] = a[i] + b[i];
        }
    }

    private static void computeAddWithVectors(VectorFloat4 a, VectorFloat4 b, VectorFloat4 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float4.add(a.get(i), b.get(i)));
        }
    }

    private static void runWithVectorTypes(int size, TornadoDevice device) {
        final VectorFloat4 a = new VectorFloat4(size);
        final VectorFloat4 b = new VectorFloat4(size);
        final VectorFloat4 results = new VectorFloat4(size);

        for (int i = 0; i < a.getLength(); i++) {
            a.set(i, new Float4(i, i, i, i));
            b.set(i, new Float4(2 * i, 2 * i, 2 * i, 2 * i));
        }

        TaskGraph taskGraph = new TaskGraph("compute") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("addWithVectors", VectorAddTest::computeAddWithVectors, a, b, results) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, results);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executorPlan.withDevice(device);

        ArrayList<Long> kernelTimersVectors = new ArrayList<>();
        // Execution of vector types version
        for (int i = 0; i < ITERATIONS; i++) {
            TornadoExecutionResult executionResult = executorPlan.execute();
            kernelTimersVectors.add(executionResult.getProfilerResult().getDeviceKernelTime());
        }

        executorPlan.freeDeviceMemory();

        long[] kernelTimersVectorsLong = kernelTimersVectors.stream().mapToLong(Long::longValue).toArray();

        System.out.println("Stats");
        Utils.computeStatistics(kernelTimersVectorsLong);
    }

    private static void runWithoutVectorTypes(int size, TornadoDevice device) {
        float[] af = new float[size * 4];
        float[] bf = new float[size * 4];
        float[] rf = new float[size * 4];

        for (int i = 0; i < af.length; i++) {
            af[i] = i;
            bf[i] = 2.0f * i;
        }

        TaskGraph taskGraphNonVector = new TaskGraph("nonVector") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, af, bf) //
                .task("computeWithPrimitiveArray", VectorAddTest::computeAdd, af, bf, rf) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, rf);

        ImmutableTaskGraph immutableTaskGraph2 = taskGraphNonVector.snapshot();
        TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph2);
        executorPlan.withDevice(device);

        for (int i = 0; i < WARMUP; i++) {
            executorPlan.execute();
        }

        ArrayList<Long> kernelTimers = new ArrayList<>();
        // Execution with no vector types
        for (int i = 0; i < ITERATIONS; i++) {
            TornadoExecutionResult executionResult = executorPlan.execute();
            kernelTimers.add(executionResult.getProfilerResult().getDeviceKernelTime());
        }

        executorPlan.freeDeviceMemory();

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

        int size = 16777216;
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
