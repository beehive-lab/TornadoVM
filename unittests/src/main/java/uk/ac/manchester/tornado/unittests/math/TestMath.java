/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.math;

import static org.junit.Assert.assertArrayEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestMath extends TornadoTestBase {

    public static void testCos(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.cos(a[i]);
        }
    }

    public static void testLog(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.log(a[i]);
        }
    }

    public static void testSqrt(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.sqrt(a[i]);
        }
    }

    public static void testExp(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.exp(a[i]);
        }
    }

    public static void testExpDouble(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.exp(a[i]);
        }
    }

    public static void testExpLong(long[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (long) Math.exp(a[i]);
        }
    }

    public static void testExpFloat(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (float) Math.exp(a[i]);
        }
    }

    public static void testPow(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (float) Math.pow(a[i], 2);
        }
    }

    public static void testPowDouble(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (double) Math.pow(a[i], 2);
        }
    }

    public static void testAcos(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (Math.acos(a[i]));
        }
    }

    public static void testFloor(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (TornadoMath.floor(a[i]));
        }
    }

    public static void testAbs(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = Math.abs(a[i]);
        }
    }

    public static void testMin(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = Math.min(a[i], b[i]);
        }
    }

    public static void testMax(double[] a, double[] b, double[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = Math.max(a[i], b[i]);
        }
    }

    public static void testClamp(long[] a, long[] b) {
        long min = 1;
        long max = 10000;
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = TornadoMath.clamp(a[i], min, max);
        }
    }

    public static void testNegate(float[] a, float[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = -a[i];
        }
    }

    public static void testRemainder(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = b[i] % a[i];
        }
    }

    public static void testFMA(float[] a, float[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] + b[i] * a[i];
        }
    }

    public static void testFMA2(float[] a, double[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            b[i] = a[i] + b[i] * a[i];
        }
    }

    @Test
    public void testMathCos() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testCos, data).streamOut(data).execute();

        testCos(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    @Test
    public void testMathLog() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testLog, data).streamOut(data).execute();

        testLog(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    @Test
    public void testMathSqrt() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testSqrt, data).streamOut(data).execute();

        testSqrt(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    @Test
    public void testMathExp() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testExp, data).streamOut(data).execute();

        testExp(seq);

        assertArrayEquals(data, seq, 0.01);

    }

    @Test
    public void testMathExpFloat() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testExpFloat, data).streamOut(data).execute();

        testExpFloat(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testMathExpDouble() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (double) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testExpDouble, data).streamOut(data).execute();

        testExpDouble(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testMathPowDouble() {
        final int size = 8192;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testPow, data).streamOut(data).execute();

        testPow(seq);
        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testMathPow() {
        final int size = 8192;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testPowDouble, data).streamOut(data).execute();

        testPowDouble(seq);
        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testMathAbs() {
        final int size = 8192;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) -Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testAbs, data).streamOut(data).execute();

        testAbs(seq);
        assertArrayEquals(data, seq, 0.01f);
    }

    @Test
    public void testMathMin() {
        final int size = 8192;
        float[] a = new float[size];
        float[] b = new float[size];
        float[] c = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testMin, a, b, c).streamOut(c).execute();

        testMin(a, b, seq);
        assertArrayEquals(c, seq, 0.01f);
    }

    @Test
    public void testMathMax() {
        final int size = 8192;
        double[] a = new double[size];
        double[] b = new double[size];
        double[] c = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testMax, a, b, c).streamOut(c).execute();

        testMax(a, b, seq);
        assertArrayEquals(c, seq, 0.01f);
    }

    @Test
    public void testMathClamp() {
        Random r = new Random();
        final int size = 8192;
        long[] a = new long[size];
        long[] b = new long[size];
        long[] seq = new long[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = r.nextLong();
            b[i] = r.nextLong();
            seq[i] = b[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testClamp, a, b).streamOut(b).execute();

        testClamp(a, seq);
        assertArrayEquals(b, seq);
    }

    @Test
    public void testNegate() {
        Random r = new Random();
        final int size = 8192;
        float[] a = new float[size];
        float[] b = new float[size];
        float[] seq = new float[size];

        float min = -10000;
        float max = 10000;

        IntStream.range(0, size).parallel().forEach(i -> {
            a[i] = min + r.nextFloat() * (max - min);
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testNegate, a, b).streamOut(b).execute();

        testNegate(a, seq);
        assertArrayEquals(b, seq, 0.001f);
    }

    @Test
    public void testRem() {
        Random r = new Random();
        final int size = 8192;
        int[] a = new int[size];
        int[] b = new int[size];
        int[] seq = new int[size];

        IntStream.range(0, size).forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
            seq[i] = b[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testRemainder, a, b).streamOut(b).execute();

        testRemainder(a, seq);
        assertArrayEquals(b, seq);
    }

    @Test
    public void testFMA() {
        Random r = new Random();
        final int size = 8192;
        float[] a = new float[size];
        float[] b = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).forEach(i -> {
            a[i] = r.nextFloat();
            b[i] = r.nextFloat();
            seq[i] = b[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testFMA, a, b).streamOut(b).execute();

        testFMA(a, seq);

        assertArrayEquals(b, seq, 0.01f);
    }

    @Test
    public void testFMA2() {
        Random r = new Random();
        final int size = 8192;
        float[] a = new float[size];
        double[] b = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).forEach(i -> {
            a[i] = r.nextFloat();
            b[i] = r.nextFloat();
            seq[i] = b[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestMath::testFMA2, a, b).streamOut(b).execute();

        testFMA2(a, seq);

        assertArrayEquals(b, seq, 0.01f);
    }

}
