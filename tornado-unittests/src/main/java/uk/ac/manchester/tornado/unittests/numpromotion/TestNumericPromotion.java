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

package uk.ac.manchester.tornado.unittests.numpromotion;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * JVM applies "Binary Numeric Promotion"
 * https://docs.oracle.com/javase/specs/jls/se8/html/jls-5.html#jls-5.6.2
 * <p>
 * In a nutshell, byte operations are performed in bytes, but JVM narrows up the
 * result to int (signed), which leads to wrong results, because the output is
 * not a byte anymore, is an integer.
 * </p>
 * <p>
 * Since this is in the JLS spec, we can't do too much regarding Java semantics,
 * but a possible solution is to expose a type in TornadoVM that we can handle
 * like an intrinsic and force not to sign those operations.
 * </p>
 * <p>
 * Affect arithmetic, logical and shift operations when operands are byte and
 * short data types.
 * </p>
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.numpromotion.TestNumericPromotion
 * </code>
 */

public class TestNumericPromotion extends TornadoTestBase {

    public static void bitwiseOr(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                result.set(j, (byte) (result.get(j) | input.get((i * elements.get(0)) + j)));
            }
        }
    }

    public static void bitwiseAnd(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                result.set(j, (byte) (result.get(j) & input.get((i * elements.get(0)) + j)));
            }
        }
    }

    public static void bitwiseXor(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                result.set(j, (byte) (result.get(j) ^ input.get((i * elements.get(0)) + j)));
            }
        }
    }

    public static void bitwiseNot(ByteArray result, ByteArray input) {
        for (int i = 0; i < input.getSize(); i++) {
            result.set(i, (byte) ~input.get(i));
        }
    }

    public static void addition(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                result.set(j, (byte) (result.get(j) + input.get((i * elements.get(0)) + j)));
            }
        }
    }

    public static void subtraction(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                result.set(j, (byte) (result.get(j) - input.get((i * elements.get(0)) + j)));
            }
        }
    }

    public static void multiplication(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                result.set(j, (byte) (result.get(j) * input.get((i * elements.get(0)) + j)));
            }
        }
    }

    public static void division(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                result.set(j, (byte) (result.get(j) / input.get((i * elements.get(0)) + j)));
            }
        }
    }

    public static void signedLeftShift(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                result.set(j, (byte) (result.get(j) << input.get((i * elements.get(0)) + j)));
            }
        }
    }

    public static void signedRightShift(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                result.set(j, (byte) (result.get(j) >> input.get((i * elements.get(0)) + j)));
            }
        }
    }

    public static void unsignedRightShift(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                result.set(j, (byte) (result.get(j) >>> input.get((i * elements.get(0)) + j)));
            }
        }
    }

    @Test
    public void testBitwiseOr() throws TornadoExecutionPlanException {

        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        ByteArray input = ByteArray.fromElements((byte) 127, (byte) 127, (byte) 127, (byte) 127, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements) //
                .task("t0", TestNumericPromotion::bitwiseOr, result, input, elements) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        bitwiseOr(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testBitwiseAnd() throws TornadoExecutionPlanException {

        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        ByteArray input = ByteArray.fromElements((byte) 127, (byte) 127, (byte) 127, (byte) 127, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::bitwiseAnd, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        bitwiseAnd(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testBitwiseXor() throws TornadoExecutionPlanException {

        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        ByteArray input = ByteArray.fromElements((byte) 127, (byte) 127, (byte) 127, (byte) 127, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::bitwiseXor, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        bitwiseXor(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testBitwiseNot() throws TornadoExecutionPlanException {

        ByteArray result = new ByteArray(8);
        ByteArray input = ByteArray.fromElements((byte) 0, (byte) 0, (byte) 127, (byte) -127, (byte) 1, (byte) -1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)//
                .task("t0", TestNumericPromotion::bitwiseNot, result, input)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(8);
        bitwiseNot(sequential, input);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testAddition() throws TornadoExecutionPlanException {

        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        ByteArray input = ByteArray.fromElements((byte) 127, (byte) 127, (byte) 127, (byte) 127, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::addition, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        addition(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testSubtraction() throws TornadoExecutionPlanException {

        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        ByteArray input = ByteArray.fromElements((byte) 125, (byte) 125, (byte) 125, (byte) 125, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::subtraction, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        subtraction(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testMultiplication() throws TornadoExecutionPlanException {

        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        result.init((byte) 1);
        ByteArray input = ByteArray.fromElements((byte) 125, (byte) 125, (byte) 125, (byte) 125, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::multiplication, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        sequential.init((byte) 1);
        multiplication(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testDivision() throws TornadoExecutionPlanException {

        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        result.init((byte) 8);
        ByteArray input = ByteArray.fromElements((byte) 2, (byte) 2, (byte) 2, (byte) 2, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::division, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        sequential.init((byte) 8);
        division(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testSignedLeftShift() throws TornadoExecutionPlanException {

        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        result.init((byte) 8);
        ByteArray input = ByteArray.fromElements((byte) 2, (byte) 2, (byte) 2, (byte) 2, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::signedLeftShift, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        sequential.init((byte) 8);
        signedLeftShift(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testSignedRightShift() throws TornadoExecutionPlanException {

        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        result.init((byte) 8);
        ByteArray input = ByteArray.fromElements((byte) 2, (byte) 2, (byte) 2, (byte) 2, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::signedRightShift, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        sequential.init((byte) 8);
        signedRightShift(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testUnsignedRightShift() throws TornadoExecutionPlanException {

        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        result.init((byte) 8);
        ByteArray input = ByteArray.fromElements((byte) 2, (byte) 2, (byte) 2, (byte) 2, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::unsignedRightShift, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        ByteArray sequential = new ByteArray(4);
        sequential.init((byte) 8);
        unsignedRightShift(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    // Compound-assignment operators (JLS 15.26.2): each includes an implicit narrowing
    // cast back to byte, distinct from the plain-operator methods above.

    public static void compoundAddAssign(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                byte value = result.get(j);
                value += input.get((i * elements.get(0)) + j);
                result.set(j, value);
            }
        }
    }

    public static void compoundSubAssign(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                byte value = result.get(j);
                value -= input.get((i * elements.get(0)) + j);
                result.set(j, value);
            }
        }
    }

    public static void compoundMulAssign(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                byte value = result.get(j);
                value *= input.get((i * elements.get(0)) + j);
                result.set(j, value);
            }
        }
    }

    public static void compoundDivAssign(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                byte value = result.get(j);
                value /= input.get((i * elements.get(0)) + j);
                result.set(j, value);
            }
        }
    }

    public static void compoundModAssign(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                byte value = result.get(j);
                value %= input.get((i * elements.get(0)) + j);
                result.set(j, value);
            }
        }
    }

    public static void compoundAndAssign(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                byte value = result.get(j);
                value &= input.get((i * elements.get(0)) + j);
                result.set(j, value);
            }
        }
    }

    public static void compoundOrAssign(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                byte value = result.get(j);
                value |= input.get((i * elements.get(0)) + j);
                result.set(j, value);
            }
        }
    }

    public static void compoundXorAssign(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                byte value = result.get(j);
                value ^= input.get((i * elements.get(0)) + j);
                result.set(j, value);
            }
        }
    }

    public static void compoundLeftShiftAssign(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                byte value = result.get(j);
                value <<= input.get((i * elements.get(0)) + j);
                result.set(j, value);
            }
        }
    }

    public static void compoundRightShiftAssign(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                byte value = result.get(j);
                value >>= input.get((i * elements.get(0)) + j);
                result.set(j, value);
            }
        }
    }

    public static void compoundUnsignedRightShiftAssign(ByteArray result, ByteArray input, ByteArray elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements.get(0); j++) {
                byte value = result.get(j);
                value >>>= input.get((i * elements.get(0)) + j);
                result.set(j, value);
            }
        }
    }

    @Test
    public void testCompoundAddAssign() throws TornadoExecutionPlanException {
        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        ByteArray input = ByteArray.fromElements((byte) 127, (byte) 127, (byte) 127, (byte) 127, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::compoundAddAssign, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        compoundAddAssign(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testCompoundSubAssign() throws TornadoExecutionPlanException {
        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        ByteArray input = ByteArray.fromElements((byte) 125, (byte) 125, (byte) 125, (byte) 125, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::compoundSubAssign, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        compoundSubAssign(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testCompoundMulAssign() throws TornadoExecutionPlanException {
        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        result.init((byte) 1);
        ByteArray input = ByteArray.fromElements((byte) 125, (byte) 125, (byte) 125, (byte) 125, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::compoundMulAssign, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        sequential.init((byte) 1);
        compoundMulAssign(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testCompoundDivAssign() throws TornadoExecutionPlanException {
        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        result.init((byte) 8);
        ByteArray input = ByteArray.fromElements((byte) 2, (byte) 2, (byte) 2, (byte) 2, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::compoundDivAssign, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        sequential.init((byte) 8);
        compoundDivAssign(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testCompoundModAssign() throws TornadoExecutionPlanException {
        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        result.init((byte) 9);
        ByteArray input = ByteArray.fromElements((byte) 4, (byte) 4, (byte) 4, (byte) 4, (byte) 5, (byte) 5, (byte) 5, (byte) 5);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::compoundModAssign, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        sequential.init((byte) 9);
        compoundModAssign(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testCompoundAndAssign() throws TornadoExecutionPlanException {
        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        ByteArray input = ByteArray.fromElements((byte) 127, (byte) 127, (byte) 127, (byte) 127, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::compoundAndAssign, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        compoundAndAssign(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testCompoundOrAssign() throws TornadoExecutionPlanException {
        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        ByteArray input = ByteArray.fromElements((byte) 127, (byte) 127, (byte) 127, (byte) 127, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::compoundOrAssign, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        compoundOrAssign(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testCompoundXorAssign() throws TornadoExecutionPlanException {
        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        ByteArray input = ByteArray.fromElements((byte) 127, (byte) 127, (byte) 127, (byte) 127, (byte) 1, (byte) 1, (byte) 1, (byte) 1);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::compoundXorAssign, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        compoundXorAssign(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testCompoundLeftShiftAssign() throws TornadoExecutionPlanException {
        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        result.init((byte) 1);
        ByteArray input = ByteArray.fromElements((byte) 1, (byte) 1, (byte) 1, (byte) 1, (byte) 2, (byte) 2, (byte) 2, (byte) 2);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::compoundLeftShiftAssign, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        sequential.init((byte) 1);
        compoundLeftShiftAssign(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testCompoundRightShiftAssign() throws TornadoExecutionPlanException {
        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        result.init((byte) -128);
        ByteArray input = ByteArray.fromElements((byte) 1, (byte) 1, (byte) 1, (byte) 1, (byte) 2, (byte) 2, (byte) 2, (byte) 2);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::compoundRightShiftAssign, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        sequential.init((byte) -128);
        compoundRightShiftAssign(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

    @Test
    public void testCompoundUnsignedRightShiftAssign() throws TornadoExecutionPlanException {
        ByteArray elements = new ByteArray(1);
        elements.init((byte) 4);
        ByteArray result = new ByteArray(4);
        result.init((byte) -128);
        ByteArray input = ByteArray.fromElements((byte) 1, (byte) 1, (byte) 1, (byte) 1, (byte) 2, (byte) 2, (byte) 2, (byte) 2);

        TaskGraph taskGraph = new TaskGraph("s0")//
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, input, elements)//
                .task("t0", TestNumericPromotion::compoundUnsignedRightShiftAssign, result, input, elements)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        ByteArray sequential = new ByteArray(4);
        sequential.init((byte) -128);
        compoundUnsignedRightShiftAssign(sequential, input, elements);
        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(sequential.get(i), result.get(i));
        }
    }

}
