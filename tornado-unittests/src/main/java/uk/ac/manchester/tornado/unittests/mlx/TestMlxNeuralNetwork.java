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
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.mlx.MlxNeuralNetwork;

/**
 * Unit tests for the MLX neural-network library tasks (mlx.fast): RMS norm, layer norm, RoPE with a
 * fixed or device-side position, and scaled dot-product attention with grouped-query heads. Each
 * test checks the task against a sequential Java reference. Skipped unless the default device is on
 * the Metal backend and mlx.metallib is available.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxNeuralNetwork
 * </code>
 */
public class TestMlxNeuralNetwork extends MlxTestBase {

    private static final float EPS = 1e-5f;

    private static double[] rmsNormJava(float[] x, float[] w, int rows, int dim, float eps) {
        double[] out = new double[rows * dim];
        for (int r = 0; r < rows; r++) {
            double ss = 0;
            for (int i = 0; i < dim; i++) {
                ss += (double) x[r * dim + i] * x[r * dim + i];
            }
            double inv = 1.0 / Math.sqrt(ss / dim + eps);
            for (int i = 0; i < dim; i++) {
                out[r * dim + i] = x[r * dim + i] * inv * w[i];
            }
        }
        return out;
    }

    /** RoPE of x[batch, heads, seqLen, headDim] at positions offset + l. */
    private static double[] ropeJava(float[] x, int batch, int heads, int seqLen, int headDim, int dims, boolean traditional, float base, float scale, int offset) {
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = x[i];
        }
        int half = dims / 2;
        for (int b = 0; b < batch; b++) {
            for (int h = 0; h < heads; h++) {
                for (int l = 0; l < seqLen; l++) {
                    int row = ((b * heads + h) * seqLen + l) * headDim;
                    double pos = (offset + l) * (double) scale;
                    for (int i = 0; i < half; i++) {
                        double theta = pos * Math.pow(base, -2.0 * i / dims);
                        int i1 = traditional ? 2 * i : i;
                        int i2 = traditional ? 2 * i + 1 : i + half;
                        double x1 = x[row + i1];
                        double x2 = x[row + i2];
                        out[row + i1] = x1 * Math.cos(theta) - x2 * Math.sin(theta);
                        out[row + i2] = x1 * Math.sin(theta) + x2 * Math.cos(theta);
                    }
                }
            }
        }
        return out;
    }

    /** softmax(q k^T * scale) v per head, with grouped-query heads and a causal mask aligned to the end. */
    private static double[] attentionJava(float[] q, float[] k, float[] v, int batch, int qHeads, int kvHeads, int qLen, int kvLen, int headDim, float scale, boolean causal) {
        double[] out = new double[batch * qHeads * qLen * headDim];
        int group = qHeads / kvHeads;
        for (int b = 0; b < batch; b++) {
            for (int h = 0; h < qHeads; h++) {
                int kh = h / group;
                for (int i = 0; i < qLen; i++) {
                    double[] scores = new double[kvLen];
                    double max = Double.NEGATIVE_INFINITY;
                    for (int j = 0; j < kvLen; j++) {
                        if (causal && j > i + (kvLen - qLen)) {
                            scores[j] = Double.NEGATIVE_INFINITY;
                            continue;
                        }
                        double dot = 0;
                        for (int d = 0; d < headDim; d++) {
                            dot += (double) q[((b * qHeads + h) * qLen + i) * headDim + d] * k[((b * kvHeads + kh) * kvLen + j) * headDim + d];
                        }
                        scores[j] = dot * scale;
                        max = Math.max(max, scores[j]);
                    }
                    double sum = 0;
                    for (int j = 0; j < kvLen; j++) {
                        scores[j] = Double.isInfinite(scores[j]) ? 0 : Math.exp(scores[j] - max);
                        sum += scores[j];
                    }
                    for (int d = 0; d < headDim; d++) {
                        double acc = 0;
                        for (int j = 0; j < kvLen; j++) {
                            acc += scores[j] / sum * v[((b * kvHeads + kh) * kvLen + j) * headDim + d];
                        }
                        out[((b * qHeads + h) * qLen + i) * headDim + d] = acc;
                    }
                }
            }
        }
        return out;
    }

    private static void rope(boolean traditional, int dims) throws TornadoExecutionPlanException {
        final int batch = 1;
        final int heads = 4;
        final int seqLen = 3;
        final int headDim = 64;
        final int offset = 5;
        final float base = 10000f;
        float[] xv = values(batch * heads * seqLen * headDim, -1, 1, dims + (traditional ? 1 : 0));
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray output = new FloatArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("rope", MlxNeuralNetwork::rope, x, output, batch, heads, seqLen, headDim, dims, traditional, base, 1f, offset) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("rope traditional=" + traditional + " dims=" + dims, ropeJava(xv, batch, heads, seqLen, headDim, dims, traditional, base, 1f, offset), output, 1e-4, 1e-5);
    }

    private static void attention(int qLen, boolean causal) throws TornadoExecutionPlanException {
        final int batch = 1;
        final int qHeads = 8;
        final int kvHeads = 2;
        final int kvLen = 17;
        final int headDim = 64;
        final float scale = (float) (1.0 / Math.sqrt(headDim));
        float[] qv = values(batch * qHeads * qLen * headDim, -1, 1, 10L + qLen);
        float[] kv = values(batch * kvHeads * kvLen * headDim, -1, 1, 11);
        float[] vv = values(batch * kvHeads * kvLen * headDim, -1, 1, 12);
        FloatArray q = FloatArray.fromArray(qv);
        FloatArray k = FloatArray.fromArray(kv);
        FloatArray v = FloatArray.fromArray(vv);
        FloatArray output = new FloatArray(qv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, q, k, v) //
                .libraryTask("sdpa", MlxNeuralNetwork::scaledDotProductAttention, q, k, v, output, batch, qHeads, kvHeads, qLen, kvLen, headDim, scale, causal) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("sdpa qLen=" + qLen + " causal=" + causal, attentionJava(qv, kv, vv, batch, qHeads, kvHeads, qLen, kvLen, headDim, scale, causal), output, 1e-4, 1e-5);
    }

    @Test
    public void testRmsNorm() throws TornadoExecutionPlanException {
        final int rows = 7;
        final int dim = 1000;
        float[] xv = values(rows * dim, -2, 2, 1);
        float[] wv = values(dim, 0.5f, 1.5f, 2);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray weight = FloatArray.fromArray(wv);
        FloatArray output = new FloatArray(rows * dim);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, weight) //
                .libraryTask("rmsNorm", MlxNeuralNetwork::rmsNorm, x, weight, output, rows, dim, EPS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("rmsNorm", rmsNormJava(xv, wv, rows, dim, EPS), output, 1e-5, 1e-6);
    }

    @Test
    public void testRmsNormWideRows() throws TornadoExecutionPlanException {
        // 4096 and 8192 are LLM hidden sizes; a row wider than one threadgroup takes MLX's looped kernel.
        for (int[] shape : new int[][] { { 1, 4096 }, { 3, 8192 } }) {
            int rows = shape[0];
            int dim = shape[1];
            float[] xv = values(rows * dim, -2, 2, 3);
            float[] wv = values(dim, 0.5f, 1.5f, 4);
            FloatArray x = FloatArray.fromArray(xv);
            FloatArray weight = FloatArray.fromArray(wv);
            FloatArray output = new FloatArray(rows * dim);

            TaskGraph taskGraph = new TaskGraph("g") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, weight) //
                    .libraryTask("rmsNorm", MlxNeuralNetwork::rmsNorm, x, weight, output, rows, dim, EPS) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.execute();
            }

            assertAllClose("rmsNorm " + rows + "x" + dim, rmsNormJava(xv, wv, rows, dim, EPS), output, 1e-5, 1e-6);
        }
    }

    @Test
    public void testLayerNorm() throws TornadoExecutionPlanException {
        final int rows = 5;
        final int dim = 777;
        float[] xv = values(rows * dim, -3, 3, 5);
        float[] wv = values(dim, 0.5f, 1.5f, 6);
        float[] bv = values(dim, -0.5f, 0.5f, 7);
        FloatArray x = FloatArray.fromArray(xv);
        FloatArray weight = FloatArray.fromArray(wv);
        FloatArray bias = FloatArray.fromArray(bv);
        FloatArray output = new FloatArray(rows * dim);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, weight, bias) //
                .libraryTask("layerNorm", MlxNeuralNetwork::layerNorm, x, weight, bias, output, rows, dim, EPS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        double[] expected = new double[rows * dim];
        for (int r = 0; r < rows; r++) {
            double mean = 0;
            for (int i = 0; i < dim; i++) {
                mean += xv[r * dim + i];
            }
            mean /= dim;
            double var = 0;
            for (int i = 0; i < dim; i++) {
                double d = xv[r * dim + i] - mean;
                var += d * d;
            }
            var /= dim;
            for (int i = 0; i < dim; i++) {
                expected[r * dim + i] = (xv[r * dim + i] - mean) / Math.sqrt(var + EPS) * wv[i] + bv[i];
            }
        }
        assertAllClose("layerNorm", expected, output, 1e-4, 1e-5);
    }

    @Test
    public void testRope() throws TornadoExecutionPlanException {
        rope(false, 64);
    }

    @Test
    public void testRopeTraditional() throws TornadoExecutionPlanException {
        rope(true, 64);
    }

    @Test
    public void testRopeRotatesOnlyTheFirstDims() throws TornadoExecutionPlanException {
        rope(false, 32);
    }

    @Test
    public void testRopeDynamic() throws TornadoExecutionPlanException {
        // The position comes from a device IntArray, so one graph serves every decode step.
        final int batch = 1;
        final int heads = 2;
        final int seqLen = 1;
        final int headDim = 64;
        final int dims = 64;
        final float base = 10000f;
        float[] xv = values(batch * heads * seqLen * headDim, -1, 1, 8);
        FloatArray x = FloatArray.fromArray(xv);
        IntArray offset = new IntArray(1);
        FloatArray output = new FloatArray(xv.length);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, offset) //
                .libraryTask("rope", MlxNeuralNetwork::ropeDynamic, x, offset, output, batch, heads, seqLen, headDim, dims, false, base, 1f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            for (int position : new int[] { 0, 7, 123 }) {
                offset.set(0, position);
                plan.execute();
                assertAllClose("position " + position, ropeJava(xv, batch, heads, seqLen, headDim, dims, false, base, 1f, position), output, 1e-4, 1e-5);
            }
        }
    }

    @Test
    public void testScaledDotProductAttention() throws TornadoExecutionPlanException {
        // One query row: the decode step.
        attention(1, false);
    }

    @Test
    public void testScaledDotProductAttentionPrefill() throws TornadoExecutionPlanException {
        attention(5, false);
    }

    @Test
    public void testScaledDotProductAttentionCausal() throws TornadoExecutionPlanException {
        attention(5, true);
    }

    // ---------------------------------------------------------------- other types

    @Test
    public void testRmsNormHalf() throws TornadoExecutionPlanException {
        final int rows = 4;
        final int dim = 2048;
        HalfFloatArray x = half(values(rows * dim, -2, 2, 9));
        HalfFloatArray weight = half(values(dim, 0.5f, 1.5f, 10));
        HalfFloatArray output = new HalfFloatArray(rows * dim);

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, weight) //
                .libraryTask("rmsNorm", MlxNeuralNetwork::rmsNorm, x, weight, output, rows, dim, EPS) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("rmsNorm float16", rmsNormJava(widen(x), widen(weight), rows, dim, EPS), output, 3e-3, 3e-3);
    }

    @Test
    public void testRopeBFloat16() throws TornadoExecutionPlanException {
        final int batch = 1;
        final int heads = 8;
        final int seqLen = 1;
        final int headDim = 128;
        final int dims = 128;
        final int offset = 100;
        final float base = 500000f;
        BFloat16Array x = bf16(values(batch * heads * seqLen * headDim, -1, 1, 11));
        BFloat16Array output = new BFloat16Array(x.getSize());

        TaskGraph taskGraph = new TaskGraph("g") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("rope", MlxNeuralNetwork::rope, x, output, batch, heads, seqLen, headDim, dims, false, base, 1f, offset) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        assertAllClose("rope bfloat16", ropeJava(widen(x), batch, heads, seqLen, headDim, dims, false, base, 1f, offset), output, 1.6e-2, 1.6e-2);
    }
}
