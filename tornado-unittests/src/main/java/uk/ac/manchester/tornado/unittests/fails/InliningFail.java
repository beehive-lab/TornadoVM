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
package uk.ac.manchester.tornado.unittests.fails;

import org.junit.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoInliningException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * Test to check if the appropriate exception is thrown by TornadoVM
 * when inner function cannot be inlined.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fails.InliningFail
 * </code>
 */
public class InliningFail extends TornadoTestBase {

    private static float complexComputation(float x) {
        float result;
        float temp1, temp2, temp3, temp4, temp5, temp6, temp7, temp8;

        temp1 = x * 2.0f + x / 3.0f - TornadoMath.abs(x) + x * x;
        temp2 = TornadoMath.sqrt(temp1 + 1.0f) * TornadoMath.sin(x) + TornadoMath.cos(x);
        temp3 = TornadoMath.exp(temp2 / 10.0f) + TornadoMath.log(TornadoMath.abs(temp1) + 1.0f);
        temp4 = TornadoMath.pow(temp3, 2.0f) * temp1 + temp2 - temp3;
        temp5 = temp4 * TornadoMath.sin(temp1) + TornadoMath.cos(temp2) * temp3;
        temp6 = TornadoMath.sqrt(TornadoMath.abs(temp5) + 1.0f) + temp4 * temp1;
        temp7 = temp6 / (temp5 + 1.0f) * TornadoMath.exp(temp4 / 100.0f);
        temp8 = TornadoMath.log(TornadoMath.abs(temp7) + 1.0f) + temp6 * temp5;
        result = temp1 + temp2 + temp3 + temp4 + temp5 + temp6 + temp7 + temp8;

        temp1 = result * 2.0f + x / 3.0f - TornadoMath.abs(result) + x * x;
        temp2 = TornadoMath.sqrt(temp1 + 1.0f) * TornadoMath.sin(result) + TornadoMath.cos(x);
        temp3 = TornadoMath.exp(temp2 / 10.0f) + TornadoMath.log(TornadoMath.abs(temp1) + 1.0f);
        temp4 = TornadoMath.pow(temp3, 2.0f) * temp1 + temp2 - temp3;
        temp5 = temp4 * TornadoMath.sin(temp1) + TornadoMath.cos(temp2) * temp3;
        temp6 = TornadoMath.sqrt(TornadoMath.abs(temp5) + 1.0f) + temp4 * temp1;
        temp7 = temp6 / (temp5 + 1.0f) * TornadoMath.exp(temp4 / 100.0f);
        temp8 = TornadoMath.log(TornadoMath.abs(temp7) + 1.0f) + temp6 * temp5;
        result = result + temp1 + temp2 + temp3 + temp4 + temp5 + temp6 + temp7 + temp8;

        temp1 = result * 2.0f + x / 3.0f - TornadoMath.abs(result) + x * x;
        temp2 = TornadoMath.sqrt(temp1 + 1.0f) * TornadoMath.sin(result) + TornadoMath.cos(x);
        temp3 = TornadoMath.exp(temp2 / 10.0f) + TornadoMath.log(TornadoMath.abs(temp1) + 1.0f);
        temp4 = TornadoMath.pow(temp3, 2.0f) * temp1 + temp2 - temp3;
        temp5 = temp4 * TornadoMath.sin(temp1) + TornadoMath.cos(temp2) * temp3;
        temp6 = TornadoMath.sqrt(TornadoMath.abs(temp5) + 1.0f) + temp4 * temp1;
        temp7 = temp6 / (temp5 + 1.0f) * TornadoMath.exp(temp4 / 100.0f);
        temp8 = TornadoMath.log(TornadoMath.abs(temp7) + 1.0f) + temp6 * temp5;
        result = result + temp1 + temp2 + temp3 + temp4 + temp5 + temp6 + temp7 + temp8;

        temp1 = result * 2.0f + x / 3.0f - TornadoMath.abs(result) + x * x;
        temp2 = TornadoMath.sqrt(temp1 + 1.0f) * TornadoMath.sin(result) + TornadoMath.cos(x);
        temp3 = TornadoMath.exp(temp2 / 10.0f) + TornadoMath.log(TornadoMath.abs(temp1) + 1.0f);
        temp4 = TornadoMath.pow(temp3, 2.0f) * temp1 + temp2 - temp3;
        temp5 = temp4 * TornadoMath.sin(temp1) + TornadoMath.cos(temp2) * temp3;
        temp6 = TornadoMath.sqrt(TornadoMath.abs(temp5) + 1.0f) + temp4 * temp1;
        temp7 = temp6 / (temp5 + 1.0f) * TornadoMath.exp(temp4 / 100.0f);
        temp8 = TornadoMath.log(TornadoMath.abs(temp7) + 1.0f) + temp6 * temp5;
        result = result + temp1 + temp2 + temp3 + temp4 + temp5 + temp6 + temp7 + temp8;

        temp1 = result * 2.0f + x / 3.0f - TornadoMath.abs(result) + x * x;
        temp2 = TornadoMath.sqrt(temp1 + 1.0f) * TornadoMath.sin(result) + TornadoMath.cos(x);
        temp3 = TornadoMath.exp(temp2 / 10.0f) + TornadoMath.log(TornadoMath.abs(temp1) + 1.0f);
        temp4 = TornadoMath.pow(temp3, 2.0f) * temp1 + temp2 - temp3;
        temp5 = temp4 * TornadoMath.sin(temp1) + TornadoMath.cos(temp2) * temp3;
        temp6 = TornadoMath.sqrt(TornadoMath.abs(temp5) + 1.0f) + temp4 * temp1;
        temp7 = temp6 / (temp5 + 1.0f) * TornadoMath.exp(temp4 / 100.0f);
        temp8 = TornadoMath.log(TornadoMath.abs(temp7) + 1.0f) + temp6 * temp5;
        result = result + temp1 + temp2 + temp3 + temp4 + temp5 + temp6 + temp7 + temp8;

        temp1 = result * 2.0f + x / 3.0f - TornadoMath.abs(result) + x * x;
        temp2 = TornadoMath.sqrt(temp1 + 1.0f) * TornadoMath.sin(result) + TornadoMath.cos(x);
        temp3 = TornadoMath.exp(temp2 / 10.0f) + TornadoMath.log(TornadoMath.abs(temp1) + 1.0f);
        temp4 = TornadoMath.pow(temp3, 2.0f) * temp1 + temp2 - temp3;
        temp5 = temp4 * TornadoMath.sin(temp1) + TornadoMath.cos(temp2) * temp3;
        temp6 = TornadoMath.sqrt(TornadoMath.abs(temp5) + 1.0f) + temp4 * temp1;
        temp7 = temp6 / (temp5 + 1.0f) * TornadoMath.exp(temp4 / 100.0f);
        temp8 = TornadoMath.log(TornadoMath.abs(temp7) + 1.0f) + temp6 * temp5;
        result = result + temp1 + temp2 + temp3 + temp4 + temp5 + temp6 + temp7 + temp8;

        temp1 = result * 2.0f + x / 3.0f - TornadoMath.abs(result) + x * x;
        temp2 = TornadoMath.sqrt(temp1 + 1.0f) * TornadoMath.sin(result) + TornadoMath.cos(x);
        temp3 = TornadoMath.exp(temp2 / 10.0f) + TornadoMath.log(TornadoMath.abs(temp1) + 1.0f);
        temp4 = TornadoMath.pow(temp3, 2.0f) * temp1 + temp2 - temp3;
        temp5 = temp4 * TornadoMath.sin(temp1) + TornadoMath.cos(temp2) * temp3;
        temp6 = TornadoMath.sqrt(TornadoMath.abs(temp5) + 1.0f) + temp4 * temp1;
        temp7 = temp6 / (temp5 + 1.0f) * TornadoMath.exp(temp4 / 100.0f);
        temp8 = TornadoMath.log(TornadoMath.abs(temp7) + 1.0f) + temp6 * temp5;
        result = result + temp1 + temp2 + temp3 + temp4 + temp5 + temp6 + temp7 + temp8;

        temp1 = result * 2.0f + x / 3.0f - TornadoMath.abs(result) + x * x;
        temp2 = TornadoMath.sqrt(temp1 + 1.0f) * TornadoMath.sin(result) + TornadoMath.cos(x);
        temp3 = TornadoMath.exp(temp2 / 10.0f) + TornadoMath.log(TornadoMath.abs(temp1) + 1.0f);
        temp4 = TornadoMath.pow(temp3, 2.0f) * temp1 + temp2 - temp3;
        temp5 = temp4 * TornadoMath.sin(temp1) + TornadoMath.cos(temp2) * temp3;
        temp6 = TornadoMath.sqrt(TornadoMath.abs(temp5) + 1.0f) + temp4 * temp1;
        temp7 = temp6 / (temp5 + 1.0f) * TornadoMath.exp(temp4 / 100.0f);
        temp8 = TornadoMath.log(TornadoMath.abs(temp7) + 1.0f) + temp6 * temp5;
        result = result + temp1 + temp2 + temp3 + temp4 + temp5 + temp6 + temp7 + temp8;

        temp1 = result * 2.0f + x / 3.0f - TornadoMath.abs(result) + x * x;
        temp2 = TornadoMath.sqrt(temp1 + 1.0f) * TornadoMath.sin(result) + TornadoMath.cos(x);
        temp3 = TornadoMath.exp(temp2 / 10.0f) + TornadoMath.log(TornadoMath.abs(temp1) + 1.0f);
        temp4 = TornadoMath.pow(temp3, 2.0f) * temp1 + temp2 - temp3;
        temp5 = temp4 * TornadoMath.sin(temp1) + TornadoMath.cos(temp2) * temp3;
        temp6 = TornadoMath.sqrt(TornadoMath.abs(temp5) + 1.0f) + temp4 * temp1;
        temp7 = temp6 / (temp5 + 1.0f) * TornadoMath.exp(temp4 / 100.0f);
        temp8 = TornadoMath.log(TornadoMath.abs(temp7) + 1.0f) + temp6 * temp5;
        result = result + temp1 + temp2 + temp3 + temp4 + temp5 + temp6 + temp7 + temp8;

        temp1 = result * 2.0f + x / 3.0f - TornadoMath.abs(result) + x * x;
        temp2 = TornadoMath.sqrt(temp1 + 1.0f) * TornadoMath.sin(result) + TornadoMath.cos(x);
        temp3 = TornadoMath.exp(temp2 / 10.0f) + TornadoMath.log(TornadoMath.abs(temp1) + 1.0f);
        temp4 = TornadoMath.pow(temp3, 2.0f) * temp1 + temp2 - temp3;
        temp5 = temp4 * TornadoMath.sin(temp1) + TornadoMath.cos(temp2) * temp3;
        temp6 = TornadoMath.sqrt(TornadoMath.abs(temp5) + 1.0f) + temp4 * temp1;
        temp7 = temp6 / (temp5 + 1.0f) * TornadoMath.exp(temp4 / 100.0f);
        temp8 = TornadoMath.log(TornadoMath.abs(temp7) + 1.0f) + temp6 * temp5;
        result = result + temp1 + temp2 + temp3 + temp4 + temp5 + temp6 + temp7 + temp8;

        temp1 = result * 2.0f + x / 3.0f - TornadoMath.abs(result) + x * x;
        temp2 = TornadoMath.sqrt(temp1 + 1.0f) * TornadoMath.sin(result) + TornadoMath.cos(x);
        temp3 = TornadoMath.exp(temp2 / 10.0f) + TornadoMath.log(TornadoMath.abs(temp1) + 1.0f);
        temp4 = TornadoMath.pow(temp3, 2.0f) * temp1 + temp2 - temp3;
        temp5 = temp4 * TornadoMath.sin(temp1) + TornadoMath.cos(temp2) * temp3;
        temp6 = TornadoMath.sqrt(TornadoMath.abs(temp5) + 1.0f) + temp4 * temp1;
        temp7 = temp6 / (temp5 + 1.0f) * TornadoMath.exp(temp4 / 100.0f);
        temp8 = TornadoMath.log(TornadoMath.abs(temp7) + 1.0f) + temp6 * temp5;
        result = result + temp1 + temp2 + temp3 + temp4 + temp5 + temp6 + temp7 + temp8;

        temp1 = result * 2.0f + x / 3.0f - TornadoMath.abs(result) + x * x;
        temp2 = TornadoMath.sqrt(temp1 + 1.0f) * TornadoMath.sin(result) + TornadoMath.cos(x);
        temp3 = TornadoMath.exp(temp2 / 10.0f) + TornadoMath.log(TornadoMath.abs(temp1) + 1.0f);
        temp4 = TornadoMath.pow(temp3, 2.0f) * temp1 + temp2 - temp3;
        temp5 = temp4 * TornadoMath.sin(temp1) + TornadoMath.cos(temp2) * temp3;
        temp6 = TornadoMath.sqrt(TornadoMath.abs(temp5) + 1.0f) + temp4 * temp1;
        temp7 = temp6 / (temp5 + 1.0f) * TornadoMath.exp(temp4 / 100.0f);
        temp8 = TornadoMath.log(TornadoMath.abs(temp7) + 1.0f) + temp6 * temp5;
        result = result + temp1 + temp2 + temp3 + temp4 + temp5 + temp6 + temp7 + temp8;

        temp1 = result * 2.0f + x / 3.0f - TornadoMath.abs(result) + x * x;
        temp2 = TornadoMath.sqrt(temp1 + 1.0f) * TornadoMath.sin(result) + TornadoMath.cos(x);
        temp3 = TornadoMath.exp(temp2 / 10.0f) + TornadoMath.log(TornadoMath.abs(temp1) + 1.0f);
        temp4 = TornadoMath.pow(temp3, 2.0f) * temp1 + temp2 - temp3;
        temp5 = temp4 * TornadoMath.sin(temp1) + TornadoMath.cos(temp2) * temp3;
        temp6 = TornadoMath.sqrt(TornadoMath.abs(temp5) + 1.0f) + temp4 * temp1;
        temp7 = temp6 / (temp5 + 1.0f) * TornadoMath.exp(temp4 / 100.0f);
        temp8 = TornadoMath.log(TornadoMath.abs(temp7) + 1.0f) + temp6 * temp5;
        result = result + temp1 + temp2 + temp3 + temp4 + temp5 + temp6 + temp7 + temp8;

        return result;
    }

    public static void testInlinedFunction(FloatArray input, FloatArray output) {
        for (@Parallel int i = 0; i < output.getSize(); i++) {
            input.set(i, complexComputation(input.get(i)));
        }
    }

    @Test (expected = TornadoInliningException.class)
    public void testInliningError() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        FloatArray a = new FloatArray(numElements);
        FloatArray b = new FloatArray(numElements);
        FloatArray c = new FloatArray(numElements);

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a.set(i, (float) Math.random());
        });

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", InliningFail::testInlinedFunction, a, b) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan tornadoExecutor = new TornadoExecutionPlan(immutableTaskGraph)) {
            tornadoExecutor.execute();
        }

        testInlinedFunction(a, c);

        for (int i = 0; i < c.getSize(); i++) {
            assertEquals(b.get(i), c.get(i), 0.01f);
        }
    }

}
