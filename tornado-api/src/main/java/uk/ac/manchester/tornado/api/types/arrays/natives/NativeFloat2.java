/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING. If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
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

/**
 * Represents a two-dimensional vector of floats with native memory allocation.
 * This class implements the {@link TornadoNativeCollectionsInterface} and provides
 * operations and functionality specific to two-float vectors.
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
public final class NativeFloat2 implements TornadoNativeCollectionsInterface<FloatBuffer> {

    public static final Class<NativeFloat2> TYPE = NativeFloat2.class;

    public static final Class<NativeVectorFloat> FIELD_CLASS = NativeVectorFloat.class;

    private static final int NUM_ELEMENTS = 2;

    @Payload
    final NativeVectorFloat nativeVector;

    /**
     * Constructs a new {@code NativeFloat2} vector with a speficied {@link NativeVectorFloat} object for storage.
     * @param nativeVector The {@link NativeVectorFloat} for backing this vector.
     */
    public NativeFloat2(NativeVectorFloat nativeVector) {
        this.nativeVector = nativeVector;
    }

    /**
     * Constructs a new {@code NativeFloat2} vector with a default native vector of floats
     * as the backing storage.
     */
    public NativeFloat2() {
        this(new NativeVectorFloat(2));
    }

    /**
     * Constructs a new {@code NativeFloat2} and initializes it with the provided values for each dimension.
     * @param x The float value for the x dimension.
     * @param y The float value for the y dimension.
     */
    public NativeFloat2(float x, float y) {
        this();
        setX(x);
        setY(y);
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the result of the addition of two {@code NativeFloat2} vectors.
     * @param a The first {@code NativeFloat2} vector for the addition.
     * @param b The second {@code NativeFloat2} vector for the addition.
     * @return A new {@code NativeFloat2} vector that contains the summation result.
     */
    public static NativeFloat2 add(NativeFloat2 a, NativeFloat2 b) {
        return new NativeFloat2(a.getX() + b.getX(), a.getY() + b.getY());
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the result of the subtraction of two {@code NativeFloat2} vectors.
     * @param a The first {@code NativeFloat2} vector for the subtraction.
     * @param b The second {@code NativeFloat2} vector for the subtraction.
     * @return A new {@code NativeFloat2} vector that contains the summation result.
     */
    public static NativeFloat2 sub(NativeFloat2 a, NativeFloat2 b) {
        return new NativeFloat2(a.getX() - b.getX(), a.getY() - b.getY());
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the result of the division of two {@code NativeFloat2} vectors.
     * @param a The divisible {@code NativeFloat2} vector.
     * @param b The divisor {@code NativeFloat2} vector.
     * @return A new {@code NativeFloat2} vector that contains the division result.
     */
    public static NativeFloat2 div(NativeFloat2 a, NativeFloat2 b) {
        return new NativeFloat2(a.getX() / b.getX(), a.getY() / b.getY());
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the result of the multiplication of two {@code NativeFloat2} vectors.
     * @param a The first {@code NativeFloat2} vector for the multiplication.
     * @param b The second {@code NativeFloat2} vector for the multiplication.
     * @return A new {@code NativeFloat2} vector that contains the multiplication result.
     */
    public static NativeFloat2 mult(NativeFloat2 a, NativeFloat2 b) {
        return new NativeFloat2(a.getX() * b.getX(), a.getY() * b.getY());
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains minimum value between each of the elements
     * of two {@code NativeFloat2} vectors.
     * @param a The first {@code NativeFloat2} vector for the comparison.
     * @param b The second {@code NativeFloat2} vector for the comparison.
     * @return A new {@code NativeFloat2} vector that contains the minimum values.
     */
    public static NativeFloat2 min(NativeFloat2 a, NativeFloat2 b) {
        return new NativeFloat2(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()));
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains maximum value between each of the elements
     * of two {@code NativeFloat2} vectors.
     * @param a The first {@code NativeFloat2} vector for the comparison.
     * @param b The second {@code NativeFloat2} vector for the comparison.
     * @return A new {@code NativeFloat2} vector that contains the maximum values.
     */
    public static NativeFloat2 max(NativeFloat2 a, NativeFloat2 b) {
        return new NativeFloat2(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()));
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the result of the addition of a given {@code NativeFloat2} vector and a scalar value.
     * @param a The {@code NativeFloat2} vector for the addition.
     * @param b The scalar float value.
     * @return A new {@code NativeFloat2} vector that contains the summation result.
     */
    public static NativeFloat2 add(NativeFloat2 a, float b) {
        return new NativeFloat2(a.getX() + b, a.getY() + b);
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the result of the subtraction of a scalar value from a given {@code NativeFloat2} vector.
     * @param a The {@code NativeFloat2} vector for the subtraction.
     * @param b The scalar float value.
     * @return A new {@code NativeFloat2} vector that contains the subtraction result.
     */
    public static NativeFloat2 sub(NativeFloat2 a, float b) {
        return new NativeFloat2(a.getX() - b, a.getY() - b);
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the result of the multiplication of a given {@code NativeFloat2} vector and a scalar value.
     * @param a The {@code NativeFloat2} vector for the multiplication.
     * @param b The scalar float value.
     * @return A new {@code NativeFloat2} vector that contains the multiplication result.
     */
    public static NativeFloat2 mult(NativeFloat2 a, float b) {
        return new NativeFloat2(a.getX() * b, a.getY() * b);
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the result of the division of the elements of {@code NativeFloat2} vector with a scalar value.
     * @param a The divisible {@code NativeFloat2} vector.
     * @param b The scalar divisor.
     * @return A new {@code NativeFloat2} vector that contains the division result.
     */
    public static NativeFloat2 div(NativeFloat2 a, float b) {
        return new NativeFloat2(a.getX() / b, a.getY() / b);
    }

    /**
     * Stores the results of the addition of two {@code NativeFloat2} vectors into a specified {@code NativeFloat2} vector.
     * @param a The first {@code NativeFloat2} vector for the addition.
     * @param b The second {@code NativeFloat2} vector for the addition.
     * @param c The {@code NativeFloat3} vector that contains the summation result.
     */
    public static void add(NativeFloat2 a, NativeFloat2 b, NativeFloat2 c) {
        c.setX(a.getX() + b.getX());
        c.setY(a.getY() + b.getY());
    }

    /**
     * Stores the results of the subtraction of two {@code NativeFloat2} vectors into a specified {@code NativeFloat2} vector.
     * @param a The first {@code NativeFloat2} vector for the subtraction.
     * @param b The second {@code NativeFloat2} vector for the subtraction.
     * @param c The {@code NativeFloat3} vector that contains the subtraction result.
     */
    public static void sub(NativeFloat2 a, NativeFloat2 b, NativeFloat2 c) {
        c.setX(a.getX() - b.getX());
        c.setY(a.getY() - b.getY());
    }

    /**
     * Stores the results of the multiplication of two {@code NativeFloat2} vectors into a specified {@code NativeFloat2} vector.
     * @param a The first {@code NativeFloat2} vector for the multiplication.
     * @param b The second {@code NativeFloat2} vector for the multiplication.
     * @param c The {@code NativeFloat3} vector that contains the multiplication result.
     */
    public static void mult(NativeFloat2 a, NativeFloat2 b, NativeFloat2 c) {
        c.setX(a.getX() * b.getX());
        c.setY(a.getY() * b.getY());
    }

    /**
     * Stores the results of the division of two {@code NativeFloat2} vectors into a specified {@code NativeFloat2} vector.
     * @param a The divisible {@code NativeFloat2} vector.
     * @param b The divisor {@code NativeFloat2} vector.
     * @param c The {@code NativeFloat3} vector that contains the division result.
     */
    public static void div(NativeFloat2 a, NativeFloat2 b, NativeFloat2 c) {
        c.setX(a.getX() / b.getX());
        c.setY(a.getY() / b.getY());
    }

    /**
     * Stores minimum value between each of the elements of two {@code NativeFloat2} vectors
     * into a specified {@code NativeFloat2} vector.
     * @param a The first {@code NativeFloat2} vector for the comparison.
     * @param b The second {@code NativeFloat2} vector for the comparison.
     * @param c The {@code NativeFloat3} vector that contains the minimum values.
     */
    public static void min(NativeFloat2 a, NativeFloat2 b, NativeFloat2 c) {
        c.setX(Math.min(a.getX(), b.getX()));
        c.setY(Math.min(a.getY(), b.getY()));
    }

    /**
     * Stores maximum value between each of the elements of two {@code NativeFloat2} vectors
     * into a specified {@code NativeFloat2} vector.
     * @param a The first {@code NativeFloat2} vector for the comparison.
     * @param b The second {@code NativeFloat2} vector for the comparison.
     * @param c The {@code NativeFloat3} vector that contains the maximum values.
     */
    public static void max(NativeFloat2 a, NativeFloat2 b, NativeFloat2 c) {
        c.setX(Math.max(a.getX(), b.getX()));
        c.setY(Math.max(a.getY(), b.getY()));
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the values of a given {@code NativeFloat2} vector incremented by a scalar value.
     * @param a The {@code NativeFloat2} vector that its values will be incremented.
     * @param value The scalar float value.
     * @return A new {@code NativeFloat2} vector that contains the incrementation result.
     */
    public static NativeFloat2 inc(NativeFloat2 a, float value) {
        return add(a, value);
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the values of a given {@code NativeFloat2} vector decreased by a scalar value.
     * @param a The {@code NativeFloat2} vector that its values will be decreased.
     * @param value The scalar float value.
     * @return A new {@code NativeFloat2} vector that contains the decrease result.
     */
    public static NativeFloat2 dec(NativeFloat2 a, float value) {
        return sub(a, value);
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the values of a given {@code NativeFloat2} vector scaled
     * the inverse of a specified scalar value.
     * @param a The {@code NativeFloat2} vector that its values will be scaled.
     * @param value The scalar float value.
     * @return A new {@code NativeFloat2} vector that contains the results of the scaling.
     */
    public static NativeFloat2 scaleByInverse(NativeFloat2 a, float value) {
        return mult(a, 1f / value);
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the values of a given {@code NativeFloat2} vector scaled by a scalar value.
     * @param a The {@code NativeFloat2} vector that its values will be scaled.
     * @param value The scalar float value.
     * @return A new {@code NativeFloat2} vector that contains the results of the scaling.
     */
    public static NativeFloat2 scale(NativeFloat2 a, float value) {
        return mult(a, value);
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the square root of the dimensions
     * of a given {@code NativeFloat2} vector.
     * @param a The input {@code NativeFloat2} vector.
     * @return A new {@code NativeFloat2} vector that contains the squared values.
     */
    public static NativeFloat2 sqrt(NativeFloat2 a) {
        return new NativeFloat2(TornadoMath.sqrt(a.getX()), TornadoMath.sqrt(a.getY()));
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the floor values of the dimensions
     * of a given {@code NativeFloat2} vector.
     * @param a The input {@code NativeFloat2} vector.
     * @return A new {@code NativeFloat2} vector that contains the floor values.
     */
    public static NativeFloat2 floor(NativeFloat2 a) {
        return new NativeFloat2(TornadoMath.floor(a.getX()), TornadoMath.floor(a.getY()));
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the fractional part of the dimensions
     * of a given {@code NativeFloat2} vector.
     * @param a The input {@code NativeFloat2} vector.
     * @return A new {@code NativeFloat2} vector that contains the fractional part of dimensions of the given vector.
     */
    public static NativeFloat2 fract(NativeFloat2 a) {
        return new NativeFloat2(TornadoMath.fract(a.getX()), TornadoMath.fract(a.getY()));
    }

    /**
     * Returns a new {@code NativeFloat2} vector which contains the values of a given {@code NativeFloat2}, clamped using specified min and max values.
     * @param x The {@code NativeFloat2} vector that will be clamped.
     * @param min The min value.
     * @param max The max value.
     * @return A new {@code NativeFloat2} vector that contains the clamping result.
     */
    public static void clamp(NativeFloat2 x, float min, float max) {
        x.setX(TornadoMath.clamp(x.getX(), min, max));
        x.setY(TornadoMath.clamp(x.getY(), min, max));
    }

    /**
     * Returns a normalized version of a given {@code NativeFloat2} vector.
     * @param value The {@code NativeFloat2} vector to be normalized.
     * @return A new {@code NativeFloat2} vector representing the normalized form of the input vector.
     */
    public static NativeFloat2 normalise(NativeFloat2 value) {
        return scaleByInverse(value, length(value));
    }

    /**
     * Returns the minimum value of the dimensions of a specified {@code NativeFloat2} vector.
     * @param value The {@code NativeFloat2} vector for which to find the minimum of its dimensions.
     * @return The minimum dimension.
     */
    public static float min(NativeFloat2 value) {
        return Math.min(value.getX(), value.getY());
    }

    /**
     * Returns the maximum value of the dimensions of a specified {@code NativeFloat2} vector.
     * @param value The {@code NativeFloat2} vector for which to find the maximum of its dimensions.
     * @return The maximum dimension.
     */
    public static float max(NativeFloat2 value) {
        return Math.max(value.getX(), value.getY());
    }

    /**
     * Returns the dot product of the dimensions of two given {@code NativeFloat2} vectors.
     * @param a The first {@code NativeFloat2} vector.
     * @param b The second {@code NativeFloat2} vector.
     * @return The dot product of the vectors.
     */
    public static float dot(NativeFloat2 a, NativeFloat2 b) {
        final NativeFloat2 m = mult(a, b);
        return m.getX() + m.getY();
    }

    /**
     * Returns the vector length of a given {@code NativeFloat2} vector.
     * @return The vector length.
     */
    public static float length(NativeFloat2 value) {
        return TornadoMath.sqrt(dot(value, value));
    }

    /**
     * Compares the values of two {@code NativeFloat2} vectors to deduct if they are equal.
     * @param a The first {@code NativeFloat2} vector for comparison.
     * @param b The second {@code NativeFloat2} vector for comparison.
     * @return True or false, depending if they are equal
     */
    public static boolean isEqual(NativeFloat2 a, NativeFloat2 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    /**
     * Creates a new {@code NativeFloat2} vector and loads the values for its dimensions
     * from a specified index of a given native {@link FloatArray}.
     * @param array The {@link FloatArray} from which to extract the {@code NativeFloat2} dimensions.
     * @param index The base index from which to extract the dimensions.
     * @return The new {@code NativeFloat2} vector.
     */
    static NativeFloat2 loadFromArray(final FloatArray array, int index) {
        final NativeFloat2 result = new NativeFloat2();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        return result;
    }

    /**
     * Gets the float value from a specified index.
     * @param index The index from which to get the value.
     * @return The float value at the specified index.
     */
    public float get(int index) {
        return nativeVector.get(index);
    }

    /**
     * Sets a float value at a specified index.
     * @param index The index to store the value.
     * @param value The float value to be stored.
     */
    public void set(int index, float value) {
        nativeVector.set(index, value);
    }

    /**
     * Sets the dimensions of the {@code NativeFloat2} vector to be the same as
     * those of a specified {@code NativeFloat2} vector.
     * @param value {@code NativeFloat2} vector to be copied.
     */
    public void set(NativeFloat2 value) {
        setX(value.getX());
        setY(value.getY());
    }

    /**
     * Get the first dimension of the {@code NativeFloat2} vector.
     * @return The first float dimension.
     */
    public float getX() {
        return get(0);
    }

    /**
     * Set the first dimension of the {@code NativeFloat2} vector with a specified value.
     * @param value The value that will be the first dimension.
     */
    public void setX(float value) {
        set(0, value);
    }

    /**
     * Get the second dimension of the {@code NativeFloat2} vector.
     * @return The second float dimension.
     */
    public float getY() {
        return get(1);
    }

    /**
     * Set the second dimension of the {@code NativeFloat2} vector with a specified value.
     * @param value The value that will be the second dimension.
     */
    public void setY(float value) {
        set(1, value);
    }

    /**
     * Duplicates this {@code NativeFloat2} vector.
     * @return A new {@code NativeFloat2} vector with the same values as this.
     */
    public NativeFloat2 duplicate() {
        NativeFloat2 vector = new NativeFloat2();
        vector.set(this);
        return vector;
    }

    /**
     * Converts the vector to a string using the specified format.
     * @param fmt The format string specifying the layout of the string.
     * @return A string representation of the vector.
     */
    public String toString(String fmt) {
        return String.format(fmt, getX(), getY());
    }

    /**
     * Converts the vector to a string using the default format.
     * @return A string representation of the vector.
     */
    @Override
    public String toString() {
        return toString(FloatOps.FMT_2);
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
        return nativeVector.getSegment().asByteBuffer().asFloatBuffer();
    }

    /**
     * Returns the number of dimensions of the {@code NativeFloat2} vector.
     * @return The number of dimensions, which in this case is two.
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
        return nativeVector.getSegment().toArray(JAVA_FLOAT);
    }

    /**
     * Sets the dimensions of the {@code NativeFloat2} vector from a given native {@link FloatArray}.
     * @param array The {@link FloatArray} from which to extract the {@code NativeFloat2} dimensions.
     * @param index The base index from which to extract the dimensions.
     */
    void storeToArray(final FloatArray array, int index) {
        array.set(index, getX());
        array.set(index + 1, getY());
    }

}
