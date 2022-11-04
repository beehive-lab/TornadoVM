/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
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

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.reductions.MultipleReductions
 * </code>
 */
public class MultipleReductions extends TornadoTestBase {

    /**
     * Check multiple-reduce parameters can generate a correct OpenCL kernel. Note
     * that output2 variable is not used, but passed. This stresses the analysis
     * phase when using reductions, even if it is not used.
     *
     * @param input
     *            input data
     * @param output1
     *            reduce variable 1
     * @param output2
     *            reduce variable 2
     */
    public static void test(int[] input, @Reduce int[] output1, @Reduce int[] output2) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output1[0] += input[i];
        }
    }

    /**
     * Check if TornadoVM can generate OpenCL code the the input expression. Note
     * that fusion of multiple reductions is not supported yet.
     */
    @Test
    public void test() {
        final int size = 128;
        int[] input = new int[size];
        int[] result1 = new int[] { 0 };
        int[] result2 = new int[] { 0 };

        IntStream.range(0, size).parallel().forEach(i -> {
            input[i] = i;
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", MultipleReductions::test, input, result1, result2) //
                .transferToHost(result1, result2); //

        taskGraph.execute();
    }
}
