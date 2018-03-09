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

public class TestReductionsLongs extends TornadoTestBase {

    private static final int SIZE = 64;
    private static final int BIG_SIZE = 128;

    public static void reductionAnnotation(long[] input, @Reduce long[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    @Test
    public void testReductionAnnotation() {
        long[] input = new long[BIG_SIZE];
        long[] result = new long[1];

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            input[i] = 1;
        });

        //@formatter:off
		new TaskSchedule("s0")
			.streamIn(input)
			.task("t0", TestReductionsLongs::reductionAnnotation, input, result)
			.streamOut(result)
			.execute();
		//@formatter:on

        long[] sequential = new long[1];
        reductionAnnotation(input, sequential);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    public static void reductionSequentialSmall(long[] input, long[] result) {
        result[0] = 0;
        for (int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    @Test
    public void testSequentialReduction() {
        long[] input = new long[SIZE];
        long[] result = new long[1];

        Random r = new Random();

        IntStream.range(0, SIZE).parallel().forEach(i -> {
            input[i] = r.nextLong();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsLongs::reductionSequentialSmall, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        long[] sequential = new long[1];

        reductionSequentialSmall(input, sequential);

        assertEquals(sequential[0], result[0], 0.001f);
    }

}
