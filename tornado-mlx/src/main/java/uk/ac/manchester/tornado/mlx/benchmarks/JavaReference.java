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
package uk.ac.manchester.tornado.mlx.benchmarks;

import java.util.PriorityQueue;

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Sequential Java references for the {@link OpEvaluation} cases: the loops a Java developer would write on the same
 * TornadoVM arrays, one method per operation so that each loop is compiled for its own operation.
 */
final class JavaReference {

    private JavaReference() {
    }

    // ---------------------------------------------------------------- element-wise

    static void add(FloatArray a, FloatArray b, FloatArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    static void subtract(FloatArray a, FloatArray b, FloatArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) - b.get(i));
        }
    }

    static void multiply(FloatArray a, FloatArray b, FloatArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) * b.get(i));
        }
    }

    static void divide(FloatArray a, FloatArray b, FloatArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) / b.get(i));
        }
    }

    static void maximum(FloatArray a, FloatArray b, FloatArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            c.set(i, Math.max(a.get(i), b.get(i)));
        }
    }

    static void minimum(FloatArray a, FloatArray b, FloatArray c) {
        for (int i = 0; i < c.getSize(); i++) {
            c.set(i, Math.min(a.get(i), b.get(i)));
        }
    }

    static void negative(FloatArray a, FloatArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, -a.get(i));
        }
    }

    static void exp(FloatArray a, FloatArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (float) Math.exp(a.get(i)));
        }
    }

    static void tanh(FloatArray a, FloatArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (float) Math.tanh(a.get(i)));
        }
    }

    /** Abramowitz and Stegun 7.1.26 (absolute error below 1.5e-7). */
    static float erf(float x) {
        float t = 1f / (1f + 0.3275911f * Math.abs(x));
        float y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) * t * (float) Math.exp(-x * x);
        return Math.copySign(y, x);
    }

    static void erf(FloatArray a, FloatArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, erf(a.get(i)));
        }
    }

    static void sigmoid(FloatArray a, FloatArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, 1f / (1f + (float) Math.exp(-a.get(i))));
        }
    }

    static void sqrt(FloatArray a, FloatArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, (float) Math.sqrt(a.get(i)));
        }
    }

    static void rsqrt(FloatArray a, FloatArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            out.set(i, 1f / (float) Math.sqrt(a.get(i)));
        }
    }

    static void square(FloatArray a, FloatArray out) {
        for (int i = 0; i < out.getSize(); i++) {
            float v = a.get(i);
            out.set(i, v * v);
        }
    }

    // ---------------------------------------------------------------- BLAS

    /** c[m,n] = a[m,k] b[k,n], i-k-j order so the inner loop streams rows of b and c. */
    static void matmul(FloatArray a, FloatArray b, FloatArray c, int m, int k, int n) {
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                c.set(i * n + j, 0f);
            }
            for (int p = 0; p < k; p++) {
                float av = a.get(i * k + p);
                for (int j = 0; j < n; j++) {
                    c.set(i * n + j, c.get(i * n + j) + av * b.get(p * n + j));
                }
            }
        }
    }

    /** out = alpha a b + beta c. */
    static void addmm(FloatArray cIn, FloatArray a, FloatArray b, FloatArray out, int m, int k, int n, float alpha, float beta) {
        matmul(a, b, out, m, k, n);
        for (int i = 0; i < m * n; i++) {
            out.set(i, alpha * out.get(i) + beta * cIn.get(i));
        }
    }

    /** c[m,n] = a[m,k] w[n,k]^T: dot products of rows. */
    static void matmulTransposed(FloatArray a, FloatArray w, FloatArray c, int m, int k, int n) {
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float sum = 0f;
                for (int p = 0; p < k; p++) {
                    sum += a.get(i * k + p) * w.get(j * k + p);
                }
                c.set(i * n + j, sum);
            }
        }
    }

    static void matmulTransposed(HalfFloatArray a, HalfFloatArray w, HalfFloatArray c, int m, int k, int n) {
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float sum = 0f;
                for (int p = 0; p < k; p++) {
                    sum += a.get(i * k + p).getFloat32() * w.get(j * k + p).getFloat32();
                }
                c.set(i * n + j, new uk.ac.manchester.tornado.api.types.HalfFloat(sum));
            }
        }
    }

    // ---------------------------------------------------------------- mlx.fast

    static void rmsNorm(FloatArray x, FloatArray w, FloatArray out, int rows, int dim, float eps) {
        for (int r = 0; r < rows; r++) {
            int base = r * dim;
            float sum = 0f;
            for (int i = 0; i < dim; i++) {
                float v = x.get(base + i);
                sum += v * v;
            }
            float scale = 1f / (float) Math.sqrt(sum / dim + eps);
            for (int i = 0; i < dim; i++) {
                out.set(base + i, x.get(base + i) * scale * w.get(i));
            }
        }
    }

    static void layerNorm(FloatArray x, FloatArray w, FloatArray bias, FloatArray out, int rows, int dim, float eps) {
        for (int r = 0; r < rows; r++) {
            int base = r * dim;
            float sum = 0f;
            for (int i = 0; i < dim; i++) {
                sum += x.get(base + i);
            }
            float mean = sum / dim;
            float var = 0f;
            for (int i = 0; i < dim; i++) {
                float d = x.get(base + i) - mean;
                var += d * d;
            }
            float scale = 1f / (float) Math.sqrt(var / dim + eps);
            for (int i = 0; i < dim; i++) {
                out.set(base + i, (x.get(base + i) - mean) * scale * w.get(i) + bias.get(i));
            }
        }
    }

    /** Non-traditional RoPE over x[heads, seqLen, headDim]: rotates the pairs (i, i + headDim / 2). */
    static void rope(FloatArray x, FloatArray out, int heads, int seqLen, int headDim, float base, float scale, int offset) {
        int half = headDim / 2;
        float[] frequencies = new float[half];
        for (int i = 0; i < half; i++) {
            frequencies[i] = (float) Math.pow(base, -2.0 * i / headDim);
        }
        for (int row = 0; row < heads * seqLen; row++) {
            float position = (offset + row % seqLen) * scale;
            int at = row * headDim;
            for (int i = 0; i < half; i++) {
                float theta = position * frequencies[i];
                float cos = (float) Math.cos(theta);
                float sin = (float) Math.sin(theta);
                float x1 = x.get(at + i);
                float x2 = x.get(at + i + half);
                out.set(at + i, x1 * cos - x2 * sin);
                out.set(at + i + half, x1 * sin + x2 * cos);
            }
        }
    }

    /** Single-query attention with grouped KV heads: q[qHeads, d], keys and values [kvHeads, kvLen, d]. */
    static void attentionDecode(FloatArray q, FloatArray keys, FloatArray values, FloatArray out, int qHeads, int kvHeads, int kvLen, int headDim, float scale) {
        float[] scores = new float[kvLen];
        int group = qHeads / kvHeads;
        for (int h = 0; h < qHeads; h++) {
            int kv = h / group * kvLen * headDim;
            float max = Float.NEGATIVE_INFINITY;
            for (int t = 0; t < kvLen; t++) {
                float s = 0f;
                for (int i = 0; i < headDim; i++) {
                    s += q.get(h * headDim + i) * keys.get(kv + t * headDim + i);
                }
                scores[t] = s * scale;
                max = Math.max(max, scores[t]);
            }
            float sum = 0f;
            for (int t = 0; t < kvLen; t++) {
                scores[t] = (float) Math.exp(scores[t] - max);
                sum += scores[t];
            }
            for (int i = 0; i < headDim; i++) {
                float acc = 0f;
                for (int t = 0; t < kvLen; t++) {
                    acc += scores[t] * values.get(kv + t * headDim + i);
                }
                out.set(h * headDim + i, acc / sum);
            }
        }
    }

    // ---------------------------------------------------------------- reductions

    static void softmaxRows(FloatArray x, FloatArray out, int rows, int cols) {
        for (int r = 0; r < rows; r++) {
            int base = r * cols;
            float max = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < cols; i++) {
                max = Math.max(max, x.get(base + i));
            }
            float sum = 0f;
            for (int i = 0; i < cols; i++) {
                float e = (float) Math.exp(x.get(base + i) - max);
                out.set(base + i, e);
                sum += e;
            }
            float inv = 1f / sum;
            for (int i = 0; i < cols; i++) {
                out.set(base + i, out.get(base + i) * inv);
            }
        }
    }

    static void argmaxRows(FloatArray x, IntArray out, int rows, int cols) {
        for (int r = 0; r < rows; r++) {
            int base = r * cols;
            int best = 0;
            float max = x.get(base);
            for (int i = 1; i < cols; i++) {
                float v = x.get(base + i);
                if (v > max) {
                    max = v;
                    best = i;
                }
            }
            out.set(r, best);
        }
    }

    /** The k largest values of each row, through a size-k min-heap. */
    static void topkRows(FloatArray x, FloatArray out, int rows, int cols, int k) {
        PriorityQueue<Float> heap = new PriorityQueue<>(k + 1);
        for (int r = 0; r < rows; r++) {
            heap.clear();
            int base = r * cols;
            for (int i = 0; i < cols; i++) {
                float v = x.get(base + i);
                if (heap.size() < k) {
                    heap.add(v);
                } else if (v > heap.peek()) {
                    heap.poll();
                    heap.add(v);
                }
            }
            for (int i = 0; i < k; i++) {
                out.set(r * k + i, heap.poll());
            }
        }
    }
}
