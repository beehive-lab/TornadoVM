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
package uk.ac.manchester.tornado.unittests.reductions;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.reductions.InstanceReduction
 * </code>
 */
public class InstanceReduction extends TornadoTestBase {

    public static final int N = 1024;

    public class ReduceTest {
        public void reduce(FloatArray input, @Reduce FloatArray result) {
            result.set(0, 0.0f);
            for (@Parallel int i = 0; i < input.getSize(); i++) {
                result.set(0, result.get(0) + input.get(i));
            }
        }
    }

    @Test
    public void testReductionInstanceClass() throws TornadoExecutionPlanException {

        FloatArray input = new FloatArray(N);
        FloatArray result = new FloatArray(1);
        FloatArray expected = new FloatArray(1);

        Random rand = new Random();
        IntStream.range(0, N).forEach(i -> {
            input.set(i, rand.nextFloat());
        });

        for (int i = 0; i < N; i++) {
            expected.set(0, expected.get(0) + input.get(i));
        }

        ReduceTest rd = new ReduceTest();

        TaskGraph taskGraph = new TaskGraph("ts") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)//
                .task("reduce", rd::reduce, input, result)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        assertEquals(expected.get(0), result.get(0), 0.1f);
    }
}
