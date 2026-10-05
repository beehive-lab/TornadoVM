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

import java.util.function.DoubleBinaryOperator;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.LibraryTask7;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxScans;

/**
 * Unit tests for the MLX scan library tasks: cumsum, cumprod, cummax, cummin and logcumsumexp along
 * the middle axis of {@code [outer, len, inner]}, forward or reverse, inclusive or exclusive. Each
 * test checks the task against a sequential Java reference. Skipped unless the default device is on
 * the Metal backend and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxScans
 * </code>
 */
public class TestMlxScans extends MlxTestBase {

    private static final int OUTER = 9;
    private static final int LEN = 700;

    /** Scan of x viewed as [outer, len, inner] along the middle axis. */
    private static double[] scanJava(float[] x, int outer, int len, int inner, boolean reverse, boolean inclusive, double identity, DoubleBinaryOperator op) {
        double[] out = new double[x.length];
        for (int o = 0; o < outer; o++) {
            for (int j = 0; j < inner; j++) {
                double acc = identity;
                for (int k = 0; k < len; k++) {
                    int i = reverse ? len - 1 - k : k;
                    int at = (o * len + i) * inner + j;
                    if (inclusive) {
                        acc = op.applyAsDouble(acc, x[at]);
                        out[at] = acc;
                    } else {
                        out[at] = acc;
                        acc = op.applyAsDouble(acc, x[at]);
                    }
                }
            }
        }
        return out;
    }

    private static double logaddexp(double a, double b) {
        double m = Math.max(a, b);
        return m == Double.NEGATIVE_INFINITY ? m : m + Math.log1p(Math.exp(-Math.abs(a - b)));
    }

    private static void scan(LibraryTask7<FloatArray, FloatArray, Integer, Integer, Integer, Boolean, Boolean> mlx, int outer, int len, int inner, boolean reverse, boolean inclusive,
            double identity, DoubleBinaryOperator ref, float lo, float hi, double relTol, double absTol) throws TornadoExecutionPlanException {
        float[] xv = values(outer * len * inner, lo, hi, 1);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("scan", mlx, x, output, outer, len, inner, reverse, inclusive) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("scan", scanJava(xv, outer, len, inner, reverse, inclusive, identity, ref), output, relTol, absTol);
    }

    @Test
    public void testCumsum() throws TornadoExecutionPlanException {
        scan(MlxScans::cumsum, OUTER, LEN, 1, false, true, 0, Double::sum, -1, 1, 1e-4, 2e-4);
    }

    @Test
    public void testCumprod() throws TornadoExecutionPlanException {
        scan(MlxScans::cumprod, OUTER, LEN, 1, false, true, 1, (a, b) -> a * b, 0.999f, 1.001f, 1e-4, 1e-6);
    }

    @Test
    public void testCummax() throws TornadoExecutionPlanException {
        scan(MlxScans::cummax, OUTER, LEN, 1, false, true, Double.NEGATIVE_INFINITY, Math::max, -10, 10, 0, 0);
    }

    @Test
    public void testCummin() throws TornadoExecutionPlanException {
        scan(MlxScans::cummin, OUTER, LEN, 1, false, true, Double.POSITIVE_INFINITY, Math::min, -10, 10, 0, 0);
    }

    @Test
    public void testLogcumsumexp() throws TornadoExecutionPlanException {
        scan(MlxScans::logcumsumexp, OUTER, LEN, 1, false, true, Double.NEGATIVE_INFINITY, TestMlxScans::logaddexp, -3, 3, 1e-5, 1e-5);
    }

    @Test
    public void testCumsumReverse() throws TornadoExecutionPlanException {
        scan(MlxScans::cumsum, OUTER, LEN, 1, true, true, 0, Double::sum, -1, 1, 1e-4, 2e-4);
    }

    @Test
    public void testCumsumExclusive() throws TornadoExecutionPlanException {
        scan(MlxScans::cumsum, OUTER, LEN, 1, false, false, 0, Double::sum, -1, 1, 1e-4, 2e-4);
    }

    @Test
    public void testCummaxReverseExclusive() throws TornadoExecutionPlanException {
        scan(MlxScans::cummax, OUTER, LEN, 1, true, false, Double.NEGATIVE_INFINITY, Math::max, -10, 10, 0, 0);
    }

    @Test
    public void testCumsumOneLongRow() throws TornadoExecutionPlanException {
        scan(MlxScans::cumsum, 1, 5000, 1, false, true, 0, Double::sum, -1, 1, 1e-4, 2e-4);
    }

    @Test
    public void testCumsumStrided() throws TornadoExecutionPlanException {
        // Scans, unlike reductions, also run along a middle axis with inner > 1.
        scan(MlxScans::cumsum, 5, 40, 13, false, true, 0, Double::sum, -1, 1, 1e-4, 2e-4);
    }

    @Test
    public void testCumsumInt() throws TornadoExecutionPlanException {
        final int outer = 4;
        final int len = 300;
        int[] xv = new int[outer * len];
        for (int i = 0; i < xv.length; i++) {
            xv[i] = (i * 7919) % 23 - 11;
        }
        IntArray x = IntArray.fromArray(xv);
        IntArray output = new IntArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("cumsum", MlxScans::cumsum, x, output, outer, len, 1, false, true) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int o = 0; o < outer; o++) {
            int acc = 0;
            for (int i = 0; i < len; i++) {
                acc += xv[o * len + i];
                assertEquals(o + "," + i, acc, output.get(o * len + i));
            }
        }
    }

    @Test
    public void testCummaxHalf() throws TornadoExecutionPlanException {
        final int outer = 4;
        final int len = 300;
        HalfFloatArray x = half(values(outer * len, -5, 5, 2));
        HalfFloatArray output = new HalfFloatArray(outer * len);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("cummax", MlxScans::cummax, x, output, outer, len, 1, true, true) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("reverse cummax float16", scanJava(widen(x), outer, len, 1, true, true, Double.NEGATIVE_INFINITY, Math::max), output, 0, 0);
    }
}
