/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class CodeGen extends TornadoTestBase {

    public static void cascadeKernel(int grayIntegralImage[], int imageWidth, int imageHeight, int resultsXY[]) {
        for (@Parallel int y = 0; y < imageHeight; y++) {
            for (@Parallel int x = 0; x < imageWidth; x++) {
                int gradient = grayIntegralImage[(y * imageWidth) + x];
            }
        }
    }

    @Test
    public void test01() {

        TaskSchedule ts = new TaskSchedule("foo");

        int imageWidth = 512;
        int imageHeight = 512;
        int[] grayIntegralImage = new int[imageHeight * imageWidth];
        int[] resultsXY = new int[imageHeight * imageWidth];

        IntStream.range(0, imageHeight * imageHeight).forEach(x -> grayIntegralImage[x] = x);

        ts.task("bar", CodeGen::cascadeKernel, grayIntegralImage, imageWidth, imageHeight, resultsXY) //
                .streamOut(resultsXY);

        ts.execute();
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

    private boolean isRunningOnCPU() {
        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(0);
        return device.getDeviceType() == TornadoDeviceType.CPU;
    }

    @Test
    public void test02() {
        if (isRunningOnCPU()) {
            return;
        }
        TaskSchedule ts = new TaskSchedule("s0") //
                .task("t0", CodeGen::badCascadeKernel2);
        ts.warmup();
    }

    @Test
    @Ignore
    public void test03() {
        if (isRunningOnCPU()) {
            return;
        }
        TaskSchedule ts = new TaskSchedule("s0") //
                .task("t0", CodeGen::badCascadeKernel3);
        ts.warmup();
    }

    @Test
    public void test04() {
        if (isRunningOnCPU()) {
            return;
        }
        TaskSchedule ts = new TaskSchedule("s0") //
                .task("t0", CodeGen::badCascadeKernel4);
        ts.warmup();
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
    public void test05() {
        final int size = 8192;
        int[] a = new int[size];
        Arrays.fill(a, 10);
        a[12] = 5;
        int[] serial = Arrays.copyOf(a, a.length);
        breakStatement(serial);

        new TaskSchedule("break") //
                .task("task", CodeGen::breakStatement, a) //
                .streamOut(a) //
                .execute(); //

        assertArrayEquals(serial, a);
    }

}
