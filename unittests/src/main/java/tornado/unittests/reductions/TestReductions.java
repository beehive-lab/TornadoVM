/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science,
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: Juan Fumero
 *
 */

package tornado.unittests.reductions;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;
import tornado.unittests.common.TornadoTestBase;

public class TestReductions extends TornadoTestBase {

	public static final int SIZE = 64;

	public static void reductionSequentialSmall(float[] input, float[] result) {
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
            .task("t0", TestReductions::reductionSequentialSmall, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

		float[] sequential = new float[1];

		reductionSequentialSmall(input, sequential);

		assertEquals(sequential[0], result[0], 0.001f);
	}

	public static void reductionSequentialSmall2(int[] input, int[] result) {
		int acc = 0;
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
            .task("t0", TestReductions::reductionSequentialSmall2, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

		int[] sequential = new int[1];

		reductionSequentialSmall2(input, sequential);

		assertEquals(sequential[0], result[0], 0.001f);
	}

	public static void reductionSequentialBig(int[] input, int[] result) {
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
            .task("t0", TestReductions::reductionSequentialBig, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

		int[] sequential = new int[1];

		reductionSequentialBig(input, sequential);

		assertEquals(sequential[0], result[0], 0.001f);
	}

	public static void reduction01(float[] a, float[] result) {
		for (@Parallel int i = 0; i < a.length; i++) {
			result[0] += a[i];
		}
	}

	@Test
	public void testReduction01() {
		float[] input = new float[SIZE];
		float[] result = new float[1];

		Random r = new Random();

		IntStream.range(0, SIZE).parallel().forEach(i -> {
			input[i] = r.nextFloat();
		});

		//@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductions::reduction01, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

		float[] sequential = new float[1];

		reduction01(input, sequential);

		assertEquals(sequential[0], result[0], 0.001f);
	}

}
