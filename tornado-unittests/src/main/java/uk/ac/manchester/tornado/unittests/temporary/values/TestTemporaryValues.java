/*
 * Copyright (c) 2022, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.temporary.values.TestTemporaryValues
 * </code>
 */
public class TestTemporaryValues extends TornadoTestBase {
    private static void computeWithTemporaryValues(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            float valueA = a[i];
            float valueB = b[i];

            a[i] = valueA + b[i];
            b[i] = valueA * 2;
            c[i] = valueA + valueB;
        }
    }

    @Test
    public void testTemporaryValues01() {
        final int numElements = 8;
        float[] aTornado = new float[numElements];
        float[] bTornado = new float[numElements];
        float[] cTornado = new float[numElements];
        float[] aJava = new float[numElements];
        float[] bJava = new float[numElements];
        float[] cJava = new float[numElements];

        Random r = new Random();
        IntStream.range(0, aJava.length).forEach(idx -> {
            aTornado[idx] = r.nextFloat();
            aJava[idx] = aTornado[idx];
            bTornado[idx] = r.nextFloat();
            bJava[idx] = bTornado[idx];
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, aTornado, bTornado) //
                .task("t0", TestTemporaryValues::computeWithTemporaryValues, aTornado, bTornado, cTornado) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, aTornado, bTornado, cTornado);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        computeWithTemporaryValues(aJava, bJava, cJava);

        for (int i = 0; i < aJava.length; i++) {
            assertEquals(aJava[i], aTornado[i], 0.001f);
            assertEquals(bJava[i], bTornado[i], 0.001f);
            assertEquals(cJava[i], cTornado[i], 0.001f);
        }
    }
}
