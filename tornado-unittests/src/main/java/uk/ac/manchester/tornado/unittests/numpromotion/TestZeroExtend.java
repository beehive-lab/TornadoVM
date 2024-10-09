/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.numpromotion;

import org.junit.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;

import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 /**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.numpromotion.TestZeroExtend
 * </code>
 */

public class TestZeroExtend {
    // CHECKSTYLE:OFF

    public static void narrowByte(ByteArray a, IntArray result, int size) {
        for(@Parallel int i = 0; i < size; i++) {
            result.set(i, a.get(i) & 0xFF);
        }
    }

    public static void narrowShort(ShortArray a, LongArray result, int size) {
        for(@Parallel int i = 0; i < size; i++) {
            result.set(i, a.get(i) & 0xFFFF);
        }
    }

    public static void narrowInt(IntArray a, LongArray result, int size) {
        for(@Parallel int i = 0; i < size; i++) {
            result.set(i, a.get(i) & 0xFFFFFFFFL);
        }
    }

    @Test
    public void testByte() throws TornadoExecutionPlanException {
        Random r = new Random();
        int size = 1024;

        ByteArray a = new ByteArray(size);
        for(int i = 0; i < size/2; i++) {
            a.set(i, (byte) (128 + r.nextInt(128)));
            a.set(i+size/2, (byte) (r.nextInt(128)));
        }

        IntArray expected = new IntArray(size);
        IntArray result = new IntArray(size);

        expected.init(0);
        result.init(0);

        TaskGraph graph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, result)
                .task("t0", TestZeroExtend::narrowByte, a, result, size)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try(TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
            narrowByte(a, expected, size);
        }

        for (int i = 0; i < expected.getSize(); i++) {
            assertEquals(expected.get(i), result.get(i));
        }
    }

    @Test
    public void testShort() throws TornadoExecutionPlanException {
        Random r = new Random();
        int size = 1024;

        ShortArray a = new ShortArray(size);

        for(int i = 0; i < size/2; i++) {
            a.set(i, (short) (Short.MAX_VALUE + (short) r.nextInt(Short.MAX_VALUE)));
            a.set(i+size/2, (short) (r.nextInt(Short.MAX_VALUE)));
        }

        LongArray expected = new LongArray(size);
        LongArray result = new LongArray(size);
        expected.init(0);
        result.init(0);

        TaskGraph graph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, result)
                .task("t0", TestZeroExtend::narrowShort, a, result, size)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try(TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
            narrowShort(a, expected, size);
        }

        for (int i = 0; i < expected.getSize(); i++) {
            assertEquals(expected.get(i), result.get(i));
        }
    }

    @Test
    public void testInt() throws TornadoExecutionPlanException {
        Random r = new Random();
        int size = 1024;

        IntArray a = new IntArray(size);

        for(int i = 0; i < size/2; i++) {
            a.set(i, Integer.MAX_VALUE + r.nextInt(Integer.MAX_VALUE));
            a.set(i+size/2, r.nextInt(Integer.MAX_VALUE));
        }

        LongArray expected = new LongArray(size);
        LongArray result = new LongArray(size);
        expected.init(0);
        result.init(0);

        TaskGraph graph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, result)
                .task("t0", TestZeroExtend::narrowInt, a, result, size)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = graph.snapshot();
        try(TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
            narrowInt(a, expected, size);
        }

        for (int i = 0; i < expected.getSize(); i++) {
            assertEquals(expected.get(i), result.get(i));
        }
    }
    // CHECKSTYLE:ON
}
