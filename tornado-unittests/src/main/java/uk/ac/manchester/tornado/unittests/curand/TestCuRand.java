/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.curand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.curand.CuRand;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Random number generation on the device through cuRAND.
 *
 * <p>
 * How to run?
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.curand.TestCuRand
 * </code>
 * </p>
 */
public class TestCuRand extends TornadoTestBase {

    private static final int NUM_ELEMENTS = 1 << 16;

    private static double mean(FloatArray values, int from, int to) {
        double total = 0.0;
        for (int i = from; i < to; i++) {
            total += values.get(i);
        }
        return total / (to - from);
    }

    private static double stddev(FloatArray values, int from, int to, double mean) {
        double total = 0.0;
        for (int i = from; i < to; i++) {
            double d = values.get(i) - mean;
            total += d * d;
        }
        return Math.sqrt(total / (to - from));
    }

    @Test
    public void testGenerateNormal() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(NUM_ELEMENTS);
        output.init(0.0f);

        TaskGraph taskGraph = new TaskGraph("rng") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("normals", CuRand::generateNormal, output, 0, NUM_ELEMENTS, 0.0f, 1.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        double m = mean(output, 0, NUM_ELEMENTS);
        double s = stddev(output, 0, NUM_ELEMENTS, m);
        // 65536 standard normals: the sample mean has standard error 1/256, so 0.05 is ~13 sigma.
        assertEquals("sample mean", 0.0, m, 0.05);
        assertEquals("sample standard deviation", 1.0, s, 0.05);
    }

    @Test
    public void testGenerateNormalWithMeanAndStdDev() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(NUM_ELEMENTS);

        TaskGraph taskGraph = new TaskGraph("rng") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("normals", CuRand::generateNormal, output, 0, NUM_ELEMENTS, 5.0f, 2.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        double m = mean(output, 0, NUM_ELEMENTS);
        assertEquals("sample mean", 5.0, m, 0.10);
        assertEquals("sample standard deviation", 2.0, stddev(output, 0, NUM_ELEMENTS, m), 0.10);
    }

    /**
     * Writing a slice must leave the rest of the buffer alone: the destination is declared
     * READ_WRITE precisely so TornadoVM keeps the untouched region valid.
     */
    @Test
    public void testGenerateIntoSlice() throws TornadoExecutionPlanException {
        int half = NUM_ELEMENTS / 2;
        FloatArray output = new FloatArray(NUM_ELEMENTS);
        output.init(-7.0f);

        TaskGraph taskGraph = new TaskGraph("rng") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("normals", CuRand::generateNormal, output, half, half, 0.0f, 1.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        for (int i = 0; i < half; i++) {
            assertEquals("element " + i + " outside the slice must be untouched", -7.0f, output.get(i), 0.0f);
        }
        double m = mean(output, half, NUM_ELEMENTS);
        assertEquals("sample mean of the slice", 0.0, m, 0.05);
    }

    /** Successive executions must advance the sequence rather than repeat it. */
    @Test
    public void testSuccessiveExecutionsDiffer() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(NUM_ELEMENTS);

        TaskGraph taskGraph = new TaskGraph("rng") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("normals", CuRand::generateNormal, output, 0, NUM_ELEMENTS, 0.0f, 1.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
            float[] first = new float[16];
            for (int i = 0; i < first.length; i++) {
                first[i] = output.get(i);
            }

            executionPlan.execute();
            int identical = 0;
            for (int i = 0; i < first.length; i++) {
                if (first[i] == output.get(i)) {
                    identical++;
                }
            }
            assertNotEquals("a second execution returned the same block of numbers", first.length, identical);
        }
    }

    @Test
    public void testGenerateUniform() throws TornadoExecutionPlanException {
        FloatArray output = new FloatArray(NUM_ELEMENTS);

        TaskGraph taskGraph = new TaskGraph("rng") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("uniform", CuRand::generateUniform, output, 0, NUM_ELEMENTS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            float v = output.get(i);
            assertTrue("uniform value out of (0,1]: " + v, v > 0.0f && v <= 1.0f);
        }
        assertEquals("sample mean", 0.5, mean(output, 0, NUM_ELEMENTS), 0.02);
    }

    @Test
    public void testGenerateNormalDouble() throws TornadoExecutionPlanException {
        DoubleArray output = new DoubleArray(NUM_ELEMENTS);

        TaskGraph taskGraph = new TaskGraph("rng") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, output) //
                .libraryTask("normals", CuRand::generateNormal, output, 0, NUM_ELEMENTS, 0.0d, 1.0d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        double total = 0.0;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            total += output.get(i);
        }
        assertEquals("sample mean", 0.0, total / NUM_ELEMENTS, 0.05);
    }
}
