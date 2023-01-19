/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.unittests.fails;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.collections.types.Matrix2DFloat;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test to check TornadoVM is able to bailout to the Java sequential
 * implementation if there are errors during optimizations phases, code
 * generation, or runtime.
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.fails.CodeFail
 * </code>
 */
public class CodeFail extends TornadoTestBase {

    /**
     * This case is not failing anymore. This stresses the local memory allocator.
     */
    public static void foo(float[] a) {
        float[] x = new float[a.length % 10];
        for (@Parallel int i = 0; i < x.length; i++) {
            a[i] = a[i] * a[i];
        }
    }

    @Test
    public void codeFail01() {

        float[] a = new float[1000];
        float[] b = new float[1000];
        Random r = new Random();
        IntStream.range(0, a.length).forEach(i -> {
            a[i] = r.nextFloat();
            b[i] = a[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0");

        taskGraph.task("t0", CodeFail::foo, a) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();
    }

    /**
     * Object allocation is not supported. This test provokes to bailout to the Java
     * sequential implementation.
     */
    public static void bar(float[] a) {
        Matrix2DFloat f = new Matrix2DFloat(256, 256); // Allocation here
        for (@Parallel int i = 0; i < 256; i++) {
            for (@Parallel int j = 0; j < 256; j++) {
                f.set(i, j, 10);
            }
        }
    }

    @Test
    public void codeFail02() {
        float[] a = new float[1000];
        float[] b = new float[1000];
        Random r = new Random();
        IntStream.range(0, a.length).forEach(i -> {
            a[i] = r.nextFloat();
            b[i] = a[i];
        });

        TaskGraph taskGraph = new TaskGraph("s0");

        taskGraph.task("t0", CodeFail::bar, a) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();
    }

    /**
     * Fusion of multiple reductions is not currently supported. This test provokes
     * to bailout to the Java sequential implementation.
     */
    public static void zoo(int[] input, @Reduce int[] output1, @Reduce int[] output2) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output1[0] += input[i];
            output2[0] += input[i];
        }
    }

    @Test(expected = TornadoBailoutRuntimeException.class)
    public void codeFail03() {
        final int size = 128;
        int[] input = new int[size];
        int[] result1 = new int[] { 0 };
        int[] result2 = new int[] { 0 };

        IntStream.range(0, size).parallel().forEach(i -> {
            input[i] = i;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", CodeFail::zoo, input, result1, result2) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result1, result2); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();
    }
}
