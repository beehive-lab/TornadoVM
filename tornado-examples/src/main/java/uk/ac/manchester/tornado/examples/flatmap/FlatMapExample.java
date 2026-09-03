/*
 * Copyright (c) 2020-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.flatmap;

import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Flat-map over a {@code FloatArray}. Values above 100 expand into {@code SIZE}
 * outputs. The output buffer is allocated up front because TornadoVM does not
 * support dynamic device allocation.
 * <p>
 * Copies the input on every execution, runs a {@code @Parallel}
 * {@code computeFlatMap} task, and copies the output back.
 * </p>
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.flatmap.FlatMapExample
 * </code>
 */
public class FlatMapExample {

    private static final int SIZE = 16;

    private static void computeFlatMap(FloatArray input, FloatArray output, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            if (input.get(i) > 100) {
                for (int j = 0; j < size; j++) {
                    output.set(i * size + j, input.get(i) + j);
                }
            }
        }
    }

    public static void main(String[] args) {

        FloatArray input = new FloatArray(SIZE * SIZE);
        FloatArray output = new FloatArray(SIZE * SIZE);

        Random r = new Random();
        IntStream.range(0, input.getSize()).forEach(i -> {
            input.set(i, 50 + r.nextInt(100));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", FlatMapExample::computeFlatMap, input, output, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executorPlan.execute();

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                System.out.print(output.get(i * SIZE + j) + " ");
            }
            System.out.println();
        }
    }
}
