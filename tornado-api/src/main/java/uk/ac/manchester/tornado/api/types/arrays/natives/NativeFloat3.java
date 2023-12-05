/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.types.arrays.natives;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import java.nio.FloatBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;
import uk.ac.manchester.tornado.api.types.vectors.Float2;

/**
 * Represents a three-dimensional vector of floats with native memory allocation.
 * This class implements the {@link TornadoNativeCollectionsInterface} and provides
 * operations and functionality specific to three-float vectors.
 *
 * <p>
 * The vector is backed by a {@link NativeVectorFloat}, and functions for addition,
 * subtraction, multiplication, and division with other vectors or scalar values are provided.
 * Additionally, methods for finding the minimum and maximum values, and checking equality with
 * other vectors are included.
 * </p>
 *
 * <p>
 * The class also provides methods for converting the vector to and from arrays,
 * loading data from and to {@link FloatBuffer}, and duplicating the vector.
 * </p>
 *
 * <p>
 * The constant {@code NUMBER_FORMAT} specifies the default format used when
 * converting the vector to a string.
 * </p>
 *
 */
@Vector
public final class NativeFloat3 implements TornadoNativeCollectionsInterface<FloatBuffer> {

    public static final Class<NativeFloat3> TYPE = NativeFloat3.class;

    private static final int NUM_ELEMENTS = 3;

    @Payload
    final NativeVectorFloat nativeVectorFloat;

    /**
     * Constructs a new {@code NativeFloat3} vector with a speficied {@link NativeVectorFloat} object for storage.
     * @param nativeVectorFloat The {@link NativeVectorFloat} for backing this vector.
     */
    public NativeFloat3(NativeVectorFloat nativeVectorFloat) {
        this.nativeVectorFloat = nativeVectorFloat;
    }

    /**
     * Constructs a new {@code NativeFloat3} vector with a default native vector of floats
     * as the backing storage.
     */
    public NativeFloat3() {
        this(new NativeVectorFloat(NUM_ELEMENTS));
    }

