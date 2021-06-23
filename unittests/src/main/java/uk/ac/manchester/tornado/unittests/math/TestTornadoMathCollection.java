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
package uk.ac.manchester.tornado.unittests.math;

import static org.junit.Assert.assertArrayEquals;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestTornadoMathCollection extends TornadoTestBase {
    public static void testTornadoCos(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.floatCos(a[i]);
        }
    }

    public static void testTornadoSin(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.floatSin(a[i]);
        }
    }

    public static void testTornadoMin(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.min(a[i], 1);
        }
    }

    public static void testTornadoMax(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.max(a[i], 10);
        }
    }

    public static void testTornadoSqrt(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.sqrt(a[i]);
        }
    }

    public static void testTornadoExp(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.exp(a[i]);
        }
    }

    public static void testTornadoExp(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.exp(a[i]);
        }
    }

    public static void testTornadoClamp(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.clamp(a[i], 10, 20);
        }
    }

    public static void testTornadoFract(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.fract(a[i]);
        }
    }

    public static void testTornadoLog(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.log(a[i]);
        }
    }

    public static void testTornadoLog(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.log(a[i]);
        }
    }

    public static void testTornadoLog2(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.log2(a[i]);
        }
    }

    public static void testTornadoPI(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = TornadoMath.PI();
        }
    }

    @Test
    public void testTornadoMathCos() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestTornadoMathCollection::testTornadoCos, data).streamOut(data).execute();

        testTornadoCos(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathSin() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestTornadoMathCollection::testTornadoSin, data).streamOut(data).execute();

        testTornadoSin(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathMin() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestTornadoMathCollection::testTornadoMin, data).streamOut(data).execute();

        testTornadoMin(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathMax() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestTornadoMathCollection::testTornadoMax, data).streamOut(data).execute();

        testTornadoMax(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathSqrt() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestTornadoMathCollection::testTornadoSqrt, data).streamOut(data).execute();

        testTornadoSqrt(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathExpDouble() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestTornadoMathCollection::testTornadoExp, data).streamOut(data).execute();

        testTornadoExp(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathExpFloat() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestTornadoMathCollection::testTornadoExp, data).streamOut(data).execute();

        testTornadoExp(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathClamp() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestTornadoMathCollection::testTornadoClamp, data).streamOut(data).execute();

        testTornadoClamp(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathFract() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestTornadoMathCollection::testTornadoFract, data).streamOut(data).execute();

        testTornadoFract(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathLog2() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestTornadoMathCollection::testTornadoLog2, data).streamOut(data).execute();

        testTornadoLog2(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathLogDouble() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestTornadoMathCollection::testTornadoLog, data).streamOut(data).execute();

        testTornadoLog(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathLogFloat() {
        final int size = 128;
        float[] data = new float[size];
        float[] seq = new float[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestTornadoMathCollection::testTornadoLog, data).streamOut(data).execute();

        testTornadoLog(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

    @Test
    public void testTornadoMathPI() {
        final int size = 128;
        double[] data = new double[size];
        double[] seq = new double[size];

        IntStream.range(0, size).parallel().forEach(i -> {
            data[i] = (float) Math.random();
            seq[i] = data[i];
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        s0.task("t0", TestTornadoMathCollection::testTornadoPI, data).streamOut(data).execute();

        testTornadoPI(seq);

        assertArrayEquals(data, seq, 0.01f);

    }

}
