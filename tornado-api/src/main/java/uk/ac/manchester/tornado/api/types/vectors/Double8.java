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
package uk.ac.manchester.tornado.api.types.vectors;

import java.nio.DoubleBuffer;

import uk.ac.manchester.tornado.api.internal.annotations.Payload;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.utils.DoubleOps;

@Vector
public final class Double8 implements TornadoVectorsInterface<DoubleBuffer> {

    public static final Class<Double8> TYPE = Double8.class;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 8;
    /**
     * backing array.
     */
    @Payload
    final double[] storage;

    private Double8(double[] storage) {
        this.storage = storage;
    }

    public Double8() {
        this(new double[NUM_ELEMENTS]);
    }

    public Double8(double s0, double s1, double s2, double s3, double s4, double s5, double s6, double s7) {
        this();
        setS0(s0);
        setS1(s1);
        setS2(s2);
        setS3(s3);
        setS4(s4);
        setS5(s5);
        setS6(s6);
        setS7(s7);
    }

    /**
     * * Operations on Double8 vectors.
     */
    public static Double8 add(Double8 a, Double8 b) {
        final Double8 result = new Double8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Double8 add(Double8 a, double b) {
        final Double8 result = new Double8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static Double8 sub(Double8 a, Double8 b) {
        final Double8 result = new Double8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Double8 sub(Double8 a, double b) {
        final Double8 result = new Double8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static Double8 div(Double8 a, Double8 b) {
        final Double8 result = new Double8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Double8 div(Double8 a, double value) {
        final Double8 result = new Double8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / value);
        }
        return result;
    }

    public static Double8 mult(Double8 a, Double8 b) {
        final Double8 result = new Double8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Double8 mult(Double8 a, double value) {
        final Double8 result = new Double8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * value);
        }
        return result;
    }

    public static Double8 min(Double8 a, Double8 b) {
        final Double8 result = new Double8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static double min(Double8 value) {
        double result = Double.MAX_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static Double8 max(Double8 a, Double8 b) {
        final Double8 result = new Double8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static double max(Double8 value) {
        double result = Double.MIN_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static Double8 sqrt(Double8 a) {
        final Double8 result = new Double8();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            a.set(i, TornadoMath.sqrt(a.get(i)));
        }
        return result;
    }

    public static boolean isEqual(Double8 a, Double8 b) {
        return TornadoMath.isEqual(a.toArray(), b.toArray());
    }

    public static double findULPDistance(Double8 value, Double8 expected) {
        return TornadoMath.findULPDistance(value.asBuffer().array(), expected.asBuffer().array());
    }

    public double[] getArray() {
        return storage;
    }

    public double get(int index) {
        return storage[index];
    }

    public void set(int index, double value) {
        storage[index] = value;
    }

    public void set(Double8 value) {
        for (int i = 0; i < 8; i++) {
            set(i, value.get(i));
        }
    }

    public double getS0() {
        return get(0);
    }

    public void setS0(double value) {
        set(0, value);
    }

    public double getS1() {
        return get(1);
    }

    public void setS1(double value) {
        set(1, value);
    }

    public double getS2() {
        return get(2);
    }

    public void setS2(double value) {
        set(2, value);
    }

    public double getS3() {
        return get(3);
    }

    public void setS3(double value) {
        set(3, value);
    }

    public double getS4() {
        return get(4);
    }

    public void setS4(double value) {
        set(4, value);
    }

    public double getS5() {
        return get(5);
    }

    public void setS5(double value) {
        set(5, value);
    }

    public double getS6() {
        return get(6);
    }

    public void setS6(double value) {
        set(6, value);
    }

    public double getS7() {
        return get(7);
    }

    public void setS7(double value) {
        set(7, value);
    }

    public Double4 getHigh() {
        return new Double4(getS4(), getS5(), getS6(), getS7());
    }

    public Double4 getLow() {
        return new Double4(getS0(), getS1(), getS2(), getS3());
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link Double8}
     */
    public Double8 duplicate() {
        Double8 vector = new Double8();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getS0(), getS1(), getS2(), getS3(), getS4(), getS5(), getS6(), getS7());
    }

    @Override
    public String toString() {
        return toString(DoubleOps.FMT_8);
    }

    @Override
    public void loadFromBuffer(DoubleBuffer buffer) {
        asBuffer().put(buffer);
    }

    @Override
    public DoubleBuffer asBuffer() {
        return DoubleBuffer.wrap(storage);
    }

    @Override
    public int size() {
        return NUM_ELEMENTS;
    }

    public double[] toArray() {
        return storage;
    }

    @Override
    public long getNumBytes() {
        return NUM_ELEMENTS * 8;
    }
}
