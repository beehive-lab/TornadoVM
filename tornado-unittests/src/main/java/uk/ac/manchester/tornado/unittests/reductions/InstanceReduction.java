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
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.reductions.InstanceReduction
 * </code>
 */
public class InstanceReduction extends TornadoTestBase {

    public static final int N = 1024;

    public class ReduceTest {
        public void reduce(float[] input, @Reduce float[] result) {
            result[0] = 0.0f;
            for (@Parallel int i = 0; i < input.length; i++) {
                result[0] += input[i];
            }
        }
    }

    @Test
    public void testReductionInstanceClass() {

        float[] input = new float[N];
        float[] result = new float[1];
        float[] expected = new float[1];

        Random rand = new Random();
        IntStream.range(0, N).forEach(i -> {
            input[i] = rand.nextFloat();
        });

        for (float v : input) {
            expected[0] += v;
        }

        ReduceTest rd = new ReduceTest();

        TaskGraph taskGraph = new TaskGraph("ts") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)//
                .task("reduce", rd::reduce, input, result)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        assertEquals(expected[0], result[0], 0.1f);
    }
}
