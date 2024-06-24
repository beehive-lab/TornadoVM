/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.flatmap;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.flatmap.TestFlatMap
 * </code>
 */
public class TestFlatMap extends TornadoTestBase {

    private static final int SIZE = 256;

    private static void computeFlatMap(FloatArray input, FloatArray output, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            if (input.get(i) > 100) {
                for (int j = 0; j < size; j++) {
                    output.set(i * size + j, input.get(i) + j);
                }
            }
        }
    }

    @Test
    public void testFlatMap() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE * SIZE);
        FloatArray output = new FloatArray(SIZE * SIZE);
        FloatArray seq = new FloatArray(SIZE * SIZE);

        Random r = new Random();
        IntStream.range(0, input.getSize()).forEach(i -> {
            input.set(i, 50 + r.nextInt(100));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestFlatMap::computeFlatMap, input, output, SIZE) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        computeFlatMap(input, seq, SIZE);

        for (int i = 0; i < input.getSize(); i++) {
            assertEquals(seq.get(i), output.get(i), 0.001f);
        }

    }
}
