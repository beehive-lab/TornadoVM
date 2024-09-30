/*
 * Copyright (c) 2020, 2022 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.arrays.TestNewArrays
 * </code>
 */
public class TestNewArrays extends TornadoTestBase {
    // CHECKSTYLE:OFF

    private static void vectorAdd(IntArray a, IntArray b, IntArray c) {
        // Array intentionally created inside the expression for allocation either in
        // constant memory or local memory (OpenCL).
        int[] idx = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43,
                44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89,
                90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128,
                129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165,
                166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202,
                203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239,
                240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255 };
        for (@Parallel int i = 0; i < idx.length; i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    private static void vectorAddComplexConditions(IntArray a, IntArray b, IntArray c) {
        // Array intentionally created inside the expression for allocation either in
        // constant memory or local memory (OpenCL).
        int[] idx = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43,
                44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89,
                90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128,
                129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165,
                166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202,
                203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239,
                240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255 };
        for (@Parallel int i = 0; i < idx.length; i++) {
            if (i % 2 == 0) {
                c.set(i, a.get(i) + b.get(i) + idx[i]);
            } else {
                c.set(i, a.get(i) + b.get(i));
            }
        }
    }

    public static void initializeToOne(IntArray a) {
        int[] testArray = new int[128];
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, 1);
            testArray[i] = 2;
        }
        a.set(0, a.get(0) + testArray[0]);
        a.set(125, a.get(125) + testArray[0]);
    }

    public static void initializeToOneParallelScope(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            float[] acc = new float[] { 0.0f, 0.0f, 0.0f };
            for (int j = 0; j < 256; j++) {
                if (j % 2 == 0) {
                    acc[2] = j;
                }
            }
            a.set(i, acc[2]);
        }
    }

    public static void initializeToOneParallelScopeComplex(FloatArray a) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            float[] acc = new float[] { 0.0f, 0.0f, 0.0f };
            for (int j = 0; j < 256; j++) {
                if (j % 2 == 0) {
                    acc[2] = j;
                }
            }
        }
    }

    public static void initializeToOneParallel(FloatArray a) {
        float[] testArray = new float[16];
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            if (i == 16) {
                testArray[0] = 2;
            } else if (i == 5) {
                testArray[1] = 3;
            }
            a.set(i, 1);
        }
        a.set(0, a.get(0) + testArray[0]);
    }

    private static void reductionAddFloats(FloatArray input, @Reduce FloatArray result) {
        result.set(0, 0.0f);
        float[] testFloatSum = new float[256];
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
            testFloatSum[i] = 1;
        }
        result.set(0, result.get(0) + testFloatSum[0]);
    }

    @Test
    public void testInitLargeArray() throws TornadoExecutionPlanException {
        int size = 256;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray c = new IntArray(size);

        IntArray sequentialResult = new IntArray(size);

        a.init(10);
        b.init(20);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestNewArrays::vectorAdd, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorAdd(a, b, sequentialResult);
        for (int i = 0; i < size; i++) {
            assertEquals(sequentialResult.get(i), c.get(i));
        }

    }

    @Test
    public void testInitLargeArrayBranches() throws TornadoExecutionPlanException {
        int size = 256;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        IntArray c = new IntArray(size);

        IntArray sequentialResult = new IntArray(size);

        a.init(10);
        b.init(20);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("t0", TestNewArrays::vectorAddComplexConditions, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        vectorAddComplexConditions(a, b, sequentialResult);
        for (int i = 0; i < size; i++) {
            assertEquals(sequentialResult.get(i), c.get(i));
        }
    }

    @Test
    public void testInitNewArrayNotParallel() throws TornadoExecutionPlanException {
        final int N = 128;
        IntArray data = new IntArray(N);
        IntArray dataSeq = new IntArray(N);

        Random r = new Random();
        IntStream.range(0, N).forEach(i -> {
            data.set(i, r.nextInt());
            dataSeq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.task("t0", TestNewArrays::initializeToOne, data);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        initializeToOne(dataSeq);

        for (int i = 0; i < N; i++) {
            assertEquals(dataSeq.get(i), data.get(i), 0.1);
        }
    }

    @Test
    public void testInitNewArrayInsideParallel() throws TornadoExecutionPlanException {
        final int N = 256;
        FloatArray data = new FloatArray(N);
        FloatArray dataSeq = new FloatArray(N);

        Random r = new Random();
        IntStream.range(0, N).forEach(i -> {
            data.set(i, r.nextFloat());
            dataSeq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.task("t0", TestNewArrays::initializeToOneParallelScope, data);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        initializeToOneParallelScope(dataSeq);

        for (int i = 0; i < N; i++) {
            assertEquals(dataSeq.get(i), data.get(i), 0.1);
        }
    }

    @Test
    public void testInitNewArrayInsideParallelWithComplexAccesses() throws TornadoExecutionPlanException {
        final int N = 256;
        FloatArray data = new FloatArray(N);
        FloatArray dataSeq = new FloatArray(N);

        Random r = new Random();
        IntStream.range(0, N).parallel().forEach(i -> {
            data.set(i, r.nextFloat());
            dataSeq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.task("t0", TestNewArrays::initializeToOneParallelScopeComplex, data);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        initializeToOneParallelScopeComplex(dataSeq);

        for (int i = 0; i < N; i++) {
            assertEquals(dataSeq.get(i), data.get(i), 0.1);
        }
    }

    @TornadoNotSupported
    public void testInitNewArrayParallel() throws TornadoExecutionPlanException {
        final int N = 128;
        FloatArray data = new FloatArray(N);
        FloatArray dataSeq = new FloatArray(N);

        Random r = new Random();
        IntStream.range(0, N).parallel().forEach(i -> {
            data.set(i, r.nextInt());
            dataSeq.set(i, data.get(i));
        });

        TaskGraph taskGraph = new TaskGraph("s0");
        assertNotNull(taskGraph);

        taskGraph.task("t0", TestNewArrays::initializeToOneParallel, data);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, data);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        initializeToOneParallel(dataSeq);

        for (int i = 0; i < N; i++) {
            assertEquals(dataSeq.get(i), data.get(i), 0.1);
        }
    }

    @TornadoNotSupported
    public void testIniNewtArrayWithReductions() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(1024);
        FloatArray result = new FloatArray(1);
        final int neutral = 0;

        result.init(neutral);

        Random r = new Random();
        IntStream.range(0, input.getSize()).sequential().forEach(i -> {
            input.set(i, r.nextFloat());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestNewArrays::reductionAddFloats, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        FloatArray sequential = new FloatArray(1);
        reductionAddFloats(input, sequential);

        // Check result
        assertEquals(sequential.get(0), result.get(0), 0.1f);
    }
    // CHECKSTYLE:ON
}
