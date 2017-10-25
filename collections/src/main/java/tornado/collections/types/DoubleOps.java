/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.collections.types;

import static java.lang.Double.doubleToRawLongBits;
import static java.lang.Math.abs;
import static java.lang.Math.ulp;
import static tornado.collections.math.TornadoMath.findULPDistance;

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
