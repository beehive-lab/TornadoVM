/*
 * Copyright (c) 2020, 2022 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.codegen;

import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * How to test?
 *
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.codegen.CodeGen
 * </code>
 */
public class CodeGen extends TornadoTestBase {

    public static void cascadeKernel(int grayIntegralImage[], int imageWidth, int imageHeight, int resultsXY[]) {
        for (@Parallel int y = 0; y < imageHeight; y++) {
            for (@Parallel int x = 0; x < imageWidth; x++) {
                int gradient = grayIntegralImage[(y * imageWidth) + x];
            }
        }
    }

    public static void badCascadeKernel2() {
        for (@Parallel int id = 0; id < 100; id++) {
            boolean stillLooksLikeAFace = true;
            for (int stage = 0; (stillLooksLikeAFace || (stage < 100)); stage++) {
                for (int t = 0; t < id; t++) {
                    stillLooksLikeAFace = (t == 0);
                }
            }
        }
    }

    public static void badCascadeKernel3() {
        for (@Parallel int id = 0; id < 100; id++) {
            boolean stillLooksLikeAFace = true;
            for (int stage = 0; (stillLooksLikeAFace || (stage < 100)); stage++) {
                for (int t = 0; stillLooksLikeAFace && (t < id); t++) {
                    stillLooksLikeAFace = (t == 0);
                }
            }
        }
    }

    public static void badCascadeKernel4() {
        for (@Parallel int id = 0; id < 100; id++) {
            boolean stillLooksLikeAFace = true;
            for (int stage = 0; stillLooksLikeAFace && (stage < id); stage++) {
                for (int t = 0; t < id; t++) {
                    stillLooksLikeAFace = (t == 0);
                }
            }
        }
    }

    /*
     * The following test is not intended to execute in parallel. This test shows
     * more complex control flow, in which there is an exit block followed by a
     * merge to represent the break in the first if-condition.
     *
     */
    private static void breakStatement(int[] a) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == 5) {
                break;
            }
            a[i] += 5;
        }
        a[0] = 0;
    }

    @Test
    public void test01() {

        TaskGraph taskGraph = new TaskGraph("foo");

        int imageWidth = 512;
        int imageHeight = 512;
        int[] grayIntegralImage = new int[imageHeight * imageWidth];
        int[] resultsXY = new int[imageHeight * imageWidth];

        IntStream.range(0, imageHeight * imageHeight).forEach(x -> grayIntegralImage[x] = x);

        taskGraph.transferToDevice(DataTransferMode.FIRST_EXECUTION, grayIntegralImage) //
                .task("bar", CodeGen::cascadeKernel, grayIntegralImage, imageWidth, imageHeight, resultsXY) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, resultsXY);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();
    }

    private boolean isRunningOnCPU() {
        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        return device.getDeviceType() == TornadoDeviceType.CPU;
    }

    @Test
    public void test02() {
        if (isRunningOnCPU()) {
            return;
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", CodeGen::badCascadeKernel2);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withWarmUp();
    }

    @Test
    @Ignore
    public void test03() {
        if (isRunningOnCPU()) {
            return;
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", CodeGen::badCascadeKernel3);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withWarmUp();
    }

    @Test
    public void test04() {
        assertNotBackendOptimization(TornadoVMBackendType.SPIRV);
        if (isRunningOnCPU()) {
            return;
        }
        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", CodeGen::badCascadeKernel4);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withWarmUp();
    }

    @Test
    public void test05() {
        final int size = 8192;
        int[] a = new int[size];
        Arrays.fill(a, 10);
        a[12] = 5;
        int[] serial = Arrays.copyOf(a, a.length);
        breakStatement(serial);

        TaskGraph taskGraph = new TaskGraph("break") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("task", CodeGen::breakStatement, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertArrayEquals(serial, a);
    }

}
