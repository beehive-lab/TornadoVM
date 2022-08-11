/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.numpromotion;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * JVM applies "Binary Numeric Promotion"
 * https://docs.oracle.com/javase/specs/jls/se8/html/jls-5.html#jls-5.6.2
 * <p>
 * In a nutshell, byte operations are performed in bytes, but JVM narrows up the
 * result to int (signed), which leads to wrong results, because the output is
 * not a byte anymore, is an integer.
 * <p>
 * Since this is in the JLS spec, we can't do too much regarding Java semantics,
 * but a possible solution is to expose a type in TornadoVM that we can handle
 * like an intrinsic and force not to sign those operations.
 * <p>
 * Affect arithmetic, logical and shift operations when operands are byte and
 * short data types.
 */

public class TestNumericPromotion extends TornadoTestBase {

    public static void bitwiseOr(byte[] result, byte[] input, byte[] elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements[0]; j++) {
                result[j] |= input[(i * elements[0]) + j];
            }
        }
    }

    public static void bitwiseAnd(byte[] result, byte[] input, byte[] elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements[0]; j++) {
                result[j] &= input[(i * elements[0]) + j];
            }
        }
    }

    public static void bitwiseXor(byte[] result, byte[] input, byte[] elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements[0]; j++) {
                result[j] ^= input[(i * elements[0]) + j];
            }
        }
    }

    public static void bitwiseNot(byte[] result, byte[] input) {
        for (int i = 0; i < input.length; i++) {
            result[i] = (byte) ~input[i];
        }
    }

    public static void addition(byte[] result, byte[] input, byte[] elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements[0]; j++) {
                result[j] += input[(i * elements[0]) + j];
            }
        }
    }

    public static void subtraction(byte[] result, byte[] input, byte[] elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements[0]; j++) {
                result[j] -= input[(i * elements[0]) + j];
            }
        }
    }

    public static void multiplication(byte[] result, byte[] input, byte[] elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements[0]; j++) {
                result[j] *= input[(i * elements[0]) + j];
            }
        }
    }

    public static void division(byte[] result, byte[] input, byte[] elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements[0]; j++) {
                result[j] /= input[(i * elements[0]) + j];
            }
        }
    }

    public static void signedLeftShift(byte[] result, byte[] input, byte[] elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements[0]; j++) {
                result[j] <<= input[(i * elements[0]) + j];
            }
        }
    }

    public static void signedRightShift(byte[] result, byte[] input, byte[] elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements[0]; j++) {
                result[j] >>= input[(i * elements[0]) + j];
            }
        }
    }

    public static void unsignedRightShift(byte[] result, byte[] input, byte[] elements) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < elements[0]; j++) {
                result[j] >>>= input[(i * elements[0]) + j];
            }
        }
    }

    @Test
    public void testBitwiseOr() {

        byte[] elements = new byte[] { 4 };
        byte[] result = new byte[4];
        byte[] input = new byte[] { 127, 127, 127, 127, 1, 1, 1, 1 };

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(result, input, elements)
                .task("t0", TestNumericPromotion::bitwiseOr, result, input, elements)
                .streamOut(result)
                .execute();
        //@formatter:on

        byte[] sequential = new byte[4];
        bitwiseOr(sequential, input, elements);
        for (int i = 0; i < result.length; i++) {
            assertEquals(sequential[i], result[i]);
        }
    }

    @Test
    public void testBitwiseAnd() {

        byte[] elements = new byte[] { 4 };
        byte[] result = new byte[4];
        byte[] input = new byte[] { 127, 127, 127, 127, 1, 1, 1, 1 };

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(result, input, elements)
                .task("t0", TestNumericPromotion::bitwiseAnd, result, input, elements)
                .streamOut(result)
                .execute();
        //@formatter:on

        byte[] sequential = new byte[4];
        bitwiseAnd(sequential, input, elements);
        for (int i = 0; i < result.length; i++) {
            assertEquals(sequential[i], result[i]);
        }
    }

    @Test
    public void testBitwiseXor() {

        byte[] elements = new byte[] { 4 };
        byte[] result = new byte[4];
        byte[] input = new byte[] { 127, 127, 127, 127, 1, 1, 1, 1 };

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(result, input, elements)
                .task("t0", TestNumericPromotion::bitwiseXor, result, input, elements)
                .streamOut(result)
                .execute();
        //@formatter:on

        byte[] sequential = new byte[4];
        bitwiseXor(sequential, input, elements);
        for (int i = 0; i < result.length; i++) {
            assertEquals(sequential[i], result[i]);
        }
    }

    @Test
    public void testBitwiseNot() {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        byte[] result = new byte[8];
        byte[] input = new byte[] { 0, 0, 127, -127, 1, -1, 1, 1 };

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(input)
                .task("t0", TestNumericPromotion::bitwiseNot, result, input)
                .streamOut(result)
                .execute();
        //@formatter:on

        byte[] sequential = new byte[8];
        bitwiseNot(sequential, input);
        for (int i = 0; i < result.length; i++) {
            assertEquals(sequential[i], result[i]);
        }
    }

    @Test
    public void testAddition() {

        byte[] elements = new byte[] { 4 };
        byte[] result = new byte[4];
        byte[] input = new byte[] { 125, 125, 125, 125, 1, 1, 1, 1 };

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(result, input, elements)
                .task("t0", TestNumericPromotion::addition, result, input, elements)
                .streamOut(result)
                .execute();
        //@formatter:on

        byte[] sequential = new byte[4];
        addition(sequential, input, elements);
        for (int i = 0; i < result.length; i++) {
            assertEquals(sequential[i], result[i]);
        }
    }

    @Test
    public void testSubtraction() {

        byte[] elements = new byte[] { 4 };
        byte[] result = new byte[4];
        byte[] input = new byte[] { 125, 125, 125, 125, 1, 1, 1, 1 };

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(result, input, elements)
                .task("t0", TestNumericPromotion::subtraction, result, input, elements)
                .streamOut(result)
                .execute();
        //@formatter:on

        byte[] sequential = new byte[4];
        subtraction(sequential, input, elements);
        for (int i = 0; i < result.length; i++) {
            assertEquals(sequential[i], result[i]);
        }
    }

    @Test
    public void testMultiplication() {

        byte[] elements = new byte[] { 4 };
        byte[] result = new byte[] { 1, 1, 1, 1 };
        byte[] input = new byte[] { 125, 125, 125, 125, 1, 1, 1, 1 };

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(result, input, elements)
                .task("t0", TestNumericPromotion::multiplication, result, input, elements)
                .streamOut(result)
                .execute();
        //@formatter:on

        byte[] sequential = new byte[] { 1, 1, 1, 1 };
        multiplication(sequential, input, elements);
        for (int i = 0; i < result.length; i++) {
            assertEquals(sequential[i], result[i]);
        }
    }

    @Test
    public void testDivision() {

        byte[] elements = new byte[] { 4 };
        byte[] result = new byte[] { 8, 8, 8, 8 };
        byte[] input = new byte[] { 2, 2, 2, 2, 1, 1, 1, 1 };

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(result, input, elements)
                .task("t0", TestNumericPromotion::division, result, input, elements)
                .streamOut(result)
                .execute();
        //@formatter:on

        byte[] sequential = new byte[] { 8, 8, 8, 8 };
        division(sequential, input, elements);
        for (int i = 0; i < result.length; i++) {
            assertEquals(sequential[i], result[i]);
        }
    }

    @Test
    public void testSignedLeftShift() {

        byte[] elements = new byte[] { 4 };
        byte[] result = new byte[] { 8, 8, 8, 8 };
        byte[] input = new byte[] { 2, 2, 2, 2, 1, 1, 1, 1 };

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(result, input, elements)
                .task("t0", TestNumericPromotion::signedLeftShift, result, input, elements)
                .streamOut(result)
                .execute();
        //@formatter:on

        byte[] sequential = new byte[] { 8, 8, 8, 8 };
        signedLeftShift(sequential, input, elements);
        for (int i = 0; i < result.length; i++) {
            assertEquals(sequential[i], result[i]);
        }
    }

    @Test
    public void testSignedRightShift() {

        byte[] elements = new byte[] { 4 };
        byte[] result = new byte[] { 8, 8, 8, 8 };
        byte[] input = new byte[] { 2, 2, 2, 2, 1, 1, 1, 1 };

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(result, input, elements)
                .task("t0", TestNumericPromotion::signedRightShift, result, input, elements)
                .streamOut(result)
                .execute();
        //@formatter:on

        byte[] sequential = new byte[] { 8, 8, 8, 8 };
        signedRightShift(sequential, input, elements);
        for (int i = 0; i < result.length; i++) {
            assertEquals(sequential[i], result[i]);
        }
    }

    @Test
    public void testUnsignedRightShift() {

        byte[] elements = new byte[] { 4 };
        byte[] result = new byte[] { 8, 8, 8, 8 };
        byte[] input = new byte[] { 2, 2, 2, 2, 1, 1, 1, 1 };

        //@formatter:off
        new TaskGraph("s0")
                .streamIn(result, input, elements)
                .task("t0", TestNumericPromotion::unsignedRightShift, result, input, elements)
                .streamOut(result)
                .execute();
        //@formatter:on

        byte[] sequential = new byte[] { 8, 8, 8, 8 };
        unsignedRightShift(sequential, input, elements);
        for (int i = 0; i < result.length; i++) {
            assertEquals(sequential[i], result[i]);
        }
    }

}
