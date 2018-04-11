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

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.api.Reduce;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestReductionsFloats extends TornadoTestBase {

    public static final int SIZE = 512;

    public static void reductionAddFloats(float[] input, @Reduce float[] result) {
        result[0] = 0.0f;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    @Test
    public void testSumFloats() {
        float[] input = new float[SIZE];
        float[] result = new float[32];

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input[i] = r.nextFloat();
        });

        //@formatter:off
		new TaskSchedule("s0")
			.streamIn(input)
			.task("t0", TestReductionsFloats::reductionAddFloats, input, result)
			.streamOut(result)
			.execute();
		//@formatter:on

        // Final result
        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }

        // Final result
        for (int i = 1; i < numGroups; i++) {
            result[0] += result[i];
        }

        float[] sequential = new float[1];
        reductionAddFloats(input, sequential);

        System.out.println(Arrays.toString(result));

        // Check result
        assertEquals(sequential[0], result[0], 0.001f);
    }

    public static void multiplyFloats(float[] input, @Reduce float[] result) {
        result[0] = 1.0f;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] *= input[i];
        }
    }

    @Test
    public void testMultFloats() {
        float[] input = new float[SIZE];
        float[] result = new float[32];

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            input[i] = 1.0f;
        });

        input[0] = r.nextFloat();
        input[10] = r.nextFloat();
        input[11] = r.nextFloat();

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsFloats::multiplyFloats, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }

        // Final result
        for (int i = 1; i < numGroups; i++) {
            result[0] *= result[i];
        }

        float[] sequential = new float[1];
        multiplyFloats(input, sequential);

        System.out.println(Arrays.toString(result));

        // Check result
        assertEquals(sequential[0], result[0], 0.001f);
    }
}
