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
package uk.ac.manchester.tornado.unittests.reductions;

import org.junit.Test;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.stream.IntStream;

public class MultipleReductions extends TornadoTestBase {

    public static void test(int[] input, @Reduce int[] output1, @Reduce int[] output2) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output1[0] += input[i];
            // output2[0] += input[i];
        }
    }

    @Test
    public void test() {
        final int size = 128;
        int[] input = new int[size];
        int[] result1 = new int[] { 0 };
        int[] result2 = new int[] { 0 };

        IntStream.range(0, size).parallel().forEach(i -> {
            input[i] = i;
        });

        TaskSchedule task = new TaskSchedule("s0") //
                .streamIn(input) //
                .task("t0", MultipleReductions::test, input, result1, result2) //
                .streamOut(result1, result2); //

        task.execute();
    }

}
