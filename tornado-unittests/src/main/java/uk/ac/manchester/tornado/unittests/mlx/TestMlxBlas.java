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

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.mlx.Mlx;

/**
 * MLX matrix multiplication library tasks, checked against a Java reference.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxBlas
 * </code>
 */
public class TestMlxBlas extends MlxTestBase {

    /** c[m, n] = a[m, k] @ b[k, n], with b given as [k, n] or, if transposed, as [n, k]. */
    private static double[] reference(float[] a, float[] b, int m, int k, int n, boolean bTransposed) {
        double[] c = new double[m * n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double sum = 0;
                for (int p = 0; p < k; p++) {
                    sum += (double) a[i * k + p] * (bTransposed ? b[j * k + p] : b[p * n + j]);
                }
                c[i * n + j] = sum;
            }
        }
        return c;
    }

    private static void matmulFloat(int m, int k, int n) throws TornadoExecutionPlanException {
        float[] av = values(m * k, -1, 1, 1);
        float[] bv = values(k * n, -1, 1, 2);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray c = new FloatArray(m * n);
        run(new TaskGraph("mm").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b).libraryTask("t", Mlx::matmul, a, b, c, m, k, n).transferToHost(DataTransferMode.EVERY_EXECUTION, c));
        assertAllClose("matmul float32 " + m + "x" + k + "x" + n, reference(av, bv, m, k, n, false), c, 1e-4, 1e-4);
    }

    @Test
    public void testMatmulFloat() throws TornadoExecutionPlanException {
        matmulFloat(37, 65, 29);
        matmulFloat(128, 256, 64);
    }

    @Test
    public void testMatmulHalfAndBFloat16() throws TornadoExecutionPlanException {
        final int m = 64, k = 96, n = 80;
        HalfFloatArray ha = half(values(m * k, -1, 1, 3));
        HalfFloatArray hb = half(values(k * n, -1, 1, 4));
        HalfFloatArray hc = new HalfFloatArray(m * n);
        run(new TaskGraph("mm16").transferToDevice(DataTransferMode.FIRST_EXECUTION, ha, hb).libraryTask("t", Mlx::matmul, ha, hb, hc, m, k, n)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, hc));
        assertAllClose("matmul float16", reference(widen(ha), widen(hb), m, k, n, false), hc, 2e-3, 2e-2);

        BFloat16Array ba = bf16(values(m * k, -1, 1, 5));
        BFloat16Array bb = bf16(values(k * n, -1, 1, 6));
        BFloat16Array bc = new BFloat16Array(m * n);
        run(new TaskGraph("mmbf").transferToDevice(DataTransferMode.FIRST_EXECUTION, ba, bb).libraryTask("t", Mlx::matmul, ba, bb, bc, m, k, n)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, bc));
        assertAllClose("matmul bfloat16", reference(widen(ba), widen(bb), m, k, n, false), bc, 1e-2, 1e-1);
    }

    @Test
    public void testMatmulTransposed() throws TornadoExecutionPlanException {
        final int m = 5, k = 64, n = 33;
        float[] av = values(m * k, -1, 1, 7);
        float[] wv = values(n * k, -1, 1, 8);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray w = FloatArray.fromArray(wv);
        FloatArray c = new FloatArray(m * n);
        run(new TaskGraph("mmt").transferToDevice(DataTransferMode.FIRST_EXECUTION, a, w).libraryTask("t", Mlx::matmulTransposed, a, w, c, m, k, n)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c));
        assertAllClose("matmulTransposed float32", reference(av, wv, m, k, n, true), c, 1e-4, 1e-4);

        HalfFloatArray ha = half(av);
        HalfFloatArray hw = half(wv);
        HalfFloatArray hc = new HalfFloatArray(m * n);
        run(new TaskGraph("mmt16").transferToDevice(DataTransferMode.FIRST_EXECUTION, ha, hw).libraryTask("t", Mlx::matmulTransposed, ha, hw, hc, m, k, n)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, hc));
        assertAllClose("matmulTransposed float16", reference(widen(ha), widen(hw), m, k, n, true), hc, 2e-3, 2e-2);
    }

    @Test
    public void testAddmm() throws TornadoExecutionPlanException {
        final int m = 17, k = 40, n = 23;
        final float alpha = 0.5f, beta = 2.0f;
        float[] cv = values(m * n, -1, 1, 9);
        float[] av = values(m * k, -1, 1, 10);
        float[] bv = values(k * n, -1, 1, 11);
        FloatArray cIn = FloatArray.fromArray(cv);
        FloatArray a = FloatArray.fromArray(av);
        FloatArray b = FloatArray.fromArray(bv);
        FloatArray out = new FloatArray(m * n);
        run(new TaskGraph("addmm").transferToDevice(DataTransferMode.FIRST_EXECUTION, cIn, a, b).libraryTask("t", Mlx::addmm, cIn, a, b, out, m, k, n, alpha, beta)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        double[] ab = reference(av, bv, m, k, n, false);
        double[] expected = new double[m * n];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = alpha * ab[i] + beta * cv[i];
        }
        assertAllClose("addmm float32", expected, out, 1e-4, 1e-4);
    }
}
