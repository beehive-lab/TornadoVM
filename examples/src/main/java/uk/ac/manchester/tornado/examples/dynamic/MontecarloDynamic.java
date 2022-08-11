/*
 * Copyright (c) 2013-2022, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.examples.dynamic;

import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;

/**
 * Montecarlo algorithm to approximate the PI value. This version has been
 * adapted from Marawacc test-suite.
 *
 */
public class MontecarloDynamic {

    public static void computeMontecarlo(float[] output) {
        for (@Parallel int j = 0; j < output.length; j++) {
            long seed = j;
            // generate a pseudo random number (you do need it twice)
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);

            // this generates a number between 0 and 1 (with an awful entropy)
            float x = (seed & 0x0FFFFFFF) / 268435455f;

            // repeat for y
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            float y = (seed & 0x0FFFFFFF) / 268435455f;

            float dist = TornadoMath.sqrt(x * x + y * y);
            if (dist <= 1.0f) {
                output[j] = 1.0f;
            } else {
                output[j] = 0.0f;
            }
        }
    }

    public static void montecarlo(final int size, final String executionType, final int iterations) {

        float[] output = new float[size];
        float[] seq = new float[size];
        long start,end;

        long startInit = System.nanoTime();
        // @formatter:off
        TaskGraph s0 = new TaskGraph("s0")
                .task("t0", MontecarloDynamic::computeMontecarlo, output)
                .streamOut(output);
        // @formatter:on
        long stopInit = System.nanoTime();
        System.out.println("Initialization time:  " + (stopInit - startInit) + " ns" + "\n");

        for (int i = 0; i < iterations; i++) {
            switch (executionType) {
                case "performance":
                    start = System.nanoTime();
                    s0.executeWithProfilerSequential(Policy.PERFORMANCE);
                    end = System.nanoTime();
                    break;
                case "end":
                    start = System.nanoTime();
                    s0.executeWithProfilerSequential(Policy.END_2_END);
                    end = System.nanoTime();
                    break;
                case "sequential":
                    System.gc();
                    start = System.nanoTime();
                    computeMontecarlo(output);
                    end = System.nanoTime();
                    break;
                default:
                    start = System.nanoTime();
                    s0.execute();
                    end = System.nanoTime();
            }
            System.out.println("Total time:  " + (end - start) + " ns" + "\n");
        }

        float sum = 0;
        for (int j = 0; j < size; j++) {
            sum += output[j];
        }
        sum *= 4;
        System.out.println("Pi value (TornadoVM) : " + (sum / size));

        computeMontecarlo(seq);

        sum = 0;
        for (int j = 0; j < size; j++) {
            sum += seq[j];
        }
        sum *= 4;

        System.out.println("Pi value (Sequential) : " + (sum / size));

    }

    public static void main(String[] args) {
        System.out.println("Montecarlo Computation");

        if (args.length < 3) {
            System.out.println("Usage: <elements> <mode:performance|end|sequential> <iterations>");
            System.exit(-1);
        }
        int inputSize = Integer.parseInt(args[0]);
        String executionType = args[1];
        int iterations = Integer.parseInt(args[2]);
        montecarlo(inputSize, executionType, iterations);
    }
}
