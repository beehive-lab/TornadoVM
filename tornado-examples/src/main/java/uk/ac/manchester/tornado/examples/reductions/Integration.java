/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.reductions;

import java.util.ArrayList;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.reductions.Integration
 * </code>
 *
 */
public class Integration {

    private static final int LOWER = 1;
    private static final int UPPER = 4;

    public static float f(float x) {
        return (1 / ((x + 1) * TornadoMath.sqrt(x * TornadoMath.exp(x))));
    }

    public float integrationSequential(int size, final float a, final float b) {
        float sum = 0.0f;
        for (int i = 1; i < (size + 1); i++) {
            sum += f(a + ((i - (1.f / 2.f)) * ((b - a) / size)));
        }
        return ((b - a) / size) * sum;
    }

    public float runIntegrationSequential(int size) {

        System.out.println("Running Sequential version");

        final float a = LOWER;
        final float b = UPPER;
        float finalValue = 0.0f;
        ArrayList<Long> timers = new ArrayList<>();
        for (int i = 0; i < ConfigurationReduce.MAX_ITERATIONS; i++) {

            long start = System.nanoTime();
            finalValue = integrationSequential(size, a, b);
            long end = System.nanoTime();
            timers.add((end - start));
        }

        System.out.println("IntegrationValue: " + finalValue);
        System.out.println("Median TotalTime: " + Stats.computeMedian(timers));
        return finalValue;
    }

    public static void integrationTornado(FloatArray input, @Reduce FloatArray sum, final float a, final float b) {
        final int size = input.getSize();
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            float value = f(a + (((i + 1) - (1 / 2)) * ((b - a) / size)));
            sum.set(0, sum.get(0) + input.get(i) + value);
        }
    }

    public float runTornado(final int size) {

        System.out.println("\nRunning Tornado version");

        FloatArray input = new FloatArray(size);
        FloatArray result = new FloatArray(1);
        result.init(0.0f);

        final float a = LOWER;
        final float b = UPPER;

        float finalValue = 0.0f;

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, a, b)//
                .task("t0", Integration::integrationTornado, input, result, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);

        ArrayList<Long> timers = new ArrayList<>();
        for (int i = 0; i < ConfigurationReduce.MAX_ITERATIONS; i++) {

            IntStream.range(0, size).sequential().forEach(idx -> {
                input.set(idx, 0);
            });

            long start = System.nanoTime();
            executor.execute();
            long end = System.nanoTime();

            finalValue = ((b - a) / size) * result.get(0);
            // System.out.println("IntegrationValue: " + finalValue);
            timers.add((end - start));
        }

        System.out.println("IntegrationValue: " + finalValue);
        System.out.println("Median TotalTime: " + Stats.computeMedian(timers));
        return finalValue;
    }

    public static void main(String[] args) {

        int size = 8192;
        if (args.length > 0) {
            try {
                size = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                size = 8192;
            }
        }

        // Run with Tornado
        new Integration().runTornado(size);
    }
}
