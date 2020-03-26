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

package uk.ac.manchester.tornado.examples.reductions;

import java.util.ArrayList;
import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskSchedule;;

public class ReductionSequentialFPGA {

    private static void reductionAddFloats(float[] input, float[] result) {
        result[0] = 0.0f;
        for (int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    public void run(int size) {

        float[] input = new float[size];
        float[] result = new float[1];
        float[] seq = new float[1];

        Random r = new Random();
        IntStream.range(0, size).sequential().forEach(i -> {
            input[i] = r.nextFloat();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .task("t0", ReductionSequentialFPGA::reductionAddFloats, input, result)
            .streamOut(result);
        //@formatter:on

        ArrayList<Long> timers = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            long start = System.nanoTime();
            task.execute();
            long end = System.nanoTime();
            timers.add((end - start));
            System.out.println("Total Time: " + (end - start));

            if (i == 0) {
                reductionAddFloats(input, seq);
                if (Math.abs(seq[0] - result[0]) > 0.01) {
                    System.out.println("Result is wrong");
                }
            }
        }

        System.out.println("Median TotalTime: " + Stats.computeMedian(timers));
    }

    public static void main(String[] args) {
        int inputSize = 512;
        if (args.length > 0) {
            inputSize = Integer.parseInt(args[0]);
        }
        System.out.println("Size = " + inputSize);
        new ReductionSequentialFPGA().run(inputSize);
    }
}
