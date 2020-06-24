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
package uk.ac.manchester.tornado.unittests.numpromotion;

import org.junit.Assert;
import org.junit.Test;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

import java.util.Random;
import java.util.stream.IntStream;

public class Inlining {

    public static int b2i(int v) {
        return v < 0 ? 256 + v : v;
    }

    public static int grey(int r, int g, int b) {
        return (29 * b2i(r) + 60 * b2i(g) + 11 * b2i(b)) / 100;
    }

    public static void rgbToGreyKernel(byte[] rgbBytes, int[] greyInts) {
        for (@Parallel int i = 0; i < greyInts.length; i++) {
            byte r = rgbBytes[i * 3];
            byte g = rgbBytes[i * 3 + 1];
            byte b = rgbBytes[i * 3 + 2];
            greyInts[i] = grey(r, g, b);
        }
    }

    public static void rgbToGreyKernelInt(int[] rgbBytes, int[] greyInts) {
        for (@Parallel int i = 0; i < greyInts.length; i++) {
            int r = rgbBytes[i * 3];
            int g = rgbBytes[i * 3 + 1];
            int b = rgbBytes[i * 3 + 2];
            greyInts[i] = grey(r, g, b);
        }
    }

    @Test
    public void test01() {

        final int size = 256;
        byte[] rgbBytes = new byte[size * 3];
        int[] greyInts = new int[size];
        int[] seq = new int[size];

        Random r = new Random();
        IntStream.range(0, rgbBytes.length).forEach(i -> {
            rgbBytes[i] = (byte) r.nextInt();
        });

        TaskSchedule ts = new TaskSchedule("foo");
        ts.streamIn(rgbBytes) //
                .task("grey", Inlining::rgbToGreyKernel, rgbBytes, greyInts)//
                .streamOut(greyInts) //
                .execute();

        rgbToGreyKernel(rgbBytes, seq);

        for (int i = 0; i < seq.length; i++) {
            Assert.assertEquals(seq[i], greyInts[i]);
        }

    }

    @Test
    public void test02() {
        final int size = 256;
        int[] rgbBytes = new int[size * 3];
        int[] greyInts = new int[size];
        int[] seq = new int[size];
        Random r = new Random();
        IntStream.range(0, rgbBytes.length).forEach(i -> {
            rgbBytes[i] = 1;
        });

        TaskSchedule ts = new TaskSchedule("foo");
        ts.streamIn(rgbBytes) //
                .task("grey", Inlining::rgbToGreyKernelInt, rgbBytes, greyInts)//
                .streamOut(greyInts) //
                .execute();

        rgbToGreyKernelInt(rgbBytes, seq);

        for (int i = 0; i < seq.length; i++) {
            Assert.assertEquals(seq[i], greyInts[i]);
        }

    }
}
