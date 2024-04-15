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
package uk.ac.manchester.tornado.api.types;

import uk.ac.manchester.tornado.api.internal.annotations.HalfType;

/**
 * This class represents a float-16 instance (half float). The data is stored in a short field, to be
 * compliant with the representation for float-16 used in the {@link Float} class. The class encapsulates
 * methods for getting the data in float-16 and float-32 format, and for basic arithmetic operations (i.e.
 * addition, subtraction, multiplication and division).
 */
@HalfType
public class HalfFloat {

    private short halfFloatValue;

    /**
     * Constructs a new instance of the {@code HalfFloat} out of a float value.
     * To convert the float to a float-16, the floatToFloat16 function of the {@link Float}
     * class is used.
     *
     * @param halfFloat
     *     The float value that will be stored in a half-float format.
     */
    public HalfFloat(float halfFloat) {
        this.halfFloatValue = Float.floatToFloat16(halfFloat);
    }

    /**
     * Constructs a new instance of the {@code HalfFloat} with a given short value.
     *
     * @param halfFloat
     *     The short value that represents the half float.
     */
    public HalfFloat(short halfFloat) {
        this.halfFloatValue = halfFloat;
    }

    /**
     * Takes two half float values, converts them to a 32-bit representation and performs an addition.
     *
     * @param a
     *     The first float-16 input for the addition.
     * @param b
     *     The second float-16 input for the addition.
     * @return The result of the addition.
     */
    private static float addHalfFloat(short a, short b) {
        float floatA = Float.float16ToFloat(a);
        float floatB = Float.float16ToFloat(b);
        return floatA + floatB;
    }

    /**
     * Takes two {@code HalfFloat} objects and returns a new {@HalfFloat} instance
     * that contains the results of the addition.
     *
     * @param a
     *     The first {@code HalfFloat} input for the addition.
     * @param b
     *     The second {@code HalfFloat} input for the addition.
     * @return A new {@HalfFloat} containing the results of the addition.
     */
    public static HalfFloat add(HalfFloat a, HalfFloat b) {
        float result = addHalfFloat(a.getHalfFloatValue(), b.getHalfFloatValue());
        return new HalfFloat(result);
    }

    /**
     * Takes two half float values, converts them to a 32-bit representation and performs a subtraction.
     *
     * @param a
     *     The first float-16 input for the subtraction.
     * @param b
     *     The second float-16 input for the subtraction.
     * @return The result of the subtraction.
     */
    private static float subHalfFloat(short a, short b) {
        float floatA = Float.float16ToFloat(a);
        float floatB = Float.float16ToFloat(b);
        return floatA - floatB;
    }

    /**
     * Takes two {@code HalfFloat} objects and returns a new {@HalfFloat} instance
     * that contains the results of the subtraction.
     *
     * @param a
     *     The first {@code HalfFloat} input for the subtraction.
     * @param b
     *     The second {@code HalfFloat} input for the subtraction.
     * @return A new {@HalfFloat} containing the results of the subtraction.
     */
    public static HalfFloat sub(HalfFloat a, HalfFloat b) {
        float result = subHalfFloat(a.getHalfFloatValue(), b.getHalfFloatValue());
        return new HalfFloat(result);
    }

    /**
     * Takes two half float values, converts them to a 32-bit representation and performs a multiplication.
     *
     * @param a
     *     The first float-16 input for the multiplication.
     * @param b
     *     The second float-16 input for the multiplication.
     * @return The result of the multiplication.
     */
    private static float multHalfFloat(short a, short b) {
        float floatA = Float.float16ToFloat(a);
        float floatB = Float.float16ToFloat(b);
        return floatA * floatB;
    }

    /**
     * Takes two {@code HalfFloat} objects and returns a new {@HalfFloat} instance
     * that contains the results of the multiplication.
     *
     * @param a
     *     The first {@code HalfFloat} input for the multiplication.
     * @param b
     *     The second {@code HalfFloat} input for the multiplication.
     * @return A new {@HalfFloat} containing the results of the multiplication.
     */
    public static HalfFloat mult(HalfFloat a, HalfFloat b) {
        float result = multHalfFloat(a.getHalfFloatValue(), b.getHalfFloatValue());
        return new HalfFloat(result);
    }

    /**
     * Takes two half float values, converts them to a 32-bit representation and performs a division.
     *
     * @param a
     *     The first float-16 input for the division.
     * @param b
     *     The second float-16 input for the division.
     * @return The result of the division.
     */
    private static float divHalfFloat(short a, short b) {
        float floatA = Float.float16ToFloat(a);
        float floatB = Float.float16ToFloat(b);
        return floatA / floatB;
    }

    /**
     * Takes two {@code HalfFloat} objects and returns a new {@HalfFloat} instance
     * that contains the results of the division.
     *
     * @param a
     *     The first {@code HalfFloat} input for the division.
     * @param b
     *     The second {@code HalfFloat} input for the division.
     * @return A new {@HalfFloat} containing the results of the division.
     */
    public static HalfFloat div(HalfFloat a, HalfFloat b) {
        float result = divHalfFloat(a.getHalfFloatValue(), b.getHalfFloatValue());
        return new HalfFloat(result);
    }

    /**
     * Gets the half-float stored in the class.
     *
     * @return The half float value stored in the {@code HalfFloat} object.
     */
    public short getHalfFloatValue() {
        return this.halfFloatValue;
    }

    /**
     * Gets the half-float stored in the class in a 32-bit representation.
     *
     * @return The float-32 equivalent value the half float stored in the {@code HalfFloat} object.
     */
    public float getFloat32() {
        return Float.float16ToFloat(halfFloatValue);
    }

    @Override
    public String toString() {
        return String.format("HalfFloat: %.4f", getFloat32());
    }

}