    /**
     * Constructs a new {@code NativeFloat3} and initializes it with the provided values for each dimension.
     * @param x The float value for the x dimension.
     * @param y The float value for the y dimension.
     * @param z The float value for the z dimension.
     */
    public NativeFloat3(float x, float y, float z) {
        this();
        setX(x);
        setY(y);
        setZ(z);
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the result of the addition of two {@code NativeFloat3} vectors.
     * @param a The first {@code NativeFloat3} vector for the addition.
     * @param b The second {@code NativeFloat3} vector for the addition.
     * @return A new {@code NativeFloat3} vector that contains the summation result.
     */
    public static NativeFloat3 add(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ());
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the result of the subtraction of two {@code NativeFloat3} vectors.
     * @param a The first {@code NativeFloat3} vector for the subtraction.
     * @param b The second {@code NativeFloat3} vector for the subtraction.
     * @return A new {@code NativeFloat3} vector that contains the summation result.
     */
    public static NativeFloat3 sub(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ());
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the result of the division of two {@code NativeFloat3} vectors.
     * @param a The divisible {@code NativeFloat3} vector.
     * @param b The divisor {@code NativeFloat3} vector.
     * @return A new {@code NativeFloat3} vector that contains the division result.
     */
    public static NativeFloat3 div(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(a.getX() / b.getX(), a.getY() / b.getY(), a.getZ() / b.getZ());
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the result of the multiplication of two {@code NativeFloat3} vectors.
     * @param a The first {@code NativeFloat3} vector for the multiplication.
     * @param b The second {@code NativeFloat3} vector for the multiplication.
     * @return A new {@code NativeFloat3} vector that contains the multiplication result.
     */
    public static NativeFloat3 mult(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(a.getX() * b.getX(), a.getY() * b.getY(), a.getZ() * b.getZ());
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains minimum value between each of the elements
     * of two {@code NativeFloat3} vectors.
     * @param a The first {@code NativeFloat3} vector for the comparison.
     * @param b The second {@code NativeFloat3} vector for the comparison.
     * @return A new {@code NativeFloat3} vector that contains the minimum values.
     */
    public static NativeFloat3 min(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains maximum value between each of the elements
     * of two {@code NativeFloat3} vectors.
     * @param a The first {@code NativeFloat3} vector for the comparison.
     * @param b The second {@code NativeFloat3} vector for the comparison.
     * @return A new {@code NativeFloat3} vector that contains the maximum values.
     */
    public static NativeFloat3 max(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the result of the cross multiplication of two {@code NativeFloat3} vectors.
     * @param a The first {@code NativeFloat3} vector for the cross multiplication.
     * @param b The second {@code NativeFloat3} vector for the cross multiplication.
     * @return A new {@code NativeFloat3} vector that contains the cross multiplication result.
     */
    public static NativeFloat3 cross(NativeFloat3 a, NativeFloat3 b) {
        return new NativeFloat3(a.getY() * b.getZ() - a.getZ() * b.getY(), a.getZ() * b.getX() - a.getX() * b.getZ(), a.getX() * b.getY() - a.getY() * b.getX());
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the result of the addition of a given {@code NativeFloat3} vector and a scalar value.
     * @param a The {@code NativeFloat3} vector for the addition.
     * @param b The scalar float value.
     * @return A new {@code NativeFloat3} vector that contains the summation result.
     */
    public static NativeFloat3 add(NativeFloat3 a, float b) {
        return new NativeFloat3(a.getX() + b, a.getY() + b, a.getZ() + b);
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the result of the subtraction of a scalar value from a given {@code NativeFloat3} vector.
     * @param a The {@code NativeFloat3} vector for the subtraction.
     * @param b The scalar float value.
     * @return A new {@code NativeFloat3} vector that contains the subtraction result.
     */
    public static NativeFloat3 sub(NativeFloat3 a, float b) {
        return new NativeFloat3(a.getX() - b, a.getY() - b, a.getZ() - b);
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the result of the multiplication of a given {@code NativeFloat3} vector and a scalar value.
     * @param a The {@code NativeFloat3} vector for the multiplication.
     * @param b The scalar float value.
     * @return A new {@code NativeFloat3} vector that contains the multiplication result.
     */
    public static NativeFloat3 mult(NativeFloat3 a, float b) {
        return new NativeFloat3(a.getX() * b, a.getY() * b, a.getZ() * b);
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the result of the division of the elements of {@code NativeFloat3} vector with a scalar value.
     * @param a The divisible {@code NativeFloat3} vector.
     * @param b The scalar divisor.
     * @return A new {@code NativeFloat3} vector that contains the division result.
     */
    public static NativeFloat3 div(NativeFloat3 a, float b) {
        return new NativeFloat3(a.getX() / b, a.getY() / b, a.getZ() / b);
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the values of a given {@code NativeFloat3} vector incremented by a scalar value.
     * @param a The {@code NativeFloat3} vector that its values will be incremented.
     * @param value The scalar float value.
     * @return A new {@code NativeFloat3} vector that contains the incrementation result.
     */
    public static NativeFloat3 inc(NativeFloat3 a, float value) {
        return new NativeFloat3(a.getX() + value, a.getY() + value, a.getZ() + value);
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the values of a given {@code NativeFloat3} vector decreased by a scalar value.
     * @param a The {@code NativeFloat3} vector that its values will be decreased.
     * @param value The scalar float value.
     * @return A new {@code NativeFloat3} vector that contains the decrease result.
     */
    public static NativeFloat3 dec(NativeFloat3 a, float value) {
        return new NativeFloat3(a.getX() - value, a.getY() - value, a.getZ() - value);
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the values of a given {@code NativeFloat3} vector scaled
     * the inverse of a specified scalar value.
     * @param a The {@code NativeFloat3} vector that its values will be scaled.
     * @param value The scalar float value.
     * @return A new {@code NativeFloat3} vector that contains the results of the scaling.
     */
    public static NativeFloat3 scaleByInverse(NativeFloat3 a, float value) {
        return mult(a, 1f / value);
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the values of a given {@code NativeFloat3} vector scaled by a scalar value.
     * @param a The {@code NativeFloat3} vector that its values will be scaled.
     * @param value The scalar float value.
     * @return A new {@code NativeFloat3} vector that contains the results of the scaling.
     */
    public static NativeFloat3 scale(NativeFloat3 a, float value) {
        return mult(a, value);
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the square root of the dimensions
     * of a given {@code NativeFloat3} vector.
     * @param a The input {@code NativeFloat3} vector.
     * @return A new {@code NativeFloat3} vector that contains the squared values.
     */
    public static NativeFloat3 sqrt(NativeFloat3 a) {
        return new NativeFloat3(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()), TornadoMath.sqrt(a.getZ()));
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the floor values of the dimensions
     * of a given {@code NativeFloat3} vector.
     * @param a The input {@code NativeFloat3} vector.
     * @return A new {@code NativeFloat3} vector that contains the floor values.
     */
    public static NativeFloat3 floor(NativeFloat3 a) {
        return new NativeFloat3(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()), TornadoMath.floor(a.getZ()));
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the fractional part of the dimensions
     * of a given {@code NativeFloat3} vector.
     * @param a The input {@code NativeFloat3} vector.
     * @return A new {@code NativeFloat3} vector that contains the fractional part of dimensions of the given vector.
     */
    public static NativeFloat3 fract(NativeFloat3 a) {
        return new NativeFloat3(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()), TornadoMath.fract(a.getZ()));
    }

    /**
     * Returns a new {@code NativeFloat3} vector which contains the values of a given {@code NativeFloat3}, clamped using specified min and max values.
     * @param x The {@code NativeFloat3} vector that will be clamped.
     * @param min The min value.
     * @param max The max value.
     * @return A new {@code NativeFloat3} vector that contains the clamping result.
     */
    public static NativeFloat3 clamp(NativeFloat3 x, float min, float max) {
        return new NativeFloat3(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max));
    }

    /**
     * Returns a normalized version of a given {@code NativeFloat3} vector.
     * @param value The {@code NativeFloat3} vector to be normalized.
     * @return A new {@code NativeFloat3} vector representing the normalized form of the input vector.
     */
    public static NativeFloat3 normalise(NativeFloat3 value) {
        final float len = 1f / length(value);
        return mult(value, len);
    }

    /**
     * Returns the minimum value of the dimensions of a specified {@code NativeFloat3} vector.
     * @param value The {@code NativeFloat3} vector for which to find the minimum of its dimensions.
     * @return The minimum dimension.
     */
    public static float min(NativeFloat3 value) {
        return Math.min(value.getX(), Math.min(value.getY(), value.getZ()));
    }

    /**
     * Returns the maximum value of the dimensions of a specified {@code NativeFloat3} vector.
     * @param value The {@code NativeFloat3} vector for which to find the maximum of its dimensions.
     * @return The maximum dimension.
     */
    public static float max(NativeFloat3 value) {
        return Math.max(value.getX(), Math.max(value.getY(), value.getZ()));
    }

    /**
     * Returns the dot product of the dimensions of two given {@code NativeFloat3} vectors.
     * @param a The first {@code NativeFloat3} vector.
     * @param b The second {@code NativeFloat3} vector.
     * @return The dot product of the vectors.
     */
    public static float dot(NativeFloat3 a, NativeFloat3 b) {
        final NativeFloat3 m = mult(a, b);
        return m.getX() + m.getY() + m.getZ();
    }

    /**
     * Returns the vector length of a given {@code NativeFloat3} vector.
     * @return The vector length.
     */
    public static float length(NativeFloat3 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    /**
     * Compares the values of two {@code NativeFloat3} vectors to deduct if they are equal.
     * @param a The first {@code NativeFloat3} vector for comparison.
     * @param b The second {@code NativeFloat3} vector for comparison.
     * @return True or false, depending if they are equal
     */
    public static boolean isEqual(NativeFloat3 a, NativeFloat3 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    /**
     * Compares the values of two {@code NativeFloat2} vectors to deduct if they are equal,
     * with a specified ULP tolerance.
     * @param a The first {@code NativeFloat2} vector for comparison.
     * @param b The second {@code NativeFloat2} vector for comparison.
     * @return The distance between the vectors.
     */
    public static boolean isEqualULP(NativeFloat3 a, NativeFloat3 b, float numULP) {
        return TornadoMath.isEqualULP(a.asBuffer().array(), b.asBuffer().array(), numULP);
    }

    /**
     * Calculates the distance between two {@code NativeFloat2} vectors taking
     * into account a specified ULP tolerance.
     * @param a The first {@code NativeFloat2} vector.
     * @param b The second {@code NativeFloat2} vector for comparison.
     * @return
     */
    public static float findULPDistance(NativeFloat3 a, NativeFloat3 b) {
        return TornadoMath.findULPDistance(a.asBuffer().array(), b.asBuffer().array());
    }

    /**
     * Returns the {@link NativeVectorFloat} associated with the {@code NativeFloat3} vector.
     * @return The {@link NativeVectorFloat} of the vector.
     */
    public NativeVectorFloat getArray() {
        return nativeVectorFloat;
    }

    /**
     * Gets the float value from a specified index.
     * @param index The index from which to get the value.
     * @return The float value at the specified index.
     */
    public float get(int index) {
        return nativeVectorFloat.get(index);
    }

    /**
     * Sets a float value at a specified index.
     * @param index The index to store the value.
     * @param value The float value to be stored.
     */
    public void set(int index, float value) {
        nativeVectorFloat.set(index, value);
    }

    /**
     * Sets the dimensions of the {@code NativeFloat3} vector to be the same as
     * those of a specified {@code NativeFloat3} vector.
     * @param value {@code NativeFloat3} vector to be copied.
     */
    public void set(NativeFloat3 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
    }

    /**
     * Get the first dimension of the {@code NativeFloat3} vector.
     * @return The first float dimension.
     */
    public float getX() {
        return get(0);
    }

    /**
     * Set the first dimension of the {@code NativeFloat3} vector with a specified value.
     * @param value The value that will be the first dimension.
     */
    public void setX(float value) {
        set(0, value);
    }

    /**
     * Get the second dimension of the {@code NativeFloat3} vector.
     * @return The second float dimension.
     */
    public float getY() {
        return get(1);
    }

    /**
     * Set the second dimension of the {@code NativeFloat3} vector with a specified value.
     * @param value The value that will be the second dimension.
     */
    public void setY(float value) {
        set(1, value);
    }

    /**
     * Get the third dimension of the {@code NativeFloat3} vector.
     * @return The second float dimension.
     */
    public float getZ() {
        return get(2);
    }

    /**
     * Set the third dimension of the {@code NativeFloat3} vector with a specified value.
     * @param value The value that will be the second dimension.
     */
    public void setZ(float value) {
        set(2, value);
    }

    /**
     * Get the first dimension of the {@code NativeFloat3} vector.
     * @return The first float dimension.
     */
    public float getS0() {
        return get(0);
    }

    /**
     * Set the first dimension of the {@code NativeFloat3} vector with a specified value.
     * @param value The value that will be the first dimension.
     */
    public void setS0(float value) {
        set(0, value);
    }

    /**
     * Get the second dimension of the {@code NativeFloat3} vector.
     * @return The second float dimension.
     */
    public float getS1() {
        return get(1);
    }

    /**
     * Set the second dimension of the {@code NativeFloat3} vector with a specified value.
     * @param value The value that will be the second dimension.
     */
    public void setS1(float value) {
        set(1, value);
    }

    /**
     * Get the third dimension of the {@code NativeFloat3} vector.
     * @return The second float dimension.
     */
    public float getS2() {
        return get(2);
    }

    /**
     * Set the third dimension of the {@code NativeFloat3} vector with a specified value.
     * @param value The value that will be the second dimension.
     */
    public void setS2(float value) {
        set(2, value);
    }

    /**
     * Duplicates this {@code NativeFloat3} vector.
     * @return A new {@code NativeFloat3} vector with the same values as this.
     */
    public NativeFloat3 duplicate() {
        final NativeFloat3 vector = new NativeFloat3();
        vector.set(this);
        return vector;
    }

    /**
     * Converts the vector to a string using the specified format.
     * @param fmt The format string specifying the layout of the string.
     * @return A string representation of the vector.
     */
    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ());
    }

    /**
     * Converts the vector to a string using the default format.
     * @return A string representation of the vector.
     */
    @Override
    public String toString() {
        return toString(FloatOps.FMT_3);
    }

    /**
     * Cast vector from {@code NativeFloat3} into a {@link NativeFloat2}.
     * @return {@link Float2}
     */
    public NativeFloat2 asNativeFloat2() {
        return new NativeFloat2(getX(), getY());
    }

    /**
     * Loads data from a specified {@link FloatBuffer}.
     * @param buffer The {@link FloatBuffer} from which to load the data.
     */
    @Override
    public void loadFromBuffer(FloatBuffer buffer) {
        asBuffer().put(buffer);
    }

    /**
     * Returns a {@link FloatBuffer} representation of underlying memory segment of the {@link NativeVectorFloat}.
     * @return A {@link FloatBuffer} view of the memory segment.
     */
    @Override
    public FloatBuffer asBuffer() {
        return nativeVectorFloat.getSegment().asByteBuffer().asFloatBuffer();
    }

    /**
     * Returns the number of dimensions of the {@code NativeFloat3} vector.
     * @return The number of dimensions, which in this case is three.
     */
    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    /**
     * Gets the data stored in the underlying memory segment in an on-heap array form.
     * @return An on-heap array that contains the data of the underlying memory segment.
     */
    public float[] toArray() {
        return nativeVectorFloat.getSegment().toArray(JAVA_FLOAT);
    }

    /**
     * Creates a new {@code NativeFloat3} vector and loads the values for its dimensions
     * from a specified index of a given native {@link FloatArray}.
     * @param array The {@link FloatArray} from which to extract the {@code NativeFloat3} dimensions.
     * @param index The base index from which to extract the dimensions.
     * @return The new {@code NativeFloat3} vector.
     */
    static NativeFloat3 loadFromArray(final FloatArray array, int index) {
        final NativeFloat3 result = new NativeFloat3();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        return result;
    }

    /**
     * Sets the dimensions of the {@code NativeFloat3} vector from a given native {@link FloatArray}.
     * @param array The {@link FloatArray} from which to extract the {@code NativeFloat3} dimensions.
     * @param index The base index from which to extract the dimensions.
     */
    void storeToArray(final FloatArray array, int index) {
        array.set(index, getX());
        array.set(index + 1, getY());
        array.set(index + 2, getZ());
    }

}
