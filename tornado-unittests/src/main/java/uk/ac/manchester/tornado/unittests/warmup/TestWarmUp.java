/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.warmup;

import org.junit.Assert;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V --live uk.ac.manchester.tornado.unittests.warmup.TestWarmUp
 * </code>
 */
public class TestWarmUp extends TornadoTestBase {

    @Test
    public void test01() throws TornadoExecutionPlanException, InterruptedException {
        TaskGraph taskGraph = new TaskGraph("foo");

        FloatArray input = new FloatArray(1024);
        FloatArray output = new FloatArray(1024);
        input.init(0.1f);

        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("bar", (FloatArray in, FloatArray out) -> {
                    for (@Parallel int i = 0; i < in.getSize(); i++) {
                        out.set(i, in.get(i) * 2);
                    }
                }, input, output).transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            // Warmup for 1 second
            long startTime = System.currentTimeMillis();
            plan.withWarmUpTime(1000);
            long endTime = System.currentTimeMillis();

            // 7 ms of tolerance
            Assert.assertTrue((endTime - startTime > 1000) && (endTime - startTime < 1008));

            // Run one more time after the warmup
            plan.withProfiler(ProfilerMode.CONSOLE).execute();
        }
    }

    @Test
    public void test02() throws TornadoExecutionPlanException, InterruptedException {
        TaskGraph taskGraph = new TaskGraph("foo");

        FloatArray input = new FloatArray(1024);
        FloatArray output = new FloatArray(1024);
        input.init(0.1f);

        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("bar", (FloatArray in, FloatArray out) -> {
                    for (@Parallel int i = 0; i < in.getSize(); i++) {
                        out.set(i, in.get(i) * 2);
                    }
                }, input, output).transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {

            // Warmup for 1000 iterations
            plan.withWarmUpIterations(1000);

            // Run one more time after the warmup
            plan.withProfiler(ProfilerMode.CONSOLE).execute();
        }
    }

}
