package uk.ac.manchester.tornado.examples.spirv;

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

}
