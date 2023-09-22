/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.collections.math;

import static java.lang.Float.floatToIntBits;
import static java.lang.Integer.toBinaryString;
import static uk.ac.manchester.tornado.api.collections.types.DoubleOps.compareBits;
import static uk.ac.manchester.tornado.api.collections.types.DoubleOps.compareULP;
import static uk.ac.manchester.tornado.api.collections.types.FloatOps.compare;

import uk.ac.manchester.tornado.api.collections.types.DoubleOps;
import uk.ac.manchester.tornado.api.collections.types.FloatOps;

public class TornadoMath {

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

    public static float atan(float value) {
        return (float) Math.atan(value);
    }

    public static float tan(float value) {
        return (float) Math.tan(value);

    }

    public static float tanh(float value) {
        return (float) Math.tanh(value);
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

    public static float acos(float a) {
        return (float) Math.acos(a);
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

    public static float cos(float angle) {
        return (float) Math.cos(angle);
    }

    public static double cos(double angle) {
        return Math.cos(angle);
    }

    public static float sin(float angle) {
        return (float) Math.sin(angle);
    }

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

    public static float sinpi(float angle) {
        return (float) Math.sin(angle * Math.PI);
    }

    public static double sinpi(double angle) {
        return Math.sin(angle * Math.PI);
    }

    public static float cospi(float angle) {
        return (float) Math.cos(angle * Math.PI);
    }

    public static double cospi(double angle) {
        return Math.cos(angle * Math.PI);
    }
}
