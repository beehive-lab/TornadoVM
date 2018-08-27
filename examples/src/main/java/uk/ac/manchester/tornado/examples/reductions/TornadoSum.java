/*
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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

package uk.ac.manchester.tornado.examples.reductions;

import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;

public class TornadoSum {

    public static void reductionAnnotation(int[] input, @Reduce int[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    public static void testReductionAnnotation() {
        int[] input = new int[128];
        int[] result = new int[1];

        IntStream.range(0, 128).parallel().forEach(i -> {
            input[i] = 1;
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TornadoSum::reductionAnnotation, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        int[] sequential = new int[1];
        reductionAnnotation(input, sequential);

    }

    public static void main(String[] args) {
        testReductionAnnotation();
    }
}
