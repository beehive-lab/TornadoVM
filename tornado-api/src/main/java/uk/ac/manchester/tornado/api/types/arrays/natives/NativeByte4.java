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

import java.nio.ByteBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;

/**
 * Represents a four-dimensional vector of bytes with native memory allocation.
 * This class implements the {@link TornadoNativeCollectionsInterface} and provides
 * operations and functionality specific to four-byte vectors.
 *
 * <p>
 * The vector is backed by a {@link NativeVectorByte}, and functions for addition,
 * subtraction, multiplication, and division with other vectors or scalar values are provided.
 * Additionally, methods for finding the minimum and maximum values, and checking equality with
 * other vectors are included.
 * </p>
 *
 * <p>
 * The class also provides methods for converting the vector to and from arrays,
 * loading data from and to {@link ByteBuffer}, and duplicating the vector.
 * </p>
 *
 * <p>
 * The constant {@code NUMBER_FORMAT} specifies the default format used when
 * converting the vector to a string.
 * </p>
 *
 */
@Vector
public final class NativeByte4 implements TornadoNativeCollectionsInterface<ByteBuffer> {

    public static final Class<NativeByte4> TYPE = NativeByte4.class;
    private static final String NUMBER_FORMAT = "{ x=%-7d, y=%-7d, z=%-7d, w=%-7d }";

    private static final int NUM_ELEMENTS = 4;

    @Payload
    final NativeVectorByte nativeVectorByte;

    /**
     * Constructs a new {@code NativeByte4} vector with a speficied {@link NativeVectorByte} object for storage.
     * @param nativeVectorByte The {@link NativeVectorByte} for backing this vector.
     */
    public NativeByte4(NativeVectorByte nativeVectorByte) {
        this.nativeVectorByte = nativeVectorByte;
    }

    /**
     * Constructs a new {@code NativeByte4} vector with a default native vector of bytes
     * as the backing storage.
     */
    public NativeByte4() {
        this(new NativeVectorByte(NUM_ELEMENTS));
    }

