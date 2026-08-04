/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.logic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Every enum elsewhere in the suite is a host-side helper never referenced from a kernel body.
 * This exercises an enum WITH a field and an instance method, selected per-element and invoked
 * from inside a {@code @Parallel} kernel -- previously untested territory (does virtual dispatch
 * on an enum constant survive the Graal-to-GPU lowering, or does it bail out?).
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.logic.TestEnumInKernel
 * </code>
 */
public class TestEnumInKernel extends TornadoTestBase {

    private enum Operation {
        ADD(1),
        SUB(-1);

        private final int sign;

        Operation(int sign) {
            this.sign = sign;
        }

        int apply(int a, int b) {
            return a + sign * b;
        }
    }

    public static void enumDispatchKernel(IntArray a, IntArray b, IntArray out) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            Operation op = (i % 2 == 0) ? Operation.ADD : Operation.SUB;
            out.set(i, op.apply(a.get(i), b.get(i)));
        }
    }

    @Test
    public void testEnumWithFieldAndMethodInKernel() throws TornadoExecutionPlanException {
        final int size = 64;
        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        for (int i = 0; i < size; i++) {
            a.set(i, i + 1);
            b.set(i, 10);
        }
        IntArray out = new IntArray(size);
        IntArray expected = new IntArray(size);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestEnumInKernel::enumDispatchKernel, a, b, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        enumDispatchKernel(a, b, expected);
        for (int i = 0; i < size; i++) {
            assertEquals(expected.get(i), out.get(i));
        }
    }

}
