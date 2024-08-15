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

package uk.ac.manchester.tornado.unittests.functional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.functional.TestLambdas
 * </code>
 */
public class TestLambdas extends TornadoTestBase {

    @Test
    public void testVectorFunctionLambda() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray c = new DoubleArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, Math.random());
            b.set(i, Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", (x, y, z) -> {
                    // Computation in a lambda expression
                    for (@Parallel int i = 0; i < z.getSize(); i++) {
                        z.set(i, x.get(i) + y.get(i));
                    }
                }, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) + b.get(i), c.get(i), 0.001);
        }
    }

    @Test
    public void testVectorFunctionLambda02() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        DoubleArray a = new DoubleArray(numElements);
        DoubleArray b = new DoubleArray(numElements);
        DoubleArray c = new DoubleArray(numElements);

        Random r = new Random();

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, r.nextDouble());
            b.set(i, r.nextInt(1000));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", (x, y, z) -> {
                    // Computation in a lambda expression
                    for (@Parallel int i = 0; i < z.getSize(); i++) {
                        z.set(i, x.get(i) * y.get(i));
                    }
                }, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) * b.get(i), c.get(i), 0.001);
        }
    }

    @Test
    public void testVectorFunctionLambda03() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        DoubleArray a = new DoubleArray(numElements);
        IntArray b = new IntArray(numElements);
        DoubleArray c = new DoubleArray(numElements);

        Random r = new Random();

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, r.nextDouble());
            b.set(i, r.nextInt(1000));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", (x, y, z) -> {
                    // Computation in a lambda expression
                    for (@Parallel int i = 0; i < z.getSize(); i++) {
                        z.set(i, x.get(i) * y.get(i));
                    }
                }, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(a.get(i) * b.get(i), c.get(i), 0.001);
        }
    }

    @Test
    public void testParameterUnboxing() throws TornadoExecutionPlanException {
        var arrayToCopy = new float[] { 1.0f, 2.0f, 3.0f, 4.0f, 5.0f };

        var taskGraph = new TaskGraph("s0");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayToCopy);

        var resultArray = new FloatArray(arrayToCopy.length);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, resultArray);

        taskGraph.task("t0", (source, sourceOffset, destination, destinationOffset, length) -> {
            for (@Parallel int i = 0; i < length; i++) {
                destination.set(destinationOffset + i, source[sourceOffset + i]);
            }
        }, arrayToCopy, 0, resultArray, 0, arrayToCopy.length);

        var snapshot = taskGraph.snapshot();
        try (var executionPlan = new TornadoExecutionPlan(snapshot)) {
            executionPlan.execute();
        }

        assertArrayEquals(arrayToCopy, resultArray.toHeapArray(), 0.001f);
    }

}
