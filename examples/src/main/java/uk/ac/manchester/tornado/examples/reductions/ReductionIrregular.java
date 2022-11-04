/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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

import java.util.ArrayList;
import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado -m tornado.examples/uk.ac.manchester.tornado.examples.reductions.ReductionIrregular
 * </code>
 *
 */
public class ReductionIrregular {

    private static void reduceFloats(float[] input, @Reduce float[] output) {
        for (@Parallel int i = 0; i < input.length; i++) {
            output[0] += input[i];
        }
    }

    private void run(final int inputSize) {

        float[] input = new float[inputSize];
        float[] result = new float[] { 0.0f };
        Random r = new Random(101);

        TaskGraph task = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)//
                .task("t0", ReductionIrregular::reduceFloats, input, result)//
                .transferToHost(result);

        ArrayList<Long> timers = new ArrayList<>();
        for (int i = 0; i < ConfigurationReduce.MAX_ITERATIONS; i++) {

            IntStream.range(0, inputSize).parallel().forEach(idx -> {
                input[idx] = r.nextFloat();
            });
            float[] sequential = new float[1];
            reduceFloats(input, sequential);

            long start = System.nanoTime();
            task.execute();
            long end = System.nanoTime();
            timers.add((end - start));

            if (Math.abs(sequential[0] - result[0]) > 0.1f) {
                System.out.println("Result is not correct - iteration: " + i + " expected: " + sequential[0] + " but found: " + result[0]);
            } else {
                System.out.println("Iteration: " + i + " is correct");
            }
        }

        System.out.println("Median TotalTime: " + Stats.computeMedian(timers));

    }

    public static void main(String[] args) {
        int inputSize = 2000;
        if (args.length > 0) {
            inputSize = Integer.parseInt(args[0]);
        }
        System.out.println("Size = " + inputSize);
        new ReductionIrregular().run(inputSize);
    }
}
