/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.unittests.reductions;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestReductionsAutomatic extends TornadoTestBase {

    @Test
    public void testIrregularSize01() {

        int numProcessors = Runtime.getRuntime().availableProcessors();
        final int size = 8192;

        int[] input = new int[size];
        int partitions = size / 128;
        int[] result = new int[partitions];

        final int neutral = 0;
        Arrays.fill(result, neutral);

        Random r = new Random();
        IntStream.range(0, size).parallel().forEach(i -> {
            // input[i] = r.nextInt(100);
            input[i] = 2;
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::reductionAnnotation, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        System.out.println(Arrays.toString(result));

        int[] sequential = new int[1];
        TestReductionsIntegers.reductionAnnotation(input, sequential);

        // Final result
        for (int i = 1; i < result.length; i++) {
            result[0] += result[i];
        }

        // Check result
        assertEquals(sequential[0], result[0]);
    }

}
