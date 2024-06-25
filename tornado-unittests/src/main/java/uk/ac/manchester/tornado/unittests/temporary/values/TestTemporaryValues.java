/*
 * Copyright (c) 2022, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.temporary.values;

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
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.temporary.values.TestTemporaryValues
 * </code>
 */
public class TestTemporaryValues extends TornadoTestBase {
    private static void computeWithTemporaryValues(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            float valueA = a.get(i);
            float valueB = b.get(i);

            a.set(i, valueA + b.get(i));
            b.set(i, valueA * 2);
            c.set(i, valueA + valueB);
        }
    }

    @Test
    public void testTemporaryValues01() throws TornadoExecutionPlanException {
        final int numElements = 8;
        FloatArray aTornado = new FloatArray(numElements);
        FloatArray bTornado = new FloatArray(numElements);
        FloatArray cTornado = new FloatArray(numElements);
        FloatArray aJava = new FloatArray(numElements);
        FloatArray bJava = new FloatArray(numElements);
        FloatArray cJava = new FloatArray(numElements);

        Random r = new Random();
        IntStream.range(0, aJava.getSize()).forEach(idx -> {
            aTornado.set(idx, r.nextFloat());
            aJava.set(idx, aTornado.get(idx));
            bTornado.set(idx, r.nextFloat());
            bJava.set(idx, bTornado.get(idx));
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, aTornado, bTornado) //
                .task("t0", TestTemporaryValues::computeWithTemporaryValues, aTornado, bTornado, cTornado) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, aTornado, bTornado, cTornado);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        computeWithTemporaryValues(aJava, bJava, cJava);

        for (int i = 0; i < aJava.getSize(); i++) {
            assertEquals(aJava.get(i), aTornado.get(i), 0.001f);
            assertEquals(bJava.get(i), bTornado.get(i), 0.001f);
            assertEquals(cJava.get(i), cTornado.get(i), 0.001f);
        }
    }
}
