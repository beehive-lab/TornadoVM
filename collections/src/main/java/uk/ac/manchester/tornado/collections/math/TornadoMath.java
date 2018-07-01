/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.collections.math;

import static java.lang.Float.MAX_EXPONENT;
import static java.lang.Float.floatToIntBits;
import static java.lang.Integer.toBinaryString;
import static uk.ac.manchester.tornado.collections.types.DoubleOps.compareBits;
import static uk.ac.manchester.tornado.collections.types.DoubleOps.compareULP;
import static uk.ac.manchester.tornado.collections.types.FloatOps.compare;

import uk.ac.manchester.tornado.collections.types.DoubleOps;
import uk.ac.manchester.tornado.collections.types.FloatOps;

public class TornadoMath {

    /**
     * *
     * Operations on scalars
     */
    public static float min(float a, float b) {
        return (a > b) ? b : a;
    }

    public static double min(double a, double b) {
        return (a > b) ? b : a;
    }

    public static long min(long a, long b) {
        return (a > b) ? b : a;
    }

    public static int min(int a, int b) {
        return (a > b) ? b : a;
    }

    public static short min(short a, short b) {
        return (a > b) ? b : a;
    }

    public static byte min(byte a, byte b) {
        return (a > b) ? b : a;
    }

    public static double max(double a, double b) {
        return (a > b) ? a : b;
    }

    public static float max(float a, float b) {
        return (a > b) ? a : b;
    }

    public static long max(long a, long b) {
        return (a > b) ? a : b;
    }

    public static int max(int a, int b) {
        return (a > b) ? a : b;
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

    public static double exp(double value) {
        return Math.exp(value);
    }

    public static float sqrt(float value) {
        return (float) Math.sqrt(value);
    }

    public static double sqrt(double value) {
        return Math.sqrt(value);
    }

    public final static long clamp(long val, long min, long max) {
        return Math.max(min, Math.min(max, val));
    }

    public final static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    public final static short clamp(short val, short min, short max) {
        return max(min, min(max, val));
    }

    public final static byte clamp(byte val, byte min, byte max) {
        return max(min, min(max, val));
    }

    public final static float clamp(float val, float min, float max) {
        return max(min, min(max, val));
    }

    public final static double clamp(double val, double min, double max) {
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

    public static boolean isEqual(float[] a, float[] b) {
        boolean result = true;
        for (int i = 0; i < a.length && result; i++) {
            result = compareBits(a[i], b[i]);
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

    public static double log(double value) {
        return Math.log(value);
    }

    public static float log2(float value) {
        return log(value) / log(2);
    }

    public static double log2(double value) {
        return Math.log(value) / Math.log(2);
    }

    public static float floatSin(float value) {
        return (float) Math.sin(value);
    }

    public static float floatCos(float value) {
        return (float) Math.cos(value);
    }

    public static float floatPI(){
        return (float) Math.PI;
    }

}
