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

import static java.lang.Float.floatToRawIntBits;
import static java.lang.Math.*;
import static tornado.collections.math.TornadoMath.findULPDistance;

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
