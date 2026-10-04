/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.mlx;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxReduce;

/**
 * The float16, bfloat16 and int32 forms of the MLX reductions, against Java references. (The
 * float32 forms are covered, with their JIT counterparts, by {@link TestJitReduce}.)
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxReduce
 * </code>
 */
public class TestMlxReduce extends MlxTestBase {

    private static final int OUTER = 6;
    private static final int LEN = 50;
    private static final int INNER = 1;

    /** Each [o, :, j] slice of v viewed as [OUTER, LEN, INNER]. */
    private static double[][] slices(float[] v) {
        double[][] s = new double[OUTER * INNER][LEN];
        for (int o = 0; o < OUTER; o++) {
            for (int j = 0; j < INNER; j++) {
                for (int i = 0; i < LEN; i++) {
                    s[o * INNER + j][i] = v[(o * LEN + i) * INNER + j];
                }
            }
        }
        return s;
    }

    private static int[] ints(int n, int lo, int hi, long seed) {
        Random r = new Random(seed);
        int[] v = new int[n];
        for (int i = 0; i < n; i++) {
            v[i] = lo + r.nextInt(hi - lo);
        }
        return v;
    }

    @Test
    public void testHalfAxis() throws TornadoExecutionPlanException {
        int n = OUTER * LEN * INNER;
        HalfFloatArray x = half(values(n, -1, 1, 71));
        HalfFloatArray sum = new HalfFloatArray(OUTER * INNER);
        HalfFloatArray mean = new HalfFloatArray(OUTER * INNER);
        HalfFloatArray max = new HalfFloatArray(OUTER * INNER);
        HalfFloatArray std = new HalfFloatArray(OUTER * INNER);
        run(new TaskGraph("ha").transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .libraryTask("sum", MlxReduce::sumAxis, x, sum, OUTER, LEN, INNER) //
                .libraryTask("mean", MlxReduce::meanAxis, x, mean, OUTER, LEN, INNER) //
                .libraryTask("max", MlxReduce::maxAxis, x, max, OUTER, LEN, INNER) //
                .libraryTask("std", MlxReduce::stdAxis, x, std, OUTER, LEN, INNER, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, sum, mean, max, std));
        double[][] s = slices(widen(x));
        double[] eSum = new double[s.length];
        double[] eMean = new double[s.length];
        double[] eMax = new double[s.length];
        double[] eStd = new double[s.length];
        for (int k = 0; k < s.length; k++) {
            double[] v = s[k];
            double m = Arrays.stream(v).average().getAsDouble();
            eSum[k] = Arrays.stream(v).sum();
            eMean[k] = m;
            eMax[k] = Arrays.stream(v).max().getAsDouble();
            eStd[k] = Math.sqrt(Arrays.stream(v).map(d -> (d - m) * (d - m)).sum() / (LEN - 1));
        }
        assertAllClose("sumAxis float16", eSum, sum, 3e-3, 1e-2);
        assertAllClose("meanAxis float16", eMean, mean, 3e-3, 1e-3);
        assertAllClose("maxAxis float16", eMax, max, 0, 0);
        assertAllClose("stdAxis float16", eStd, std, 3e-3, 1e-3);
    }

    @Test
    public void testBFloat16Whole() throws TornadoExecutionPlanException {
        int n = 4099;
        BFloat16Array x = bf16(values(n, -3, 3, 72));
        BFloat16Array min = new BFloat16Array(1);
        BFloat16Array lse = new BFloat16Array(1);
        BFloat16Array var = new BFloat16Array(1);
        run(new TaskGraph("bw").transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .libraryTask("min", MlxReduce::min, x, min) //
                .libraryTask("lse", MlxReduce::logsumexp, x, lse) //
                .libraryTask("var", MlxReduce::var, x, var, 0) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, min, lse, var));
        double[] v = new double[n];
        float[] w = widen(x);
        for (int i = 0; i < n; i++) {
            v[i] = w[i];
        }
        double mx = Arrays.stream(v).max().getAsDouble();
        double mean = Arrays.stream(v).average().getAsDouble();
        assertAllClose("min bfloat16", new double[] { Arrays.stream(v).min().getAsDouble() }, min, 0, 0);
        assertAllClose("logsumexp bfloat16", new double[] { mx + Math.log(Arrays.stream(v).map(d -> Math.exp(d - mx)).sum()) }, lse, 1e-2, 1e-2);
        assertAllClose("var bfloat16", new double[] { Arrays.stream(v).map(d -> (d - mean) * (d - mean)).sum() / n }, var, 1e-2, 1e-2);
    }

    @Test
    public void testIntAxes() throws TornadoExecutionPlanException {
        final int outer = 4;
        final int len1 = 5;
        final int len2 = 6;
        final int inner = 1;
        int[] xv = ints(outer * len1 * len2 * inner, -9, 10, 73);
        IntArray x = IntArray.fromArray(xv);
        IntArray sum = new IntArray(outer * inner);
        IntArray max = new IntArray(outer * inner);
        IntArray min = new IntArray(outer * inner);
        IntArray argmin = new IntArray(outer * inner);
        ByteArray any = new ByteArray(outer * inner);
        run(new TaskGraph("ia").transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .libraryTask("sum", MlxReduce::sumAxes, x, sum, outer, len1, len2, inner) //
                .libraryTask("max", MlxReduce::maxAxes, x, max, outer, len1, len2, inner) //
                .libraryTask("min", MlxReduce::minAxes, x, min, outer, len1, len2, inner) //
                .libraryTask("argmin", MlxReduce::argminAxis, x, argmin, outer, len1 * len2, inner) //
                .libraryTask("any", MlxReduce::anyAxes, x, any, outer, len1, len2, inner) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, sum, max, min, argmin, any));
        int len = len1 * len2;
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                int s = 0;
                int mx = Integer.MIN_VALUE;
                int mn = Integer.MAX_VALUE;
                int am = 0;
                boolean nz = false;
                for (int i = 0; i < len; i++) {
                    int v = xv[(o * len + i) * inner + j];
                    s += v;
                    mx = Math.max(mx, v);
                    if (v < mn) {
                        mn = v;
                        am = i;
                    }
                    nz |= v != 0;
                }
                int k = o * inner + j;
                assertEquals("sumAxes int32 " + k, s, sum.get(k));
                assertEquals("maxAxes int32 " + k, mx, max.get(k));
                assertEquals("minAxes int32 " + k, mn, min.get(k));
                assertEquals("argminAxis int32 " + k, am, argmin.get(k));
                assertEquals("anyAxes int32 " + k, nz ? 1 : 0, any.get(k));
            }
        }
    }

    @Test
    public void testIntWhole() throws TornadoExecutionPlanException {
        int[] xv = ints(1000, 1, 4, 74);
        IntArray x = IntArray.fromArray(xv);
        ByteArray all = new ByteArray(1);
        IntArray small = IntArray.fromElements(3, -2, 5, 1, -4);
        IntArray smallProd = new IntArray(1);
        run(new TaskGraph("iw").transferToDevice(DataTransferMode.FIRST_EXECUTION, x, small) //
                .libraryTask("all", MlxReduce::all, x, all) //
                .libraryTask("prod", MlxReduce::prod, small, smallProd) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, all, smallProd));
        assertEquals("all int32", 1, all.get(0));
        assertEquals("prod int32", 3 * -2 * 5 * 1 * -4, smallProd.get(0));
    }
}
