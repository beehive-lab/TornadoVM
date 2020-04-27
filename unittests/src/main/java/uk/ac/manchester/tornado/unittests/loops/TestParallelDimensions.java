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
package uk.ac.manchester.tornado.unittests.loops;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestParallelDimensions extends TornadoTestBase {

    public static void forLoopOneD(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 10;
        }
    }

    @Test
    public void test1DParallel() {
        final int size = 128;

        int[] a = new int[size];

        Arrays.fill(a, 1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestParallelDimensions::forLoopOneD, a)
                .streamOut(a)
                .execute();
        //@formatter:on

        for (int i = 0; i < a.length; i++) {
            assertEquals(10, a[i]);
        }

    }

    public static void forLoop2D(int[] a, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                a[i * size + j] = 10;
            }
        }
    }

    @Test
    public void test2DParallel() {
        final int size = 128;

        int[] a = new int[size * size];
        Arrays.fill(a, 1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestParallelDimensions::forLoop2D, a, size)
                .streamOut(a)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                assertEquals(10, a[i * size + j]);
            }
        }
    }

    public static void forLoop3D(int[] a, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                for (@Parallel int y = 0; y < size; y++) {
                    a[(size * size * y) + (size * j) + i] = 10;
                }
            }
        }
    }

    @Test
    public void test3DParallel() {
        final int size = 128;

        int[] a = new int[size * size * size];
        Arrays.fill(a, 1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestParallelDimensions::forLoop3D, a, size)
                .streamOut(a)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int y = 0; y < size; y++) {
                    assertEquals(10, a[(size * size * y) + (size * j) + i]);
                }
            }
        }
    }

    public static void forLoop3DMap(int[] a, int[] b, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                for (@Parallel int y = 0; y < size; y++) {
                    int threeDindex = (size * size * y) + (size * j) + i;
                    a[threeDindex] = b[threeDindex];
                }
            }
        }
    }

    @Test
    public void test3DParallelMap() {
        final int size = 128;

        int[] a = new int[size * size * size];
        int[] b = new int[size * size * size];

        Arrays.fill(a, 1);
        Arrays.fill(b, 110);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestParallelDimensions::forLoop3DMap, a, b, size)
                .streamOut(a)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int y = 0; y < size; y++) {
                    assertEquals(110, a[(size * size * y) + (size * j) + i]);
                }
            }
        }
    }
}
