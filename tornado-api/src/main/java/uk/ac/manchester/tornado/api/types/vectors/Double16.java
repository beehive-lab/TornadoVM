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
public final class Double16 implements TornadoVectorsInterface<DoubleBuffer> {

    public static final Class<Double16> TYPE = Double16.class;
    /**
     * number of elements in the storage.
     */
    private static final int NUM_ELEMENTS = 16;
    /**
     * backing array.
     */
    @Payload
    final double[] storage;

    private Double16(double[] storage) {
        this.storage = storage;
    }

    public Double16() {
        this(new double[NUM_ELEMENTS]);
    }

    public Double16(double s0, double s1, double s2, double s3, double s4, double s5, double s6, double s7, double s8, double s9, double s10, double s11, double s12, double s13, double s14,
            double s15) {
        this();
        setS0(s0);
        setS1(s1);
        setS2(s2);
        setS3(s3);
        setS4(s4);
        setS5(s5);
        setS6(s6);
        setS7(s7);
        setS8(s8);
        setS9(s9);
        setS10(s10);
        setS11(s11);
        setS12(s12);
        setS13(s13);
        setS14(s14);
        setS15(s15);
    }

    /**
     * * Operations on Double16 vectors.
     */
    public static Double16 add(Double16 a, Double16 b) {
        final Double16 result = new Double16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b.get(i));
        }
        return result;
    }

    public static Double16 add(Double16 a, double b) {
        final Double16 result = new Double16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) + b);
        }
        return result;
    }

    public static Double16 sub(Double16 a, Double16 b) {
        final Double16 result = new Double16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b.get(i));
        }
        return result;
    }

    public static Double16 sub(Double16 a, double b) {
        final Double16 result = new Double16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) - b);
        }
        return result;
    }

    public static Double16 div(Double16 a, Double16 b) {
        final Double16 result = new Double16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / b.get(i));
        }
        return result;
    }

    public static Double16 div(Double16 a, double value) {
        final Double16 result = new Double16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) / value);
        }
        return result;
    }

    public static Double16 mult(Double16 a, Double16 b) {
        final Double16 result = new Double16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * b.get(i));
        }
        return result;
    }

    public static Double16 mult(Double16 a, double value) {
        final Double16 result = new Double16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, a.get(i) * value);
        }
        return result;
    }

    public static Double16 min(Double16 a, Double16 b) {
        final Double16 result = new Double16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.min(a.get(i), b.get(i)));
        }
        return result;
    }

    public static double min(Double16 value) {
        double result = Double.MAX_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.min(result, value.get(i));
        }
        return result;
    }

    public static Double16 max(Double16 a, Double16 b) {
        final Double16 result = new Double16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result.set(i, Math.max(a.get(i), b.get(i)));
        }
        return result;
    }

    public static double max(Double16 value) {
        double result = Double.MIN_VALUE;
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            result = Math.max(result, value.get(i));
        }
        return result;
    }

    public static Double16 sqrt(Double16 a) {
        final Double16 result = new Double16();
        for (int i = 0; i < NUM_ELEMENTS; i++) {
            a.set(i, TornadoMath.sqrt(a.get(i)));
        }
        return result;
    }

    public static double dot(Double16 a, Double16 b) {
        final Double16 m = mult(a, b);
        return m.getS0() + m.getS1() + m.getS2() + m.getS3() //
                + m.getS4() + m.getS5() + m.getS6() + m.getS7() //
                + m.getS8() + m.getS9() + m.getS10() + m.getS11() //
                + m.getS12() + m.getS13() + m.getS14() + m.getS15();
    }

    public static boolean isEqual(Double16 a, Double16 b) {
        return TornadoMath.isEqual(a.toArray(), b.toArray());
    }

    public static double findULPDistance(Double16 value, Double16 expected) {
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

    public void set(Double16 value) {
        for (int i = 0; i < 16; i++) {
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

    public double getS8() {
        return get(8);
    }

    public void setS8(double value) {
        set(8, value);
    }

    public double getS9() {
        return get(9);
    }

    public void setS9(double value) {
        set(9, value);
    }

    public double getS10() {
        return get(10);
    }

    public void setS10(double value) {
        set(10, value);
    }

    public double getS11() {
        return get(11);
    }

    public void setS11(double value) {
        set(11, value);
    }

    public double getS12() {
        return get(12);
    }

    public void setS12(double value) {
        set(12, value);
    }

    public double getS13() {
        return get(13);
    }

    private void setS13(double value) {
        set(13, value);
    }

    public double getS14() {
        return get(14);
    }

    public void setS14(double value) {
        set(14, value);
    }

    public double getS15() {
        return get(15);
    }

    public void setS15(double value) {
        set(15, value);
    }

    /**
     * Duplicates this vector.
     *
     * @return {@link Double16}
     */
    public Double16 duplicate() {
        Double16 vector = new Double16();
        vector.set(this);
        return vector;
    }

    public String toString(String fmt) {
        return String.format(fmt, getS0(), getS1(), getS2(), getS3(), getS4(), getS5(), getS6(), getS7(), getS8(), getS9(), getS10(), getS11(), getS12(), getS13(), getS13(), getS14(), getS15());
    }

    @Override
    public String toString() {
        return toString(DoubleOps.FMT_16);
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
