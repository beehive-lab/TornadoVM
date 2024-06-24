/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.loops;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.loops.TestParallelDimensions
 * </code>
 */
public class TestParallelDimensions extends TornadoTestBase {

    public static void forLoopOneD(IntArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            a.set(i, 10);
        }
    }

    @Test
    public void test1DParallel() throws TornadoExecutionPlanException {
        final int size = 128;

        IntArray a = new IntArray(size);

        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestParallelDimensions::forLoopOneD, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(10, a.get(i));
        }

    }

    public static void forLoop2D(IntArray a, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                a.set(i * size + j, 10);
            }
        }
    }

    @Test
    public void test2DParallel() throws TornadoExecutionPlanException {
        final int size = 128;

        IntArray a = new IntArray(size * size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestParallelDimensions::forLoop2D, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                assertEquals(10, a.get(i * size + j));
            }
        }
    }

    public static void forLoop3D(IntArray a, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                for (@Parallel int y = 0; y < size; y++) {
                    a.set((size * size * y) + (size * j) + i, 10);
                }
            }
        }
    }

    @Test
    public void test3DParallel() throws TornadoExecutionPlanException {
        final int size = 128;

        IntArray a = new IntArray(size * size * size);
        a.init(1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", TestParallelDimensions::forLoop3D, a, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int y = 0; y < size; y++) {
                    assertEquals(10, a.get((size * size * y) + (size * j) + i));
                }
            }
        }
    }

    public static void forLoop3DMap(IntArray a, IntArray b, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                for (@Parallel int y = 0; y < size; y++) {
                    int threeDindex = (size * size * y) + (size * j) + i;
                    a.set(threeDindex, b.get(threeDindex));
                }
            }
        }
    }

    @Test
    public void test3DParallelMap() throws TornadoExecutionPlanException {
        final int size = 128;

        IntArray a = new IntArray(size * size * size);
        IntArray b = new IntArray(size * size * size);

        a.init(1);
        b.init(110);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, b) //
                .task("t0", TestParallelDimensions::forLoop3DMap, a, b, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int y = 0; y < size; y++) {
                    assertEquals(110, a.get((size * size * y) + (size * j) + i));
                }
            }
        }
    }
}
