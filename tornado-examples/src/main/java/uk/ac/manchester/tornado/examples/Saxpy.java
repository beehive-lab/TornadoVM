/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import uk.ac.manchester.tornado.api.DRMode;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.examples.common.Messages;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.Saxpy
 * </code>
 *
 */
public class Saxpy {

    public static void saxpy(float alpha, FloatArray x, FloatArray y) {
        for (@Parallel int i = 0; i < y.getSize(); i++) {
            y.set(i, alpha * x.get(i));
        }
    }

    public static void main(String[] args) {
        int numElements = 512;

        if (args.length > 0) {
            numElements = Integer.parseInt(args[0]);
        }

        final float alpha = 2f;

        final FloatArray x = new FloatArray(numElements);
        final FloatArray y = new FloatArray(numElements);

        IntStream.range(0, numElements).parallel().forEach(i -> x.set(i, 450));

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .task("t0", Saxpy::saxpy, alpha, x, y)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
        executor.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.SERIAL).execute();

        numElements = 512 * 2;

        final FloatArray a = new FloatArray(numElements);
        final FloatArray b = new FloatArray(numElements);

        IntStream.range(0, numElements).parallel().forEach(i -> a.set(i, 450));

        TaskGraph taskGraph1 = new TaskGraph("s1").task("t0", Saxpy::saxpy, alpha, a, b).transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
        TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1);
        executor1.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.PARALLEL).execute();

        boolean wrongResult = false;
        for (int i = 0; i < y.getSize(); i++) {
            if (Math.abs(y.get(i) - (alpha * x.get(i))) > 0.01) {
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
