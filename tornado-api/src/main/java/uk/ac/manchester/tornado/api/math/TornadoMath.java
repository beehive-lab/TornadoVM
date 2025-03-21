/*
 * Copyright (c) 2013-2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.math;

import static java.lang.Float.floatToIntBits;
import static java.lang.Integer.toBinaryString;
import static uk.ac.manchester.tornado.api.types.utils.DoubleOps.compareBits;
import static uk.ac.manchester.tornado.api.types.utils.DoubleOps.compareULP;
import static uk.ac.manchester.tornado.api.types.utils.FloatOps.compare;
import static uk.ac.manchester.tornado.api.types.vectors.Float3.dot;

import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.utils.DoubleOps;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;
import uk.ac.manchester.tornado.api.types.vectors.Float16;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.vectors.Float8;

public class TornadoMath {

    public static float min(float a, float b) {
        return Math.min(a, b);
    }

    public static double min(double a, double b) {
        return Math.min(a, b);
    }

    public static long min(long a, long b) {
        return Math.min(a, b);
    }

    public static int min(int a, int b) {
        return Math.min(a, b);
    }

    public static short min(short a, short b) {
        return (a > b) ? b : a;
    }

    public static byte min(byte a, byte b) {
        return (a > b) ? b : a;
    }

    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    public static float max(float a, float b) {
        return Math.max(a, b);
    }

    public static long max(long a, long b) {
        return Math.max(a, b);
    }

    public static int max(int a, int b) {
        return Math.max(a, b);
    }

    public static short max(short a, short b) {
        return (a > b) ? a : b;
    }

    public static byte max(byte a, byte b) {
        return (a > b) ? a : b;
    }

    public static float abs(float a) {
        return Math.abs(a);
    }

    public static double abs(double a) {
        return Math.abs(a);
    }

    public static long abs(long a) {
        return Math.abs(a);
    }

    public static int abs(int a) {
        return Math.abs(a);
    }

    public static short abs(short a) {
        return (short) Math.abs(a);
    }

    public static byte abs(byte a) {
        return (byte) Math.abs(a);
    }

    public static float exp(float value) {
        return (float) Math.exp(value);
    }

    /**
     * In PTX, the exp operation that accepts a double input is narrowed to f32,
     * since the PTX instruction does not support f64 operands.
     */
    public static double exp(double value) {
        return Math.exp(value);
    }

    public static float sqrt(float value) {
        return (float) Math.sqrt(value);
    }

    public static double sqrt(double value) {
        return Math.sqrt(value);
    }

    public static long clamp(long val, long min, long max) {
        return Math.max(min, Math.min(max, val));
    }

    public static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    public static short clamp(short val, short min, short max) {
        return max(min, min(max, val));
    }

    public static byte clamp(byte val, byte min, byte max) {
        return max(min, min(max, val));
    }

    public static float clamp(float val, float min, float max) {
        return max(min, min(max, val));
    }

    public static double clamp(double val, double min, double max) {
        return max(min, min(max, val));
    }

    public static float floor(float f) {
        return (float) Math.floor(f);
    }

    public static double floor(double f) {
        return Math.floor(f);
    }

    public static float fract(float f) {
        return f - floor(f);
    }

    public static double fract(double f) {
        return f - floor(f);
    }

    public static Float2 cos(Float2 f) {
        return new Float2(TornadoMath.cos(f.getX()), TornadoMath.cos(f.getY()));
    }

    public static Float4 cos(Float4 f) {
        return new Float4(TornadoMath.cos(f.getX()), TornadoMath.cos(f.getY()), TornadoMath.cos(f.getZ()), TornadoMath.cos(f.getW()));
    }

    public static Float8 cos(Float8 f) {
        Float8 result = new Float8();
        for (int i = 0; i < f.size(); i++) {
            result.set(i, TornadoMath.cos(f.get(i)));
        }
        return result;
    }

    public static Float16 cos(Float16 f) {
        Float16 result = new Float16();
        for (int i = 0; i < f.size(); i++) {
            result.set(i, TornadoMath.cos(f.get(i)));
        }
        return result;
    }

    public static Float2 sin(Float2 f) {
        return new Float2(TornadoMath.sin(f.getX()), TornadoMath.sin(f.getY()));
    }

    public static Float4 sin(Float4 f) {
        return new Float4(TornadoMath.sin(f.getX()), TornadoMath.sin(f.getY()), TornadoMath.sin(f.getZ()), TornadoMath.sin(f.getW()));
    }

    public static Float8 sin(Float8 f) {
        Float8 result = new Float8();
        for (int i = 0; i < f.size(); i++) {
            result.set(i, TornadoMath.sin(f.get(i)));
        }
        return result;
    }

    public static Float16 sin(Float16 f) {
        Float16 result = new Float16();
        for (int i = 0; i < f.size(); i++) {
            result.set(i, TornadoMath.sin(f.get(i)));
        }
        return result;
    }

    public static boolean isEqual(float[] a, float[] b) {
        boolean result = true;
        for (int i = 0; i < a.length && result; i++) {
            result = compareBits(a[i], b[i]);
        }
        return result;
    }

    public static boolean isEqual(FloatArray a, FloatArray b) {
        boolean result = true;
        for (int i = 0; i < a.getSize() && result; i++) {
            result = compareBits(a.get(i), b.get(i));
        }
        return result;
    }

    public static boolean isEqual(IntArray a, IntArray b) {
        boolean result = true;
        for (int i = 0; i < a.getSize() && result; i++) {
            result = compareBits(a.get(i), b.get(i));
        }
        return result;
    }

    public static boolean isEqual(HalfFloatArray a, HalfFloatArray b) {
        boolean result = true;
        for (int i = 0; i < a.getSize() && result; i++) {
            result = compareBits(a.get(i).getHalfFloatValue(), b.get(i).getHalfFloatValue());
        }
        return result;
    }

    public static boolean isEqual(DoubleArray a, DoubleArray b) {
        boolean result = true;
        for (int i = 0; i < a.getSize() && result; i++) {
            result = compareBits(a.get(i), b.get(i));
        }
        return result;
    }

    public static boolean isEqual(double[] a, double[] b) {
        boolean result = true;
        for (int i = 0; i < a.length && result; i++) {
            result = compareBits(a[i], b[i]);
        }
        return result;
    }

    public static boolean isEqualULP(float[] value, float[] expected, float numULP) {
        boolean result = true;
        for (int i = 0; i < value.length && result; i++) {
            result = compareULP(value[i], expected[i], numULP);
        }
        return result;
    }

    public static boolean isEqualULP(double[] value, double[] expected, double numULP) {
        boolean result = true;
        for (int i = 0; i < value.length && result; i++) {
            result = compareULP(value[i], expected[i], numULP);
        }
        return result;
    }

    public static float findULPDistance(FloatArray value, FloatArray expected) {
        float maxULP = Float.MIN_VALUE;
        for (int i = 0; i < value.getSize(); i++) {
            maxULP = Math.max(maxULP, FloatOps.findMaxULP(value.get(i), expected.get(i)));
        }
        return maxULP;
    }

    public static double findULPDistance(DoubleArray value, DoubleArray expected) {
        double maxULP = Double.MIN_VALUE;
        for (int i = 0; i < value.getSize(); i++) {
            maxULP = Math.max(maxULP, DoubleOps.findMaxULP(value.get(i), expected.get(i)));
        }
        return maxULP;
    }

    public static float findULPDistance(float[] value, float[] expected) {
        float maxULP = Float.MIN_VALUE;
        for (int i = 0; i < value.length; i++) {
            maxULP = Math.max(maxULP, FloatOps.findMaxULP(value[i], expected[i]));
        }
        return maxULP;
    }

    public static double findULPDistance(double[] value, double[] expected) {
        double maxULP = Double.MIN_VALUE;
        for (int i = 0; i < value.length; i++) {
            maxULP = Math.max(maxULP, DoubleOps.findMaxULP(value[i], expected[i]));
        }
        return maxULP;
    }

    public static boolean isEqualTol(float[] a, float[] b, float tol) {
        boolean result = true;
        for (int i = 0; i < a.length && result; i++) {
            result = compare(a[i], b[i], tol);
        }
        return result;
    }

    public static boolean isEqual(int[] a, int[] b) {
        boolean result = true;
        for (int i = 0; i < a.length && result; i++) {
            result = a[i] == b[i];
        }
        return result;
    }

    public static boolean isEqual(short[] a, short[] b) {
        boolean result = true;
        for (int i = 0; i < a.length && result; i++) {
            result = a[i] == b[i];
        }
        return result;
    }

    public static boolean isEqual(byte[] a, byte[] b) {
        boolean result = true;
        for (int i = 0; i < a.length && result; i++) {
            result = a[i] == b[i];
        }
        return result;
    }

    public static String bitDiff(float[] a, float[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            sb.append(toBinaryString(floatToIntBits(a[i]) ^ floatToIntBits(b[i])));
            sb.append(" ");
        }
        return sb.toString();
    }

    public static float log(float value) {
        return (float) Math.log(value);
    }

    /**
     * In PTX, the log operation that accepts a double input is narrowed to f32,
     * since the PTX instruction does not support f64 operands.
     */
    public static double log(double value) {
        return Math.log(value);
    }

    public static float log2(float value) {
        return log(value) / log(2);
    }

    /**
     * In PTX, the log2 operation that accepts a double input is narrowed to f32,
     * since the PTX instruction does not support f64 operands.
     */
    public static double log2(double value) {
        return Math.log(value) / Math.log(2);
    }

    public static float atan(float value) {
        return (float) Math.atan(value);
    }

    public static double atan(double value) {
        return Math.atan(value);
    }

    public static float tan(float value) {
        return (float) Math.tan(value);
    }

    public static double tan(double value) {
        return Math.tan(value);
    }

    public static float tanh(float value) {
        return (float) Math.tanh(value);
    }

    public static double tanh(double value) {
        return Math.tanh(value);
    }

    public static float floatPI() {
        return (float) Math.PI;
    }

    public static double PI() {
        return Math.PI;
    }

    public static float pow(float a, float b) {
        return (float) Math.pow(a, b);
    }

    public static double pow(double a, double b) {
        return Math.pow(a, b);
    }

    public static float atan2(float a, float b) {
        return (float) Math.atan2(a, b);
    }

    public static double atan2(double a, double b) {
        return Math.atan2(a, b);
    }

    public static float acos(float a) {
        return (float) Math.acos(a);
    }

    public static float acosh(float x) {
        return (float) Math.log(x + Math.sqrt(x * x - 1));
    }

    public static double acosh(double x) {
        return Math.log(x + Math.sqrt(x * x - 1));
    }

    public static double acos(double a) {
        return Math.acos(a);
    }

    public static float asin(float a) {
        return (float) Math.asin(a);
    }

    public static double asin(double a) {
        return Math.asin(a);
    }

    public static float asinh(float x) {
        return (float) Math.log(x + Math.sqrt(x * x + 1));
    }

    public static double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1));
    }

    public static float cos(float angle) {
        return (float) Math.cos(angle);
    }

    /**
     * In PTX, the cos operation that accepts a double input is narrowed to f32,
     * since the PTX instruction does not support f64 operands.
     */
    public static double cos(double angle) {
        return Math.cos(angle);
    }

    public static float sin(float angle) {
        return (float) Math.sin(angle);
    }

    /**
     * In PTX, the sin operation that accepts a double input is narrowed to f32,
     * since the PTX instruction does not support f64 operands.
     */
    public static double sin(double angle) {
        return Math.sin(angle);
    }

    public static float signum(float a) {
        return Math.signum(a);
    }

    public static double signum(double a) {
        return Math.signum(a);
    }

    public static float toRadians(float angdeg) {
        return (float) Math.toRadians(angdeg);
    }

    public static double toRadians(double angdeg) {
        return Math.toRadians(angdeg);
    }

    public static float sinpi(float angle) {
        return (float) Math.sin(angle * Math.PI);
    }

    /**
     * In PTX, the sinpi operation that accepts a double input is narrowed to f32,
     * since the PTX sin instruction does not support f64 operands.
     */
    public static double sinpi(double angle) {
        return Math.sin(angle * Math.PI);
    }

    public static float cospi(float angle) {
        return (float) Math.cos(angle * Math.PI);
    }

    /**
     * In PTX, the cospi operation that accepts a double input is narrowed to f32,
     * since the PTX cos instruction does not support f64 operands.
     */
    public static double cospi(double angle) {
        return Math.cos(angle * Math.PI);
    }

    public static Float3 rotate(Matrix4x4Float m, Float3 x) {
        return new Float3(dot(m.row(0).asFloat3(), x), dot(m.row(1).asFloat3(), x), dot(m.row(2).asFloat3(), x));
    }

    public static float ceil(float value) {
        return (float) Math.ceil(value);
    }

    public static double ceil(double value) {
        return Math.ceil(value);
    }

}
