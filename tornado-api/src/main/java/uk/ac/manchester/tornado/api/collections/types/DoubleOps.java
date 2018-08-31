/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.api.collections.types;

import static java.lang.Double.doubleToRawLongBits;
import static java.lang.Math.abs;
import static java.lang.Math.ulp;
import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.findULPDistance;

public class DoubleOps {

    public static final double EPSILON = 1e-7f;
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

    public static final boolean compareBits(double a, double b) {
        long ai = doubleToRawLongBits(a);
        long bi = doubleToRawLongBits(b);

        long diff = ai ^ bi;
        return (diff == 0);
    }

    public static final boolean compareULP(double value, double expected, double ulps) {
        final double tol = ulps * ulp(expected);
        if (value == expected) {
            return true;
        }

        return abs(value - expected) < tol;
    }

    public static final double findMaxULP(Double2 value, Double2 expected) {
        return findULPDistance(value.storage, expected.storage);
    }

    public static final double findMaxULP(Double3 value, Double3 expected) {
        return findULPDistance(value.storage, expected.storage);
    }

    public static final double findMaxULP(Double4 value, Double4 expected) {
        return findULPDistance(value.storage, expected.storage);
    }

    public static final double findMaxULP(Double6 value, Double6 expected) {
        return findULPDistance(value.storage, expected.storage);
    }

    public static final double findMaxULP(Double8 value, Double8 expected) {
        return findULPDistance(value.storage, expected.storage);
    }

    public static final double findMaxULP(double value, double expected) {
        final double ULP = ulp(expected);

        if (value == expected) {
            return 0f;
        }

        final double absValue = abs(value - expected);
        return absValue / ULP;
    }

    public static final boolean compare(double a, double b) {
        return (abs(a - b) <= EPSILON);
    }

    public static final boolean compare(double a, double b, double tol) {
        return (abs(a - b) <= tol);
    }

    public static final double sq(double value) {
        return value * value;
    }

    public static final void atomicAdd(double[] array, int index, double value) {
        array[index] += value;
    }
}
