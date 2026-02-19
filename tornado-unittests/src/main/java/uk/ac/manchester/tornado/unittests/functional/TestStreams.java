/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.functional;

import org.junit.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;

import java.util.ArrayList;

import static org.junit.Assert.fail;

public class TestStreams {

    private static void deviceDummyCompute(final DoubleArray src, final DoubleArray dst) {
        for (@Parallel int i = 0; i < src.getSize(); i++) {
            dst.set(i, src.get(i) * 2);
        }
    }

    private static void hostComputeMethod() throws TornadoExecutionPlanException {
        DoubleArray src = new DoubleArray(1024);
        DoubleArray dst = new DoubleArray(1024);
        String threadName = Thread.currentThread().getName();

        TaskGraph taskGraph = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, src, dst) //
                .task(threadName, TestStreams::deviceDummyCompute, src, dst) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, dst);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph)) {
            executor.execute();
        }
    }

    @Test
    public void testParallelStreams() throws TornadoExecutionPlanException {
        ArrayList<Integer> s = new ArrayList<>();
        for (int i = 0; i < 512; i++) {
            s.add(i);
        }

        s.parallelStream().forEach(k -> {
            try {
                hostComputeMethod();
            } catch (TornadoExecutionPlanException e) {
                fail("Got exception executing TornadoExecutionPlan inside parallel stream: " + e.getMessage());
            }
        });
    }

}
