/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.instances;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.instances.TestInstances
 * </code>
 *
 */
public class TestInstances extends TornadoTestBase {

    public static class Foo {
        // Parallel initialisation
        public void compute(DoubleArray array, double initValue) {
            for (int i = 0; i < array.getSize(); i++) {
                array.set(i, initValue);
            }
        }
    }

    @Test
    public void testInit() throws TornadoExecutionPlanException {
        Foo f = new Foo();
        DoubleArray array = new DoubleArray(1000);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", f::compute, array, 2.1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, array);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < array.getSize(); i++) {
            assertEquals(2.1, array.get(i), 0.001);
        }
    }

    public void compute(DoubleArray array, double initValue) {
        for (int i = 0; i < array.getSize(); i++) {
            array.set(i, initValue);
        }
    }

    @Test
    public void testThis() throws TornadoExecutionPlanException {
        DoubleArray array = new DoubleArray(1000);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", this::compute, array, 2.1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, array);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < array.getSize(); i++) {
            assertEquals(2.1, array.get(i), 0.001);
        }
    }
}
