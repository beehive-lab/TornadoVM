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
package uk.ac.manchester.tornado.collections.types;

import static java.lang.Float.floatToRawIntBits;
import static java.lang.Math.abs;
import static java.lang.Math.ulp;
import static uk.ac.manchester.tornado.collections.math.TornadoMath.findULPDistance;

public class FloatOps {

    public static final float EPSILON = 1e-7f;
    public static final String fmt = "%.3f";
    public static final String fmt2 = "{%.3f,%.3f}";
    public static final String fmt3 = "{%.3f,%.3f,%.3f}";
    public static final String fmt3e = "{%.4e,%.4e,%.4e}";
    public static final String fmt4 = "{%.3f,%.3f,%.3f,%.3f}";
    public static final String fmt4m = "%.3f,%.3f,%.3f,%.3f";
    public static final String fmt4em = "%.3e,%.3e,%.3e,%.3e";
    public static final String fmt6 = "{%.3f,%.3f,%.3f,%.3f,%.3f,%.3f}";
    public static final String fmt8 = "{%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f}";
    public static final String fmt6e = "{%e,%e,%e,%e,%e,%e}";

    public static final boolean compareBits(float a, float b) {
        long ai = floatToRawIntBits(a);
        long bi = floatToRawIntBits(b);

        long diff = ai ^ bi;
        return (diff == 0);
    }

    public static final boolean compareULP(float value, float expected, float ulps) {
        final float tol = ulps * ulp(expected);
        if (value == expected) {
            return true;
        }

        return abs(value - expected) < tol;
    }

    public static final float findMaxULP(Float2 value, Float2 expected) {
        return findULPDistance(value.storage, expected.storage);
    }

    public static final float findMaxULP(Float3 value, Float3 expected) {
        return findULPDistance(value.storage, expected.storage);
    }

    public static final float findMaxULP(Float4 value, Float4 expected) {
        return findULPDistance(value.storage, expected.storage);
    }

    public static final float findMaxULP(Float6 value, Float6 expected) {
        return findULPDistance(value.storage, expected.storage);
    }

    public static final float findMaxULP(Float8 value, Float8 expected) {
        return findULPDistance(value.storage, expected.storage);
    }

    public static final float findMaxULP(float value, float expected) {
        final float ULP = ulp(expected);

        if (value == expected) {
            return 0f;
        }

        final float absValue = abs(value - expected);
        return absValue / ULP;
    }

    public static final boolean compare(float a, float b) {
        return (abs(a - b) <= EPSILON);
    }

    public static final boolean compare(float a, float b, float tol) {
        return (abs(a - b) <= tol);
    }

    public static final float sq(float value) {
        return value * value;
    }

    public static final void atomicAdd(float[] array, int index, float value) {
        array[index] += value;
    }
}
