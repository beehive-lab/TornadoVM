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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.vectors.Float8;

public class FloatOps {

    public static final float EPSILON = 1e-7f;
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

    public static boolean compareBits(float a, float b) {
        long ai = Float.floatToRawIntBits(a);
        long bi = Float.floatToRawIntBits(b);

        long diff = ai ^ bi;
        return (diff == 0);
    }

    public static boolean compareULP(float value, float expected, float ulps) {
        final float tol = ulps * Math.ulp(expected);
        if (value == expected) {
            return true;
        }

        return Math.abs(value - expected) < tol;
    }

    public static float findMaxULP(Float2 value, Float2 expected) {
        return TornadoMath.findULPDistance(value.toArray(), expected.toArray());
    }

    public static float findMaxULP(Float3 value, Float3 expected) {
        return TornadoMath.findULPDistance(value.toArray(), expected.toArray());
    }

    public static float findMaxULP(Float4 value, Float4 expected) {
        return TornadoMath.findULPDistance(value.toArray(), expected.toArray());
    }

    public static float findMaxULP(FloatArray value, FloatArray expected) {
        return TornadoMath.findULPDistance(value, expected);
    }

    public static float findMaxULP(Float8 value, Float8 expected) {
        return TornadoMath.findULPDistance(value.toArray(), expected.toArray());
    }

    public static float findMaxULP(float value, float expected) {
        final float ulp = Math.ulp(expected);

        if (value == expected) {
            return 0f;
        }

        final float absValue = Math.abs(value - expected);
        return absValue / ulp;
    }

    public static boolean compare(float a, float b) {
        return (Math.abs(a - b) <= EPSILON);
    }

    public static boolean compare(float a, float b, float tol) {
        return (Math.abs(a - b) <= tol);
    }

    public static float sq(float value) {
        return value * value;
    }

    public static void atomicAdd(float[] array, int index, float value) {
        array[index] += value;
    }
}