    /**
     * Constructs a new {@code NativeByte4} and initializes it with the provided values for each dimension.
     * @param x The byte value for the x dimension.
     * @param y The byte value for the y dimension.
     * @param z The byte value for the z dimension.
     * @param w The byte value for the w dimension.
     */
    public NativeByte4(byte x, byte y, byte z, byte w) {
        this();
        setX(x);
        setY(y);
        setZ(z);
        setW(w);
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains the result of the addition of two {@code NativeByte4} vectors.
     * @param a The first {@code NativeByte4} vector for the addition.
     * @param b The second {@code NativeByte4} vector for the addition.
     * @return A new {@code NativeByte4} vector that contains the summation result.
     */
    public static NativeByte4 add(NativeByte4 a, NativeByte4 b) {
        return new NativeByte4((byte) (a.getX() + b.getX()), (byte) (a.getY() + b.getY()), (byte) (a.getZ() + b.getZ()), (byte) (a.getW() + b.getW()));
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains the result of the subtraction of two {@code NativeByte4} vectors.
     * @param a The first {@code NativeByte4} vector for the subtraction.
     * @param b The second {@code NativeByte4} vector for the subtraction.
     * @return A new {@code NativeByte4} vector that contains the summation result.
     */
    public static NativeByte4 sub(NativeByte4 a, NativeByte4 b) {
        return new NativeByte4((byte) (a.getX() - b.getX()), (byte) (a.getY() - b.getY()), (byte) (a.getZ() - b.getZ()), (byte) (a.getW() - b.getW()));
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains the result of the division of two {@code NativeByte4} vectors.
     * @param a The divisible {@code NativeByte4} vector.
     * @param b The divisor {@code NativeByte4} vector.
     * @return A new {@code NativeByte4} vector that contains the division result.
     */
    public static NativeByte4 div(NativeByte4 a, NativeByte4 b) {
        return new NativeByte4((byte) (a.getX() / b.getX()), (byte) (a.getY() / b.getY()), (byte) (a.getZ() / b.getZ()), (byte) (a.getW() / b.getW()));
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains the result of the multiplication of two {@code NativeByte4} vectors.
     * @param a The first {@code NativeByte4} vector for the multiplication.
     * @param b The second {@code NativeByte4} vector for the multiplication.
     * @return A new {@code NativeByte4} vector that contains the multiplication result.
     */
    public static NativeByte4 mult(NativeByte4 a, NativeByte4 b) {
        return new NativeByte4((byte) (a.getX() * b.getX()), (byte) (a.getY() * b.getY()), (byte) (a.getZ() * b.getZ()), (byte) (a.getW() * b.getW()));
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains minimum value between each of the elements
     * of two {@code NativeByte4} vectors.
     * @param a The first {@code NativeByte4} vector for the comparison.
     * @param b The second {@code NativeByte4} vector for the comparison.
     * @return A new {@code NativeByte4} vector that contains the minimum values.
     */
    public static NativeByte4 min(NativeByte4 a, NativeByte4 b) {
        return new NativeByte4(TornadoMath.min(a.getX(), b.getX()), TornadoMath.min(a.getY(), b.getY()), TornadoMath.min(a.getZ(), b.getZ()), TornadoMath.min(a.getW(), b.getW()));
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains maximum value between each of the elements
     * of two {@code NativeByte4} vectors.
     * @param a The first {@code NativeByte4} vector for the comparison.
     * @param b The second {@code NativeByte4} vector for the comparison.
     * @return A new {@code NativeByte4} vector that contains the maximum values.
     */
    public static NativeByte4 max(NativeByte4 a, NativeByte4 b) {
        return new NativeByte4(TornadoMath.max(a.getX(), b.getX()), TornadoMath.max(a.getY(), b.getY()), TornadoMath.max(a.getZ(), b.getZ()), TornadoMath.max(a.getW(), b.getW()));
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains the result of the addition of a given {@code NativeByte4} vector and a scalar value.
     * @param a The {@code NativeByte4} vector for the addition.
     * @param b The scalar byte value.
     * @return A new {@code NativeByte4} vector that contains the summation result.
     */
    public static NativeByte4 add(NativeByte4 a, byte b) {
        return new NativeByte4((byte) (a.getX() + b), (byte) (a.getY() + b), (byte) (a.getZ() + b), (byte) (a.getW() + b));
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains the result of the subtraction of a scalar value from a given {@code NativeByte4} vector.
     * @param a The {@code NativeByte4} vector for the subtraction.
     * @param b The scalar byte value.
     * @return A new {@code NativeByte4} vector that contains the subtraction result.
     */
    public static NativeByte4 sub(NativeByte4 a, byte b) {
        return new NativeByte4((byte) (a.getX() - b), (byte) (a.getY() - b), (byte) (a.getZ() - b), (byte) (a.getW() - b));
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains the result of the multiplication of a given {@code NativeByte4} vector and a scalar value.
     * @param a The {@code NativeByte4} vector for the multiplication.
     * @param b The scalar byte value.
     * @return A new {@code NativeByte4} vector that contains the multiplication result.
     */
    public static NativeByte4 mult(NativeByte4 a, byte b) {
        return new NativeByte4((byte) (a.getX() * b), (byte) (a.getY() * b), (byte) (a.getZ() * b), (byte) (a.getW() * b));
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains the result of the division of the elements of {@code NativeByte4} vector with a scalar value.
     * @param a The divisible {@code NativeByte4} vector.
     * @param b The scalar divisor.
     * @return A new {@code NativeByte4} vector that contains the division result.
     */
    public static NativeByte4 div(NativeByte4 a, byte b) {
        return new NativeByte4((byte) (a.getX() / b), (byte) (a.getY() / b), (byte) (a.getZ() / b), (byte) (a.getW() / b));
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains the values of a given {@code NativeByte4} vector incremented by a scalar value.
     * @param a The {@code NativeByte4} vector that its values will be incremented.
     * @param value The scalar byte value.
     * @return A new {@code NativeByte4} vector that contains the incrementation result.
     */
    public static NativeByte4 inc(NativeByte4 a, byte value) {
        return add(a, value);
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains the values of a given {@code NativeByte4} vector decreased by a scalar value.
     * @param a The {@code NativeByte4} vector that its values will be decreased.
     * @param value The scalar byte value.
     * @return A new {@code NativeByte4} vector that contains the decrease result.
     */
    public static NativeByte4 dec(NativeByte4 a, byte value) {
        return sub(a, value);
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains the values of a given {@code NativeByte4} vector scaled by a scalar value.
     * @param a The {@code NativeByte4} vector that its values will be scaled.
     * @param value The scalar byte value.
     * @return A new {@code NativeByte4} vector that contains the results of the scaling.
     */
    public static NativeByte4 scale(NativeByte4 a, byte value) {
        return mult(a, value);
    }

    /**
     * Returns a new {@code NativeByte4} vector which contains the values of a given {@code NativeByte4}, clamped using specified min and max values.
     * @param x The {@code NativeByte4} vector that will be clamped.
     * @param min The min value.
     * @param max The max value.
     * @return A new {@code NativeByte4} vector that contains the clamping result.
     */
    public static NativeByte4 clamp(NativeByte4 x, byte min, byte max) {
        return new NativeByte4(TornadoMath.clamp(x.getX(), min, max), TornadoMath.clamp(x.getY(), min, max), TornadoMath.clamp(x.getZ(), min, max), TornadoMath.clamp(x.getW(), min, max));
    }

    /**
     * Returns the minimum value of the dimensions of a specified {@code NativeByte4} vector.
     * @param value The {@code NativeByte4} vector for which to find the minimum of its dimensions.
     * @return The minimum dimension.
     */
    public static byte min(NativeByte4 value) {
        return TornadoMath.min(TornadoMath.min(value.getX(), value.getY()), TornadoMath.min(value.getZ(), value.getW()));
    }

    /**
     * Returns the maximum value of the dimensions of a specified {@code NativeByte4} vector.
     * @param value The {@code NativeByte4} vector for which to find the maximum of its dimensions.
     * @return The maximum dimension.
     */
    public static byte max(NativeByte4 value) {
        return TornadoMath.max(TornadoMath.max(value.getX(), value.getY()), TornadoMath.max(value.getZ(), value.getW()));
    }

    /**
     * Compares the values of two {@code NativeByte4} vectors to deduct if they are equal.
     * @param a The first {@code NativeByte4} vector for comparison.
     * @param b The second {@code NativeByte4} vector for comparison.
     * @return True or false, depending if they are equal
     */
    public static boolean isEqual(NativeByte4 a, NativeByte4 b) {
        return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
    }

    /**
     * Creates a new {@code NativeByte4} vector and loads the values for its dimensions
     * from a specified index of a given native {@link ByteArray}.
     * @param array The {@link ByteArray} from which to extract the {@code NativeByte4} dimensions.
     * @param index The base index from which to extract the dimensions.
     * @return The new {@code NativeByte4} vector.
     */
    static NativeByte4 loadFromArray(final ByteArray array, int index) {
        final NativeByte4 result = new NativeByte4();
        result.setX(array.get(index));
        result.setY(array.get(index + 1));
        result.setZ(array.get(index + 2));
        result.setW(array.get(index + 3));
        return result;
    }

    /**
     * Sets the dimensions of the {@code NativeByte4} vector to be the same as
     * those of a specified {@code NativeByte4} vector.
     * @param value {@code NativeByte4} vector to be copied.
     */
    public void set(NativeByte4 value) {
        setX(value.getX());
        setY(value.getY());
        setZ(value.getZ());
        setW(value.getW());
    }

    /**
     * Gets the byte value from a specified index.
     * @param index The index from which to get the value.
     * @return The byte value at the specified index.
     */
    public byte get(int index) {
        return nativeVectorByte.get(index);
    }

    /**
     * Sets a byte value at a specified index.
     * @param index The index to store the value.
     * @param value The byte value to be stored.
     */
    public void set(int index, byte value) {
        nativeVectorByte.set(index, value);
    }

    /**
     * Get the first dimension of the {@code NativeByte4} vector.
     * @return The first byte dimension.
     */
    public byte getX() {
        return get(0);
    }

    /**
     * Set the first dimension of the {@code NativeByte4} vector with a specified value.
     * @param value The value that will be the first dimension.
     */
    public void setX(byte value) {
        set(0, value);
    }

    /**
     * Get the second dimension of the {@code NativeByte4} vector.
     * @return The second byte dimension.
     */
    public byte getY() {
        return get(1);
    }

    /**
     * Set the second dimension of the {@code NativeByte4} vector with a specified value.
     * @param value The value that will be the second dimension.
     */
    public void setY(byte value) {
        set(1, value);
    }

    /**
     * Get the third dimension of the {@code NativeByte4} vector.
     * @return The third byte dimension.
     */
    public byte getZ() {
        return get(2);
    }

    /**
     * Set the third dimension of the {@code NativeByte4} vector with a specified value.
     * @param value The value that will be the third dimension.
     */
    public void setZ(byte value) {
        set(2, value);
    }

    /**
     * Get the fourth dimension of the {@code NativeByte4} vector.
     * @return The fourth byte dimension.
     */
    public byte getW() {
        return get(3);
    }

    /**
     * Set the fourth dimension of the {@code NativeByte4} vector with a specified value.
     * @param value The value that will be the fourth dimension.
     */
    public void setW(byte value) {
        set(3, value);
    }

    /**
     * Duplicates this {@code NativeByte3} vector.
     * @return A new {@code NativeByte3} vector with the same values as this.
     */
    public NativeByte4 duplicate() {
        NativeByte4 vector = new NativeByte4();
        vector.set(this);
        return vector;
    }

    /**
     * Converts the vector to a string using the specified format.
     * @param fmt The format string specifying the layout of the string.
     * @return A string representation of the vector.
     */
    public String toString(String fmt) {
        return String.format(fmt, getX(), getY(), getZ(), getW());
    }

    /**
     * Converts the vector to a string using the default format.
     * @return A string representation of the vector.
     */
    @Override
    public String toString() {
        return toString(NUMBER_FORMAT);
    }

    /**
     * Loads data from a specified {@link ByteBuffer}.
     * @param buffer The {@link ByteBuffer} from which to load the data.
     */
    @Override
    public void loadFromBuffer(ByteBuffer buffer) {
        asBuffer().put(buffer);
    }

    /**
     * Returns a {@link ByteBuffer} representation of underlying memory segment of the {@link NativeVectorByte}.
     * @return A {@link ByteBuffer} view of the memory segment.
     */
    @Override
    public ByteBuffer asBuffer() {
        return nativeVectorByte.getSegment().asByteBuffer();
    }

    /**
     * Returns the number of dimensions of the {@code NativeByte4} vector.
     * @return The number of dimensions, which in this case is four.
     */
    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    /**
     * Sets the dimensions of the {@code NativeByte4} vector from a given native {@link ByteArray}.
     * @param array The {@link ByteArray} from which to extract the {@code NativeByte4} dimensions.
     * @param index The base index from which to extract the dimensions.
     */
    void storeToArray(ByteArray array, int index) {
        array.set(index, getX());
        array.set(index + 1, getY());
        array.set(index + 2, getZ());
        array.set(index + 3, getW());
    }

}
