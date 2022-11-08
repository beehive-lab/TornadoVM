/*
 * Copyright (c) 2021, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.unittests.foundation;

import uk.ac.manchester.tornado.api.annotations.Parallel;

public class TestKernels {
    public static void copyTest(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 50;
        }
    }

    public static void addValue(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] += 50;
        }
    }

    public static void copyTest2(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i];
        }
    }

    public static void compute(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] + 50;
        }
    }

    public static void init(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 100;
            b[i] = 500;
        }
    }

    public static void vectorAddCompute(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] + c[i];
        }
    }

    public static void vectorMul(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] * c[i];
        }
    }

    public static void vectorSub(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] - c[i];
        }
    }

    public static void vectorDiv(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] / c[i];
        }
    }

    public static void vectorSquare(int[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] * b[i];
        }
    }

    public static void saxpy(int[] a, int[] b, int[] c, int alpha) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = alpha * b[i] + c[i];
        }
    }

    public static void copyTestZero(int[] a) {
        a[0] = 50;
    }

    public static void sum(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] + c[i];
        }
    }

    public static void vectorAddFloatCompute(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] + c[i];
        }
    }

    public static void vectorAddDoubleCompute(double[] a, double[] b, double[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] + c[i];
        }
    }

    public static void vectorMulDoubleCompute(double[] a, double[] b, double[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] * c[i];
        }
    }

    public static void vectorSubDoubleCompute(double[] a, double[] b, double[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] - c[i];
        }
    }

    public static void vectorDivDoubleCompute(double[] a, double[] b, double[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] / c[i];
        }
    }

    public static void vectorSubFloatCompute(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] - c[i];
        }
    }

    public static void vectorMulFloatCompute(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] * c[i];
        }
    }

    public static void vectorDivFloatCompute(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] / c[i];
        }
    }

    public static void testFloatCopy(float[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 50.0f;
        }
    }

    public static void testDoublesCopy(double[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 50;
        }
    }

    public static void testLongsCopy(long[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 50;
        }
    }

    public static void vectorSumLongCompute(long[] a, long[] b, long[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = b[i] + c[i];
        }
    }

    public static void vectorSumShortCompute(short[] a, short[] b, short[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = (short) (b[i] + c[i]);
        }
    }

    public static void testIfInt(int[] a) {
        if (a[0] == 0) {
            a[0] = 50;
        }
    }

    public static void testIfInt2(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            if (a[i] == 0) {
                a[i] = 50;
            }
        }
    }

    public static void testIfInt3(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            if (a[i] == 0) {
                a[i] = 50;
            } else {
                a[i] = 100;
            }
        }
    }

    public static void testIfInt4(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            if (a[i] > 0) {
                a[i] = 50;
            } else {
                a[i] = 100;
            }
        }
    }

    public static void testIfInt5(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            if (a[i] >= 0) {
                a[i] = 50;
            } else {
                a[i] = 100;
            }
        }
    }

    public static void testIfInt6(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            if (a[i] >= 0 && a[i] <= 1) { // Note : there is a bug with multiple conditions, && is not generated
                a[i] = 100;
            } else {
                a[i] = 200;
            }
        }
    }

}
