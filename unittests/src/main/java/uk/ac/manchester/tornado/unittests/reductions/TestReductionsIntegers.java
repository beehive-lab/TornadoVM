/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Authors: Juan Fumero
 *
 */

package uk.ac.manchester.tornado.unittests.reductions;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.api.Reduce;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestReductionsIntegers extends TornadoTestBase {

    public static final int SIZE = 64;
    public static final int BIG_SIZE = 128;

    /**
     * First approach: use annotations in the user code to identify the
     * reduction variables. This is a similar approach to OpenMP and OpenACC.
     * 
     * @param input
     * @param result
     */
    public static void reductionAnnotation(int[] input, @Reduce int[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    @Test
    public void testReductionAnnotation() {
        int[] input = new int[BIG_SIZE];
        int[] result = new int[1];

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            input[i] = 1;
        });

        //@formatter:off
		new TaskSchedule("s0")
			.streamIn(input)
			.task("t0", TestReductionsIntegers::reductionAnnotation, input, result)
			.streamOut(result)
			.execute();
		//@formatter:on

        int[] sequential = new int[1];
        reductionAnnotation(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    public static void reductionSequentialSmall(float[] input, float[] result) {
        result[0] = 0;
        for (int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    @Test
    public void testSequentialReduction() {
        float[] input = new float[SIZE];
        float[] result = new float[1];

        Random r = new Random();

        IntStream.range(0, SIZE).parallel().forEach(i -> {
            input[i] = r.nextFloat();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::reductionSequentialSmall, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        float[] sequential = new float[1];

        reductionSequentialSmall(input, sequential);

        assertEquals(sequential[0], result[0], 0.001f);
    }

    public static void reductionSequentialSmall2(int[] input, int[] result) {
        int acc = 0; // neutral element for the addition
        for (int i = 0; i < input.length; i++) {
            acc += input[i];
        }
        result[0] = acc;
    }

    @Test
    public void testSequentialReduction2() {
        int[] input = new int[SIZE * 2];
        int[] result = new int[1];

        Random r = new Random();

        IntStream.range(0, SIZE * 2).parallel().forEach(i -> {
            input[i] = r.nextInt();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::reductionSequentialSmall2, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        int[] sequential = new int[1];

        reductionSequentialSmall2(input, sequential);

        assertEquals(sequential[0], result[0], 0.001f);
    }

    public static void reductionSequentialBig(int[] input, @Reduce int[] result) {
        for (int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    @Test
    public void testSequentialReductionBig() {
        int[] input = new int[SIZE * 2];
        int[] result = new int[1];

        Random r = new Random();

        IntStream.range(0, SIZE * 2).parallel().forEach(i -> {
            input[i] = r.nextInt();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::reductionSequentialBig, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        int[] sequential = new int[1];

        reductionSequentialBig(input, sequential);

        assertEquals(sequential[0], result[0], 0.001f);
    }

    public static void reduction01(int[] a, @Reduce int[] result) {
        for (@Parallel int i = 0; i < a.length; i++) {
            result[0] += a[i];
        }
    }

    @Test
    public void testReduction01() {
        int[] input = new int[SIZE];
        int[] result = new int[1];

        Random r = new Random();

        IntStream.range(0, SIZE).parallel().forEach(i -> {
            input[i] = r.nextInt();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::reduction01, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        int[] sequential = new int[1];

        reduction01(input, sequential);

        assertEquals(sequential[0], result[0], 0.001f);
    }

    public static void mapReduce01(int[] a, int[] b, int[] c, @Reduce int[] result) {

        // map
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = a[i] * b[i];
        }

        // reduction
        for (@Parallel int i = 0; i < a.length; i++) {
            result[0] += c[i];
        }
    }

    @Test
    public void testMapReduce() {
        int[] a = new int[SIZE * 2];
        int[] b = new int[SIZE * 2];
        int[] c = new int[SIZE * 2];
        int[] result = new int[SIZE * 2];

        Random r = new Random();

        IntStream.range(0, SIZE * 2).parallel().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a)
            .task("t0", TestReductionsIntegers::mapReduce01, a, b, c, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        int[] sequential = new int[SIZE * 2];

        mapReduce01(a, b, c, sequential);

        assertEquals(sequential[0], result[0], 0.001f);
    }

    // Reusing result, sequential
    public static void mapReduce02(int[] a, int[] b, @Reduce int[] result) {

        // map
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] * b[i];
        }

        // reduction
        for (int i = 0; i < result.length; i++) {
            result[0] += result[i];
        }
    }

    @Test
    public void testMapReduce2() {
        int[] a = new int[SIZE * 2];
        int[] b = new int[SIZE * 2];
        int[] result = new int[SIZE * 2];

        Random r = new Random();

        IntStream.range(0, SIZE * 2).parallel().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a)
            .task("t0", TestReductionsIntegers::mapReduce02, a, b, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        int[] sequential = new int[SIZE * 2];

        mapReduce02(a, b, sequential);

        assertEquals(sequential[0], result[0], 0.001f);
    }

    // Reusing result, in Parallel
    public static void mapReduce03(int[] a, int[] b, @Reduce int[] result) {

        // map
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = a[i] * b[i];
        }

        // reduction
        for (@Parallel int i = 0; i < result.length; i++) {
            result[0] += result[i];
        }
    }

    @Test
    public void testMapReduce3() {
        int[] a = new int[SIZE * 2];
        int[] b = new int[SIZE * 2];
        int[] result = new int[SIZE * 2];

        Random r = new Random();

        IntStream.range(0, SIZE * 2).parallel().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        //@formatter:off
	        new TaskSchedule("s0")
	            .streamIn(a)
	            .task("t0", TestReductionsIntegers::mapReduce03, a, b, result)
	            .streamOut(result)
	            .execute();
	        //@formatter:on

        int[] sequential = new int[SIZE * 2];

        mapReduce03(a, b, sequential);

        assertEquals(sequential[0], result[0], 0.001f);
    }

    public static void testThreadSchuler(int[] a, int[] b, int[] result) {

        // map
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = a[i] * b[i];
        }

        // map
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = result[i] * b[i];
        }

    }

    @Test
    public void testThreadSchuler() {
        int[] a = new int[SIZE * 2];
        int[] b = new int[SIZE * 2];
        int[] result = new int[SIZE * 2];

        Random r = new Random();

        IntStream.range(0, SIZE * 2).parallel().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        //@formatter:off
		        new TaskSchedule("s0")
		            .streamIn(a)
		            .task("t0", TestReductionsIntegers::testThreadSchuler, a, b, result)
		            .streamOut(result)
		            .execute();
		        //@formatter:on

        int[] sequential = new int[SIZE * 2];

        testThreadSchuler(a, b, sequential);

        assertEquals(sequential[0], result[0], 0.001f);
    }

    public static void testThreadSchuler2(int[] a, int[] b, int[] result) {
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = a[i] * b[i];
        }
    }

    @Test
    public void testThreadSchuler2() {
        int[] a = new int[SIZE * 2];
        int[] b = new int[SIZE * 2];
        int[] result = new int[SIZE * 2];

        Random r = new Random();

        IntStream.range(0, SIZE * 2).parallel().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        //@formatter:off
		        new TaskSchedule("s0")
		            .streamIn(a)
		            .task("t0", TestReductionsIntegers::testThreadSchuler2, a, b, result)
		            .streamOut(result)
		            .execute();
		        //@formatter:on

        int[] sequential = new int[SIZE * 2];

        testThreadSchuler2(a, b, sequential);

        assertEquals(sequential[0], result[0], 0.001f);
    }

    public static void testThreadLoop(int[] a, int[] result) {
        for (@Parallel int i = 1; i < a.length; i++) {
            result[i] = a[i] * a[i];
        }
    }

    @Test
    public void testThreadLoop() {
        int[] a = new int[SIZE * 2];
        int[] b = new int[SIZE * 2];
        int[] result = new int[SIZE * 2];

        Random r = new Random();

        IntStream.range(0, SIZE * 2).parallel().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        //@formatter:off
		        new TaskSchedule("s0")
		            .streamIn(a)
		            .task("t0", TestReductionsIntegers::testThreadLoop, a, result)
		            .streamOut(result)
		            .execute();
		        //@formatter:on

        int[] sequential = new int[SIZE * 2];

        testThreadLoop(a, sequential);

        for (int i = 2; i < SIZE * 2; i++) {
            assertEquals(sequential[i], result[i]);
        }
    }

    public static void reductionMultiplication(int[] input, @Reduce int[] result, int[] neutral) {
        // neutral
        result[0] = neutral[0];
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = result[0] * input[i];
        }
    }

    @Test
    public void testReductionMultiplication() {
        int[] input = new int[BIG_SIZE];
        int[] result = new int[1];

        input[0] = 5;
        input[1] = 2;
        for (int i = 2; i < input.length; i++) {
            input[i] = 1;
        }

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::reductionMultiplication, input, result, new int[] {1})
            .streamOut(result)
            .execute();
        //@formatter:on

        int[] sequential = new int[1];
        reductionMultiplication(input, sequential, new int[] { 1 });
        System.out.println("[I] " + sequential[0]);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    public static void reductionSub(int[] input, @Reduce int[] result, int[] neutral) {
        result[0] = neutral[0];
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] -= input[i];
        }
    }

    @Test
    public void testReductionSub() {
        int[] input = new int[BIG_SIZE];
        int[] result = new int[1];

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            input[i] = 2;
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::reductionSub, input, result, new int[] {0})
            .streamOut(result)
            .execute();
        //@formatter:on

        int[] sequential = new int[1];
        reductionSub(input, sequential, new int[] { 0 });

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    public static void reductionSubRandom(int[] input, @Reduce int[] result, int[] neutral) {
        result[0] = neutral[0];
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] -= input[i];
        }
    }

    @Test
    public void testReductionSubRandom() {
        int[] input = new int[BIG_SIZE];
        int[] result = new int[1];

        Random r = new Random();

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            input[i] = r.nextInt(100);
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::reductionSubRandom, input, result, new int[] {0})
            .streamOut(result)
            .execute();
        //@formatter:on

        int[] sequential = new int[1];
        reductionSubRandom(input, sequential, new int[] { 0 });

        System.out.println("VALUE: " + sequential[0]);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

}
