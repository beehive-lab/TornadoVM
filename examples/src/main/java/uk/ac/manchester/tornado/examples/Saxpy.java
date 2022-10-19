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
package uk.ac.manchester.tornado.examples;

import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.examples.common.Messages;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado -m tornado.examples/uk.ac.manchester.tornado.examples.Saxpy
 * </code>
 *
 */
public class Saxpy {

    public static void saxpy(float alpha, float[] x, float[] y) {
        for (@Parallel int i = 0; i < y.length; i++) {
            y[i] = alpha * x[i];
        }
    }

    public static void main(String[] args) {
        int numElements = 512;

        if (args.length > 0) {
            numElements = Integer.parseInt(args[0]);
        }

        final float alpha = 2f;

        final float[] x = new float[numElements];
        final float[] y = new float[numElements];

        IntStream.range(0, numElements).parallel().forEach(i -> x[i] = 450);

        TaskGraph s0 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .task("t0", Saxpy::saxpy, alpha, x, y)//
                .transferToHost(y);

        s0.executeWithProfilerSequentialGlobal(Policy.PERFORMANCE);

        numElements = 512 * 2;

        final float[] a = new float[numElements];
        final float[] b = new float[numElements];

        IntStream.range(0, numElements).parallel().forEach(i -> a[i] = 450);

        TaskGraph s1 = new TaskGraph("s1").task("t0", Saxpy::saxpy, alpha, a, b).transferToHost(a);

        s1.executeWithProfilerSequentialGlobal(Policy.PERFORMANCE);

        s1.executeWithProfilerSequentialGlobal(Policy.PERFORMANCE);

        boolean wrongResult = false;
        for (int i = 0; i < y.length; i++) {
            if (Math.abs(y[i] - (alpha * x[i])) > 0.01) {
                wrongResult = true;
                break;
            }
        }
        if (!wrongResult) {
            System.out.println(Messages.CORRECT);
        } else {
            System.out.println(Messages.WRONG);
        }
    }

}
