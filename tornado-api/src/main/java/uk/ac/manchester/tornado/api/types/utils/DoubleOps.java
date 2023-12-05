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
package uk.ac.manchester.tornado.api.types.utils;

import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.vectors.Double2;
import uk.ac.manchester.tornado.api.types.vectors.Double3;
import uk.ac.manchester.tornado.api.types.vectors.Double4;
import uk.ac.manchester.tornado.api.types.vectors.Double8;

public class DoubleOps {

    public static final double EPSILON = 1e-7f;
    public static final String FMT = "%.3f";
    public static final String FMT_2 = "{%.3f,%.3f}";
    public static final String FMT_3 = "{%.3f,%.3f,%.3f}";
    public static final String FMT_3_E = "{%.4e,%.4e,%.4e}";
    public static final String FMT_4 = "{%.3f,%.3f,%.3f,%.3f}";
    public static final String FMT_4_M = "%.3f,%.3f,%.3f,%.3f";
    public static final String FMT_4_EM = "%.3e,%.3e,%.3e,%.3e";
    public static final String FMT_6 = "{%.3f,%.3f,%.3f,%.3f,%.3f,%.3f}";
    public static final String FMT_8 = "{%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f}";
    public static final String FMT_16 = "{%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f}";
    public static final String FMT_6_E = "{%e,%e,%e,%e,%e,%e}";

    public static boolean compareBits(double a, double b) {
        long ai = Double.doubleToRawLongBits(a);
        long bi = Double.doubleToRawLongBits(b);

        long diff = ai ^ bi;
        return (diff == 0);
    }

    public static boolean compareULP(double value, double expected, double ulps) {
        final double tol = ulps * Math.ulp(expected);
        if (value == expected) {
            return true;
        }

        return Math.abs(value - expected) < tol;
    }

    public static double findMaxULP(Double2 value, Double2 expected) {
        return TornadoMath.findULPDistance(value.toArray(), expected.toArray());
    }

    public static double findMaxULP(Double3 value, Double3 expected) {
        return TornadoMath.findULPDistance(value.toArray(), expected.toArray());
    }

    public static double findMaxULP(Double4 value, Double4 expected) {
        return TornadoMath.findULPDistance(value.toArray(), expected.toArray());
    }

    public static double findMaxULP(Double8 value, Double8 expected) {
        return TornadoMath.findULPDistance(value.toArray(), expected.toArray());
    }

    public static double findMaxULP(double value, double expected) {
        final double ulp = Math.ulp(expected);

        if (value == expected) {
            return 0f;
        }

        final double absValue = Math.abs(value - expected);
        return absValue / ulp;
    }

    public static boolean compare(double a, double b) {
        return (Math.abs(a - b) <= EPSILON);
    }

    public static boolean compare(double a, double b, double tol) {
        return (Math.abs(a - b) <= tol);
    }

    public static double sq(double value) {
        return value * value;
    }

    public static void atomicAdd(double[] array, int index, double value) {
        array[index] += value;
    }
}
