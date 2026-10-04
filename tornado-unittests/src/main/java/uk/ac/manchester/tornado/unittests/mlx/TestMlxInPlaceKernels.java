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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.mlx.Mlx;
import uk.ac.manchester.tornado.mlx.MlxLogic;
import uk.ac.manchester.tornado.mlx.provider.MlxLibraryProvider;

/**
 * Element-wise MLX tasks that run MLX's own Metal kernels directly on TornadoVM's buffers, with no
 * MLX result array and no copy back. Covers both kernel forms (one element per thread below 65,536
 * elements, several above, with an uneven tail), float16 and bfloat16, and a comparison writing
 * bytes, and checks that the in-place route was taken.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxInPlaceKernels
 * </code>
 */
public class TestMlxInPlaceKernels extends MlxTestBase {

    private static final int[] SIZES = { 1, 1027, 65535, 65536, 65537, 1 << 20 };

    private static void assertInPlace(long before) {
        assertTrue("the task did not run as an in-place MLX kernel", MlxLibraryProvider.kernelDispatches() > before);
    }

    @Test
    public void testAddFloat() throws TornadoExecutionPlanException {
        for (int n : SIZES) {
            FloatArray a = FloatArray.fromArray(values(n, -5, 5, 1));
            FloatArray b = FloatArray.fromArray(values(n, -5, 5, 2));
            FloatArray c = new FloatArray(n);
            long before = MlxLibraryProvider.kernelDispatches();
            run(new TaskGraph("add").transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                    .libraryTask("t", Mlx::add, a, b, c) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, c));
            assertInPlace(before);
            for (int i = 0; i < n; i++) {
                assertEquals("add n=" + n + " element " + i, a.get(i) + b.get(i), c.get(i), 0.0f);
            }
        }
    }

    @Test
    public void testExpHalf() throws TornadoExecutionPlanException {
        for (int n : SIZES) {
            float[] v = values(n, -3, 3, 3);
            HalfFloatArray a = new HalfFloatArray(n);
            for (int i = 0; i < n; i++) {
                a.set(i, new HalfFloat(v[i]));
            }
            HalfFloatArray out = new HalfFloatArray(n);
            long before = MlxLibraryProvider.kernelDispatches();
            run(new TaskGraph("exp").transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                    .libraryTask("t", Mlx::exp, a, out) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
            assertInPlace(before);
            for (int i = 0; i < n; i++) {
                float expected = (float) Math.exp(a.get(i).getFloat32());
                assertEquals("exp n=" + n + " element " + i, expected, out.get(i).getFloat32(), 2e-3f * Math.max(1.0f, expected));
            }
        }
    }

    @Test
    public void testMultiplyBFloat16() throws TornadoExecutionPlanException {
        int n = 70001;
        float[] va = values(n, -4, 4, 4);
        float[] vb = values(n, -4, 4, 5);
        BFloat16Array a = new BFloat16Array(n);
        BFloat16Array b = new BFloat16Array(n);
        for (int i = 0; i < n; i++) {
            a.setFloat(i, va[i]);
            b.setFloat(i, vb[i]);
        }
        BFloat16Array c = new BFloat16Array(n);
        long before = MlxLibraryProvider.kernelDispatches();
        run(new TaskGraph("mul").transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("t", Mlx::multiply, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c));
        assertInPlace(before);
        for (int i = 0; i < n; i++) {
            float expected = a.getFloat(i) * b.getFloat(i);
            assertEquals("multiply element " + i, expected, c.getFloat(i), 1e-2f * Math.max(1.0f, Math.abs(expected)));
        }
    }

    @Test
    public void testLessWithNaNs() throws TornadoExecutionPlanException {
        int n = 100003;
        float[] va = values(n, -1, 1, 6);
        float[] vb = values(n, -1, 1, 7);
        for (int i = 0; i < n; i += 13) {
            va[i] = Float.NaN;
        }
        FloatArray a = FloatArray.fromArray(va);
        FloatArray b = FloatArray.fromArray(vb);
        ByteArray out = new ByteArray(n);
        long before = MlxLibraryProvider.kernelDispatches();
        run(new TaskGraph("less").transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .libraryTask("t", MlxLogic::less, a, b, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out));
        assertInPlace(before);
        for (int i = 0; i < n; i++) {
            assertEquals("less element " + i, va[i] < vb[i] ? 1 : 0, out.get(i));
        }
    }

    /** A chain of in-place kernels whose outputs feed the next task, as in a real graph. */
    @Test
    public void testChain() throws TornadoExecutionPlanException {
        int n = 1 << 18;
        FloatArray a = FloatArray.fromArray(values(n, 0.5f, 2, 8));
        FloatArray b = new FloatArray(n);
        FloatArray c = new FloatArray(n);
        FloatArray d = new FloatArray(n);
        run(new TaskGraph("chain").transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .libraryTask("t1", Mlx::sqrt, a, b) //
                .libraryTask("t2", Mlx::multiply, b, b, c) //
                .libraryTask("t3", Mlx::subtract, c, a, d) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, d));
        for (int i = 0; i < n; i++) {
            assertEquals("sqrt(a)^2 - a element " + i, 0.0f, d.get(i), 1e-5f * a.get(i));
        }
    }
}
