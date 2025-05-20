/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.unittests.compute;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test class for TransformerKernelsTest.
 *
 * <p>
 * How to run?
 * </p>
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.compute.TransformerKernelsTest
 * </code>
 */
public class TransformerKernelsTest extends TornadoTestBase {

    private static final float DELTA = 0.001f;
    private final Random random = new Random(7);

    public static void reductionOneBlockWithLayer(KernelContext context, FloatArray output, FloatArray x, int size, float ermsNorm, int localMemSize) {
        int gid = context.globalIdx;
        int lid = context.localIdx;
        int groupId = context.groupIdx;
        int groupSize = context.localGroupSizeX;

        // Allocate local memory with the provided size
        float[] localX = context.allocateFloatLocalArray(localMemSize);

        // Load input value and compute square
        if (gid < size) {
            localX[lid] = x.get(gid);
            localX[lid] = localX[lid] * localX[lid];
        } else {
            localX[lid] = 0.0f;
        }

        // Perform parallel reduction within the work group
        for (int stride = (groupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (lid < stride) {
                localX[lid] += localX[lid + stride];
            }
        }

        // Each workgroup stores its partial sum in a different location
        if (lid == 0) {
            // Store the partial sum from each workgroup
            output.set(groupId + 1, localX[0]);
        }

        // Only the first thread in the first workgroup computes the final normalization factor
        if (gid == 0) {
            // Combine partial sums from all workgroups
            float ss = 0.0f;
            for (int i = 1; i < output.getSize(); i++) {  // Assuming 8 workgroups
                ss += output.get(i);
            }

            ss /= size;
            ss += ermsNorm;
            ss = 1.0f / TornadoMath.sqrt(ss);
            output.set(0, ss);  // Store the final scale factor
        }
    }

    /**
     * Applies the computed normalization factor to input and weight elements.
     * This is the second phase of RMS normalization.
     *
     * Formula: output[i] = weight[i] * (normalizationFactor * x[i])
     *
     * @param context
     *     Kernel execution context
     * @param output
     *     Array for normalized output
     * @param x
     *     Input values to normalize
     * @param weights
     *     Weight values for each element
     * @param temp
     *     Temporary array containing normalization factor at index 0
     */
    public static void reductionOneBlock2WithLayer(KernelContext context, FloatArray output, FloatArray x, FloatArray weights, FloatArray temp) {
        int gid = context.globalIdx;

        float ss = temp.get(0);
        output.set(gid, weights.get(gid) * (ss * x.get(gid)));
    }

    /**
     * Copies keys and values into the key-value cache for attention computation.
     * Enables efficient access to past key-value pairs during autoregressive generation.
     *
     * Cache layout: [layer][position][dimension]
     * - Each layer has its own key and value cache
     * - Each position in sequence has a key and value vector
     *
     * @param destKeyCache
     *     Destination array for key cache
     * @param srcKey
     *     Source keys to copy
     * @param destValueCache
     *     Destination array for value cache
     * @param srcValue
     *     Source values to copy
     * @param positioNlayer
     *     Array containing current position
     * @param kvDim
     *     Dimension of key/value vectors
     * @param layer
     *     Current transformer layer index
     * @param contextLength
     *     Maximum sequence length
     */
    public static void copyToCache(FloatArray destKeyCache, FloatArray srcKey, FloatArray destValueCache, FloatArray srcValue, IntArray positioNlayer, int kvDim, int layer, int contextLength) {

        int position = positioNlayer.get(0);
        int loff = layer * contextLength * kvDim;
        int destOffset = loff + position * kvDim;

        for (@Parallel int i = 0; i < srcValue.getSize(); i++) {
            destKeyCache.set(destOffset + i, srcKey.get(i));
            destValueCache.set(destOffset + i, srcValue.get(i));
        }
    }

    /**
     * Applies Rotary Position Encoding (RoPE) to query and key vectors.
     * RoPE rotates pairs of dimensions based on their position in the sequence,
     * enabling the model to learn relative positional information.
     *
     * For each pair of dimensions (2*i, 2*i+1):
     * - Compute rotation angle based on position and frequency
     * - Apply 2D rotation to the pair
     *
     * @param context
     *     Kernel execution context
     * @param positionHolder
     *     Array containing current position
     * @param sq
     *     Query vectors to rotate
     * @param sk
     *     Key vectors to rotate
     * @param kv_dim
     *     Dimension of key/value vectors
     * @param head_size
     *     Dimension of each attention head
     */
    public static void ropeRotation(KernelContext context, IntArray positionHolder, FloatArray sq, FloatArray sk, int kv_dim, int head_size) {
        int i = context.globalIdx * 2;

        int head_dim = i % head_size;
        // 50000.0f vs 10000.0f
        float freq = 1.0f / TornadoMath.pow(50000.0f, head_dim / (float) head_size);
        float val = positionHolder.get(0) * freq;
        float fcr = TornadoMath.cos(val);
        float fci = TornadoMath.sin(val);

        int rotn = i < kv_dim ? 2 : 1; // how many vectors? 2 = q & k, 1 = q only

        // Rotate query vector
        float v0q = sq.get(i);
        float v1q = sq.get(i + 1);
        sq.set(i, v0q * fcr - v1q * fci);
        sq.set(i + 1, v0q * fci + v1q * fcr);

        // Rotate key vector if needed
        if (rotn > 1 && i < sk.getSize()) {
            float v0k = sk.get(i);
            float v1k = sk.get(i + 1);
            sk.set(i, v0k * fcr - v1k * fci);
            sk.set(i + 1, v0k * fci + v1k * fcr);
        }

    }

    /**
     * Orchestrates parallel multi-head attention computation across all heads.
     * Each head processes attention independently in parallel.
     *
     * Attention computation:
     * 1. Compute attention scores (Q·K)
     * 2. Apply softmax for attention weights
     * 3. Compute weighted sum of values (attention·V)
     *
     * @param q
     *     Query vectors for all heads
     * @param key_cache
     *     Cached key vectors
     * @param value_cache
     *     Cached value vectors
     * @param xb
     *     Output buffer for attention results
     * @param nHeads
     *     Number of attention heads
     * @param headSize
     *     Dimension of each head
     * @param kvDim
     *     Total key/value dimension
     * @param kvMul
     *     Key/value head multiplier for grouped-query attention
     * @param seqLen
     *     Current sequence length
     * @param positionHolder
     *     Array containing position and layer info
     * @param wrapAtt
     *     Buffer for attention weights
     * @param layer
     *     Current transformer layer
     * @param contextLength
     *     Maximum context length
     */
    public static void processHeadsParallel(FloatArray q, FloatArray key_cache, FloatArray value_cache, FloatArray xb, int nHeads, int headSize, int kvDim, int kvMul, int seqLen,
            IntArray positionHolder, FloatArray wrapAtt, int layer, int contextLength) {

        int pos = positionHolder.get(0);
        int loff = layer * contextLength * kvDim;

        // Parallelize computation across attention heads
        for (@Parallel int h = 0; h < nHeads; h++) {
            // Process each head in parallel
            processHeadTornado(q, key_cache, value_cache, xb, h, headSize, kvDim, kvMul, loff, pos, wrapAtt);
        }
    }

    /**
     * Computes attention for a single head.
     * Implements scaled dot-product attention with softmax normalization.
     *
     * Steps:
     * 1. Compute attention scores: Q·K / sqrt(head_size)
     * 2. Apply softmax (with max subtraction for numerical stability)
     * 3. Compute weighted sum of values
     *
     * @param allQ
     *     All query vectors
     * @param key_cache
     *     Cached keys
     * @param value_cache
     *     Cached values
     * @param allXb
     *     Output buffer
     * @param h
     *     Head index to process
     * @param headSize
     *     Dimension per head
     * @param kvDim
     *     Key/value dimension
     * @param kvMul
     *     Key multiplier for grouped attention
     * @param loff
     *     Layer offset in cache
     * @param pos
     *     Current position
     * @param wrapAtt
     *     Attention weights buffer
     */
    private static void processHeadTornado(FloatArray allQ, FloatArray key_cache, FloatArray value_cache, FloatArray allXb, int h, int headSize, int kvDim, int kvMul, long loff, int pos,
            FloatArray wrapAtt) {

        // Base index for this head's attention weights
        int headOffset = h * (pos + 1);

        // STEP 1: Calculate attention scores for all timesteps
        for (int t = 0; t <= pos; t++) {
            int kvHeadIdx = h / kvMul;
            int keyOffset = (int) (loff + t * kvDim + kvHeadIdx * headSize);

            float score = 0.0f;
            for (int i = 0; i < headSize; i++) {
                score += allQ.get(h * headSize + i) * key_cache.get(keyOffset + i);
            }
            score = score / TornadoMath.sqrt(headSize);

            // Store in attention buffer
            wrapAtt.set(headOffset + t, score);
        }

        // STEP 2: Find max score for softmax stability
        float maxScore = wrapAtt.get(headOffset);
        for (int t = 1; t <= pos; t++) {
            float val = wrapAtt.get(headOffset + t);
            if (val > maxScore) {
                maxScore = val;
            }
        }

        // STEP 3: Compute exponentials and sum
        float sum = 0.0f;
        for (int t = 0; t <= pos; t++) {
            int idx = headOffset + t;
            float expScore = TornadoMath.exp(wrapAtt.get(idx) - maxScore);
            wrapAtt.set(idx, expScore);
            sum += expScore;
        }

        // STEP 4: Normalize
        float normFactor = (sum > 0.0f) ? (1.0f / sum) : (1.0f / (pos + 1));
        for (int t = 0; t <= pos; t++) {
            int idx = headOffset + t;
            wrapAtt.set(idx, wrapAtt.get(idx) * normFactor);
        }

        // STEP 5: Compute weighted sum of values for each dimension
        for (int i = 0; i < headSize; i++) {
            float weightedSum = 0.0f;
            for (int t = 0; t <= pos; t++) {
                int kvHeadIdx = h / kvMul;
                int valueOffset = (int) (loff + t * kvDim + kvHeadIdx * headSize);
                weightedSum += wrapAtt.get(headOffset + t) * value_cache.get(valueOffset + i);
            }
            allXb.set(h * headSize + i, weightedSum);
        }
    }

    /**
     * Performs optimized matrix-vector multiplication where each work group
     * processes one row of the matrix.
     *
     * Algorithm:
     * 1. Each work group handles one output dimension
     * 2. Threads in work group compute partial dot products
     * 3. Parallel reduction yields final row result
     *
     * @param context
     *     Kernel execution context
     * @param x
     *     Input vector
     * @param hb
     *     Output vector
     * @param w
     *     Weight matrix (row-major)
     * @param n
     *     Input dimension
     * @param d
     *     Output dimension
     * @param localWorkGroupSize
     *     Number of threads per work group
     */
    public static void matrixVectorGeneric(KernelContext context, FloatArray x, FloatArray hb, FloatArray w, int n, int d, int localWorkGroupSize) {
        // One row per workgroup (not per thread)
        int rowId = context.groupIdx;
        int localId = context.localIdx;
        int localSize = localWorkGroupSize;

        // Early exit if this workgroup is beyond our output dimension
        if (rowId >= d) {
            return;
        }
        float sum = matrixVectorRowMajorOptimized(context, localSize, x, w, n, d);

        // Thread 0 in each workgroup writes the final result
        if (localId == 0) {
            hb.set(rowId, sum);
        }
    }

    /**
     * Matrix-vector multiplication with residual connection.
     * Combines regular matrix multiplication with addition of existing values.
     *
     * Formula: hb[i] = hb[i] + w[i]·x
     *
     * @param context
     *     Kernel execution context
     * @param x
     *     Input vector
     * @param hb
     *     Input/output vector (contains residual, receives result)
     * @param w
     *     Weight matrix
     * @param n
     *     Input dimension
     * @param d
     *     Output dimension
     * @param localWorkGroupSize
     *     Work group size
     */
    public static void matrixVectorGenericWithResidual(KernelContext context, FloatArray x, FloatArray hb, FloatArray w, int n, int d, int localWorkGroupSize) {
        // One row per workgroup (not per thread)
        int rowId = context.groupIdx;
        int localId = context.localIdx;
        int localSize = localWorkGroupSize;

        // Early exit if this workgroup is beyond our output dimension
        if (rowId >= d) {
            return;
        }

        float sum = matrixVectorRowMajorOptimized(context, localSize, x, w, n, d);

        // Thread 0 in each workgroup writes the final result
        if (localId == 0) {
            float result = hb.get(rowId) + sum;
            hb.set(rowId, result);
        }
    }

    /**
     * Fused feed-forward network with SiLU activation and GLU gating.
     * Implements the SwiGLU variant used in LLaMA-style models.
     *
     * Formula: FFN(x) = SiLU(x·W1) ⊙ (x·W3)
     * where ⊙ denotes element-wise multiplication
     *
     * @param context
     *     Kernel execution context
     * @param x
     *     Input vector
     * @param hb
     *     Output buffer
     * @param w1
     *     First feed-forward weight matrix
     * @param w3
     *     Third feed-forward weight matrix (gate)
     * @param n
     *     Input dimension
     * @param d
     *     Hidden dimension
     * @param localWorkGroupSize
     *     Work group size
     */
    public static void fusedFeedForwardWithSiLUAndGLUActivation(KernelContext context, FloatArray x, FloatArray hb, FloatArray w1, FloatArray w3, int n, int d, int localWorkGroupSize) {
        // One row per workgroup (not per thread)
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        if (rowId >= d) {
            return;
        }

        float sum1 = matrixVectorRowMajorOptimized(context, localWorkGroupSize, x, w1, n, d);
        float sum3 = matrixVectorRowMajorOptimized(context, localWorkGroupSize, x, w3, n, d);

        // Thread 0 in each workgroup writes the final result
        if (localId == 0) {
            float silu = siluActivation(sum1);  // Using the new SiLU method
            float result = silu * sum3;
            hb.set(rowId, result);
        }
    }

    /**
     * Gaussian Error Linear Unit (GELU) activation function.
     * Approximation formula: GELU(x) ≈ 0.5 * x * (1 + tanh(√(2/π) * (x + 0.044715 * x³)))
     *
     * @param x
     *     Input value
     * @return Activated value
     */
    public static float geluActivation(float x) {
        float x3 = x * x * x;
        return 0.5f * x * (1.0f + TornadoMath.tanh((0.797885f * (x + 0.044715f * x3))));
    }

    /**
     * Sigmoid-weighted Linear Unit (SiLU) activation function.
     * Also known as Swish activation.
     *
     * Formula: SiLU(x) = x * σ(x) = x / (1 + e^(-x))
     *
     * @param x
     *     Input value
     * @return Activated value
     */
    public static float siluActivation(float x) {
        return x * (1.0f / (1.0f + TornadoMath.exp(-x)));
    }

    /**
     * Optimized row-major matrix-vector multiplication for a single row.
     * Uses parallel reduction within a work group to compute one dot product.
     *
     * Algorithm:
     * 1. Each thread computes partial dot product
     * 2. Partial results stored in local memory
     * 3. Tree-based reduction combines partial results
     * 4. Returns final dot product for the row
     *
     * @param context
     *     Kernel execution context
     * @param localSize
     *     Work group size
     * @param x
     *     Input vector
     * @param w
     *     Weight matrix row
     * @param n
     *     Input dimension
     * @param d
     *     Output dimension
     * @return Dot product result for this row
     */
    public static float matrixVectorRowMajorOptimized(KernelContext context, int localSize, FloatArray x, FloatArray w, int n, int d) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        // Allocate local memory for reduction
        float[] localSum = context.allocateFloatLocalArray(localSize);

        int rowOffset = rowId * n;

        // Each thread calculates partial dot product
        float partialSum = 0.0f;
        for (int j = localId; j < n; j += localSize) {
            int matrixIdx = rowOffset + j;
            partialSum += w.get(matrixIdx) * x.get(j);
        }

        // Store partial sum in local memory
        localSum[localId] = partialSum;
        context.localBarrier();

        // Parallel reduction within workgroup
        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSum[localId] += localSum[localId + stride];
            }
            context.localBarrier();
        }

        return localSum[0];
    }

    /**
     * A simplified serial implementation of RMS normalization for TornadoVM.
     * This doesn't use workgroups or barriers, making it easier to reason about.
     * Only the first thread performs the computation.
     */
    public static void serialRmsNorm(KernelContext context, FloatArray output, FloatArray x, int size, float epsilon) {
        int gid = context.globalIdx;

        // Only the first thread does all the work
        if (gid == 0) {
            // Calculate sum of squares
            float sumOfSquares = 0.0f;
            for (int i = 0; i < size; i++) {
                float val = x.get(i);
                sumOfSquares += val * val;
            }

            // Calculate scale factor
            sumOfSquares /= size;
            sumOfSquares += epsilon;
            float scale = 1.0f / TornadoMath.sqrt(sumOfSquares);

            // Store the result
            output.set(0, scale);
        }
    }

    // First kernel - Computes partial sums for each workgroup
    public static void reductionPartialSums(KernelContext context, FloatArray output, FloatArray x, int size, int localMemSize) {
        int gid = context.globalIdx;
        int lid = context.localIdx;
        int groupId = context.groupIdx;
        int groupSize = context.localGroupSizeX;

        // Allocate local memory with the provided size
        float[] localX = context.allocateFloatLocalArray(localMemSize);

        // Load input value and compute square
        if (gid < size) {
            localX[lid] = x.get(gid);
            localX[lid] = localX[lid] * localX[lid];
        } else {
            localX[lid] = 0.0f;
        }

        // Perform parallel reduction within the work group
        for (int stride = (groupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (lid < stride) {
                localX[lid] += localX[lid + stride];
            }
        }

        // Each workgroup stores its partial sum in a different location
        if (lid == 0) {
            // Store the partial sum from each workgroup
            output.set(groupId + 1, localX[0]);
        }
    }

    // Second kernel - Combines partial sums and computes final normalization
    public static void reductionFinalNormalization(KernelContext context, FloatArray output, int size, float ermsNorm) {
        int gid = context.globalIdx;

        // Only one thread needs to perform this calculation
        if (gid == 0) {
            // Combine partial sums from all workgroups
            float ss = 0.0f;
            for (int i = 1; i < output.getSize(); i++) {  // Fixed bounds to avoid out of bounds
                ss += output.get(i);
            }

            ss /= size;
            ss += ermsNorm;
            ss = 1.0f / TornadoMath.sqrt(ss);
            output.set(0, ss);  // Store the final scale factor
        }
    }

    // Sequential version for comparison (unchanged)
    public static void reductionOneBlockSequentialX(FloatArray output, FloatArray x, int size, float ermsNorm) {
        float sum = 0.0f;

        // Compute sum of squares
        for (int i = 0; i < size; i++) {
            float val = x.get(i);
            sum += val * val;
        }

        // Compute normalization factor
        sum /= size;
        sum += ermsNorm;
        sum = 1.0f / (float) Math.sqrt(sum);

        // Store result
        output.set(0, sum);

        // For comparison with parallel version, also store partial sums
        int localSize = 128;
        int numWorkGroups = size / localSize;

        for (int g = 0; g < numWorkGroups; g++) {
            float partialSum = 0.0f;
            int start = g * localSize;
            int end = start + localSize;

            for (int i = start; i < end; i++) {
                if (i < size) {
                    float val = x.get(i);
                    partialSum += val * val;
                }
            }

            output.set(g + 1, partialSum);
        }
    }

    /**
     * Fills a FloatArray with random values within a range.
     */
    private void fillRandomData(FloatArray array, float min, float max) {
        for (int i = 0; i < array.getSize(); i++) {
            array.set(i, min + random.nextFloat() * (max - min));
        }
    }

    /**
     * Copy of reductionOneBlockWithLayer for sequential execution comparison.
     */
    private void reductionOneBlockSequential(FloatArray output, FloatArray x, int size, float ermsNorm) {
        float sumOfSquares = 0.0f;
        for (int i = 0; i < size; i++) {
            float val = x.get(i);
            sumOfSquares += val * val;
        }

        sumOfSquares /= size;
        sumOfSquares += ermsNorm;
        float scale = 1.0f / (float) Math.sqrt(sumOfSquares);

        output.set(0, scale);
    }

    /**
     * Copy of reductionOneBlock2WithLayer for sequential execution comparison.
     */
    private void reductionOneBlock2Sequential(FloatArray output, FloatArray x, FloatArray weights, FloatArray temp) {
        float scale = temp.get(0);
        for (int i = 0; i < x.getSize(); i++) {
            output.set(i, weights.get(i) * (scale * x.get(i)));
        }
    }

    /**
     * Copy of copyToCache for sequential execution comparison.
     */
    private void copyToCacheSequential(FloatArray destKeyCache, FloatArray srcKey, FloatArray destValueCache, FloatArray srcValue, IntArray positionNlayer, int kvDim, int layer, int contextLength) {

        int position = positionNlayer.get(0);
        int loff = layer * contextLength * kvDim;
        int destOffset = loff + position * kvDim;

        for (int i = 0; i < srcValue.getSize(); i++) {
            destKeyCache.set(destOffset + i, srcKey.get(i));
            destValueCache.set(destOffset + i, srcValue.get(i));
        }
    }

    /**
     * Copy of ropeRotation for sequential execution comparison.
     */
    private void ropeRotationSequential(IntArray positionHolder, FloatArray sq, FloatArray sk, int kv_dim, int head_size) {
        for (int i = 0; i < kv_dim; i += 2) {
            int head_dim = i % head_size;
            float freq = 1.0f / (float) Math.pow(50000.0f, head_dim / (float) head_size);
            float val = positionHolder.get(0) * freq;
            float fcr = (float) Math.cos(val);
            float fci = (float) Math.sin(val);

            // Rotate query vector
            float v0q = sq.get(i);
            float v1q = sq.get(i + 1);
            sq.set(i, v0q * fcr - v1q * fci);
            sq.set(i + 1, v0q * fci + v1q * fcr);

            // Rotate key vector
            if (i < sk.getSize()) {
                float v0k = sk.get(i);
                float v1k = sk.get(i + 1);
                sk.set(i, v0k * fcr - v1k * fci);
                sk.set(i + 1, v0k * fci + v1k * fcr);
            }
        }
    }

    /**
     * Sequential version of processHeadsParallel.
     */
    private void processHeadsSequential(FloatArray q, FloatArray key_cache, FloatArray value_cache, FloatArray xb, int nHeads, int headSize, int kvDim, int kvMul, int seqLen, IntArray positionNlayer,
            FloatArray wrapAtt, int layer, int contextLength) {

        int pos = positionNlayer.get(0);
        int loff = layer * contextLength * kvDim;

        for (int h = 0; h < nHeads; h++) {
            // Base index for this head's attention weights
            int headOffset = h * (pos + 1);

            // STEP 1: Calculate attention scores for all timesteps
            for (int t = 0; t <= pos; t++) {
                int kvHeadIdx = h / kvMul;
                int keyOffset = (int) (loff + t * kvDim + kvHeadIdx * headSize);

                float score = 0.0f;
                for (int i = 0; i < headSize; i++) {
                    score += q.get(h * headSize + i) * key_cache.get(keyOffset + i);
                }
                score = score / (float) Math.sqrt(headSize);

                // Store in attention buffer
                wrapAtt.set(headOffset + t, score);
            }

            // STEP 2: Find max score for softmax stability
            float maxScore = wrapAtt.get(headOffset);
            for (int t = 1; t <= pos; t++) {
                float val = wrapAtt.get(headOffset + t);
                if (val > maxScore) {
                    maxScore = val;
                }
            }

            // STEP 3: Compute exponentials and sum
            float sum = 0.0f;
            for (int t = 0; t <= pos; t++) {
                int idx = headOffset + t;
                float expScore = (float) Math.exp(wrapAtt.get(idx) - maxScore);
                wrapAtt.set(idx, expScore);
                sum += expScore;
            }

            // STEP 4: Normalize
            float normFactor = (sum > 0.0f) ? (1.0f / sum) : (1.0f / (pos + 1));
            for (int t = 0; t <= pos; t++) {
                int idx = headOffset + t;
                wrapAtt.set(idx, wrapAtt.get(idx) * normFactor);
            }

            // STEP 5: Compute weighted sum of values for each dimension
            for (int i = 0; i < headSize; i++) {
                float weightedSum = 0.0f;
                for (int t = 0; t <= pos; t++) {
                    int kvHeadIdx = h / kvMul;
                    int valueOffset = (int) (loff + t * kvDim + kvHeadIdx * headSize);
                    weightedSum += wrapAtt.get(headOffset + t) * value_cache.get(valueOffset + i);
                }
                xb.set(h * headSize + i, weightedSum);
            }
        }
    }

    /**
     * Sequential version of matrix-vector multiplication.
     */
    private void matrixVectorSequential(FloatArray x, FloatArray hb, FloatArray w, int n, int d) {
        for (int i = 0; i < d; i++) {
            float sum = 0.0f;
            for (int j = 0; j < n; j++) {
                sum += w.get(i * n + j) * x.get(j);
            }
            hb.set(i, sum);
        }
    }

    /**
     * Sequential version of matrix-vector multiplication with residual.
     */
    private void matrixVectorWithResidualSequential(FloatArray x, FloatArray hb, FloatArray w, int n, int d) {
        for (int i = 0; i < d; i++) {
            float sum = 0.0f;
            for (int j = 0; j < n; j++) {
                sum += w.get(i * n + j) * x.get(j);
            }
            hb.set(i, hb.get(i) + sum);
        }
    }

    /**
     * Sequential version of fused feed-forward with SiLU and GLU.
     */
    private void fusedFeedForwardSequential(FloatArray x, FloatArray hb, FloatArray w1, FloatArray w3, int n, int d) {
        for (int i = 0; i < d; i++) {
            float sum1 = 0.0f;
            float sum3 = 0.0f;
            for (int j = 0; j < n; j++) {
                sum1 += w1.get(i * n + j) * x.get(j);
                sum3 += w3.get(i * n + j) * x.get(j);
            }
            float silu = sum1 * (1.0f / (1.0f + (float) Math.exp(-sum1)));
            hb.set(i, silu * sum3);
        }
    }

    @Test
    public void testReductionOneBlockWithLayer() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 128; // Work group size
        final int numWorkGroups = size / localSize;
        final float ermsNorm = 1e-5f;

        FloatArray input = new FloatArray(size);
        FloatArray output = new FloatArray(numWorkGroups + 1); // +1 for final result
        FloatArray outputSeq = new FloatArray(numWorkGroups + 1);

        // Initialize data
        fillRandomData(input, -2.0f, 2.0f);

        output.init(0.0f);
        // Run sequential version
        reductionOneBlockSequential(outputSeq, input, size, ermsNorm);

        // Set up TornadoVM execution
        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);
        worker.setLocalWork(localSize, 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, input).task("t0", TransformerKernelsTest::reductionOneBlockWithLayer, new KernelContext(), output,
                input, size, ermsNorm, localSize).transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(scheduler).execute();
        // Verify results

        assertEquals(outputSeq.get(0), output.get(0), DELTA);

        executionPlan.freeDeviceMemory();
    }

    @Test
    public void testReductionOneBlock2WithLayer() throws TornadoExecutionPlanException {
        final int size = 1024;

        FloatArray input = new FloatArray(size);
        FloatArray weights = new FloatArray(size);
        FloatArray output = new FloatArray(size);
        FloatArray outputSeq = new FloatArray(size);
        FloatArray temp = new FloatArray(1);

        // Initialize data
        fillRandomData(input, -2.0f, 2.0f);
        fillRandomData(weights, -1.0f, 1.0f);
        temp.set(0, 0.1f); // Normalization scale factor

        // Run sequential version
        reductionOneBlock2Sequential(outputSeq, input, weights, temp);

        // Set up TornadoVM execution
        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weights, temp).task("t0", TransformerKernelsTest::reductionOneBlock2WithLayer,
                new KernelContext(), output, input, weights, temp).transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        // Verify results
        for (int i = 0; i < size; i++) {
            assertEquals(outputSeq.get(i), output.get(i), DELTA);
        }
    }

    @Test
    public void testCopyToCache() throws TornadoExecutionPlanException {
        final int kvDim = 128;
        final int layer = 2;
        final int contextLength = 16;
        final int position = 5;

        FloatArray srcKey = new FloatArray(kvDim);
        FloatArray srcValue = new FloatArray(kvDim);
        FloatArray destKeyCache = new FloatArray(layer * contextLength * kvDim * 2); // Extra space for safety
        FloatArray destValueCache = new FloatArray(layer * contextLength * kvDim * 2);
        FloatArray destKeyCacheSeq = new FloatArray(layer * contextLength * kvDim * 2);
        FloatArray destValueCacheSeq = new FloatArray(layer * contextLength * kvDim * 2);
        IntArray positionNlayer = new IntArray(2);

        // Initialize data
        fillRandomData(srcKey, -1.0f, 1.0f);
        fillRandomData(srcValue, -1.0f, 1.0f);
        positionNlayer.set(0, position);
        positionNlayer.set(1, layer);

        // Run sequential version
        copyToCacheSequential(destKeyCacheSeq, srcKey, destValueCacheSeq, srcValue, positionNlayer, kvDim, layer, contextLength);

        // Set up TornadoVM execution
        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, srcKey, srcValue, positionNlayer).task("t0", TransformerKernelsTest::copyToCache, destKeyCache,
                srcKey, destValueCache, srcValue, positionNlayer, kvDim, layer, contextLength).transferToHost(DataTransferMode.EVERY_EXECUTION, destKeyCache, destValueCache);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        // Verify results - need to check specific range where data was copied
        int offset = layer * contextLength * kvDim + position * kvDim;
        for (int i = 0; i < kvDim; i++) {
            assertEquals(destKeyCacheSeq.get(offset + i), destKeyCache.get(offset + i), DELTA);
            assertEquals(destValueCacheSeq.get(offset + i), destValueCache.get(offset + i), DELTA);
        }
    }

    @Test
    public void testRopeRotation() throws TornadoExecutionPlanException {
        final int kvDim = 128;
        final int headSize = 64;
        final int position = 3;

        FloatArray sq = new FloatArray(kvDim);
        FloatArray sk = new FloatArray(kvDim);
        FloatArray sqSeq = new FloatArray(kvDim);
        FloatArray skSeq = new FloatArray(kvDim);
        IntArray positionHolder = new IntArray(1);

        // Initialize data
        fillRandomData(sq, -1.0f, 1.0f);
        fillRandomData(sk, -1.0f, 1.0f);

        // Copy to sequential arrays
        for (int i = 0; i < kvDim; i++) {
            sqSeq.set(i, sq.get(i));
            skSeq.set(i, sk.get(i));
        }

        positionHolder.set(0, position);

        // Run sequential version
        ropeRotationSequential(positionHolder, sqSeq, skSeq, kvDim, headSize);

        // Set up TornadoVM execution - processing kvDim/2 items (pairs)
        int numPairs = kvDim / 2;
        WorkerGrid worker = new WorkerGrid1D(numPairs);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, sq, sk, positionHolder).task("t0", TransformerKernelsTest::ropeRotation, new KernelContext(),
                positionHolder, sq, sk, kvDim, headSize).transferToHost(DataTransferMode.EVERY_EXECUTION, sq, sk);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        // Verify results
        for (int i = 0; i < kvDim; i++) {
            assertEquals(sqSeq.get(i), sq.get(i), DELTA);
            assertEquals(skSeq.get(i), sk.get(i), DELTA);
        }
    }

    @Test
    public void testProcessHeadsParallel() throws TornadoExecutionPlanException {
        final int nHeads = 8;
        final int headSize = 64;
        final int kvMul = 2; // Grouped query attention factor
        final int kvDim = headSize * (nHeads / kvMul);
        final int seqLen = 8;
        final int contextLength = 16;
        final int pos = 3;
        final int layer = 1;

        // Allocate arrays
        FloatArray query = new FloatArray(nHeads * headSize);
        FloatArray keyCache = new FloatArray(layer * 2 * contextLength * kvDim); // Extra space
        FloatArray valueCache = new FloatArray(layer * 2 * contextLength * kvDim);
        FloatArray output = new FloatArray(nHeads * headSize);
        FloatArray outputSeq = new FloatArray(nHeads * headSize);
        FloatArray attentionWeights = new FloatArray(nHeads * (pos + 1));
        FloatArray attentionWeightsSeq = new FloatArray(nHeads * (pos + 1));
        IntArray positionHolder = new IntArray(2);

        // Initialize data
        fillRandomData(query, -1.0f, 1.0f);
        fillRandomData(keyCache, -0.5f, 0.5f);
        fillRandomData(valueCache, -0.5f, 0.5f);
        positionHolder.set(0, pos);
        positionHolder.set(1, layer);

        // Initialize sequential arrays
        for (int i = 0; i < query.getSize(); i++) {
            outputSeq.set(i, 0.0f);
        }

        // Run sequential version
        processHeadsSequential(query, keyCache, valueCache, outputSeq, nHeads, headSize, kvDim, kvMul, seqLen, positionHolder, attentionWeightsSeq, layer, contextLength);

        // Set up TornadoVM execution
        WorkerGrid worker = new WorkerGrid1D(nHeads);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, query, keyCache, valueCache, positionHolder).task("t0",
                TransformerKernelsTest::processHeadsParallel, query, keyCache, valueCache, output, nHeads, headSize, kvDim, kvMul, seqLen, positionHolder, attentionWeights, layer, contextLength)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output, attentionWeights);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        // Verify results
        for (int i = 0; i < output.getSize(); i++) {
            assertEquals(outputSeq.get(i), output.get(i), DELTA);
        }
    }

    @Test
    public void testMatrixVectorGeneric() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.SPIRV);
        final int inputDim = 64;
        final int outputDim = 128;
        final int localWorkGroupSize = 32;

        FloatArray input = new FloatArray(inputDim);
        FloatArray weights = new FloatArray(inputDim * outputDim);
        FloatArray output = new FloatArray(outputDim);
        FloatArray outputSeq = new FloatArray(outputDim);

        // Initialize data
        fillRandomData(input, -1.0f, 1.0f);
        fillRandomData(weights, -0.1f, 0.1f);

        // Run sequential version
        matrixVectorSequential(input, outputSeq, weights, inputDim, outputDim);

        // Set up TornadoVM execution
        WorkerGrid worker = new WorkerGrid1D(outputDim * localWorkGroupSize);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);
        worker.setLocalWork(localWorkGroupSize, 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weights).task("t0", TransformerKernelsTest::matrixVectorGeneric, new KernelContext(), input,
                output, weights, inputDim, outputDim, localWorkGroupSize).transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        // Verify results
        for (int i = 0; i < outputDim; i++) {
            assertEquals(outputSeq.get(i), output.get(i), DELTA);
        }
    }

    @Test
    public void testMatrixVectorGenericWithResidual() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.SPIRV);
        final int inputDim = 64;
        final int outputDim = 128;
        final int localWorkGroupSize = 32;

        FloatArray input = new FloatArray(inputDim);
        FloatArray weights = new FloatArray(inputDim * outputDim);
        FloatArray output = new FloatArray(outputDim);
        FloatArray outputSeq = new FloatArray(outputDim);

        // Initialize data
        fillRandomData(input, -1.0f, 1.0f);
        fillRandomData(weights, -0.1f, 0.1f);
        fillRandomData(output, -0.5f, 0.5f);

        // Copy initial output values to sequential version
        for (int i = 0; i < outputDim; i++) {
            outputSeq.set(i, output.get(i));
        }

        // Run sequential version
        matrixVectorWithResidualSequential(input, outputSeq, weights, inputDim, outputDim);

        // Set up TornadoVM execution
        WorkerGrid worker = new WorkerGrid1D(outputDim * localWorkGroupSize);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);
        worker.setLocalWork(localWorkGroupSize, 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weights, output).task("t0", TransformerKernelsTest::matrixVectorGenericWithResidual,
                new KernelContext(), input, output, weights, inputDim, outputDim, localWorkGroupSize).transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        // Verify results
        for (int i = 0; i < outputDim; i++) {
            assertEquals(outputSeq.get(i), output.get(i), DELTA);
        }
    }

    @Test
    public void testFusedFeedForwardWithSiLUAndGLUActivation() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.SPIRV);
        final int inputDim = 64;
        final int hiddenDim = 128;
        final int localWorkGroupSize = 32;

        FloatArray input = new FloatArray(inputDim);
        FloatArray w1 = new FloatArray(inputDim * hiddenDim);
        FloatArray w3 = new FloatArray(inputDim * hiddenDim);
        FloatArray output = new FloatArray(hiddenDim);
        FloatArray outputSeq = new FloatArray(hiddenDim);

        // Initialize data
        fillRandomData(input, -1.0f, 1.0f);
        fillRandomData(w1, -0.1f, 0.1f);
        fillRandomData(w3, -0.1f, 0.1f);

        // Run sequential version
        fusedFeedForwardSequential(input, outputSeq, w1, w3, inputDim, hiddenDim);

        // Set up TornadoVM execution
        WorkerGrid worker = new WorkerGrid1D(hiddenDim * localWorkGroupSize);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);
        worker.setLocalWork(localWorkGroupSize, 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, input, w1, w3).task("t0", TransformerKernelsTest::fusedFeedForwardWithSiLUAndGLUActivation,
                new KernelContext(), input, output, w1, w3, inputDim, hiddenDim, localWorkGroupSize).transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        // Verify results
        for (int i = 0; i < hiddenDim; i++) {
            assertEquals(outputSeq.get(i), output.get(i), DELTA);
        }
    }

    @Test
    public void testActivationFunctions() {
        // Test a range of values for both activation functions
        float[] testValues = { -5.0f, -2.0f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 2.0f, 5.0f };

        for (float value : testValues) {
            float geluResult = geluActivation(value);
            float siluResult = siluActivation(value);

            // Calculate expected results
            float x3 = value * value * value;
            float expectedGelu = 0.5f * value * (1.0f + (float) Math.tanh((0.797885f * (value + 0.044715f * x3))));
            float expectedSilu = value * (1.0f / (1.0f + (float) Math.exp(-value)));

            assertEquals(expectedGelu, geluResult, DELTA);
            assertEquals(expectedSilu, siluResult, DELTA);
        }
    }

    /**
     * Sequential implementation to match the serial kernel.
     */
    private void serialRmsNormSequential(FloatArray output, FloatArray x, int size, float epsilon) {
        float sumOfSquares = 0.0f;
        for (int i = 0; i < size; i++) {
            float val = x.get(i);
            sumOfSquares += val * val;
        }

        sumOfSquares /= size;
        sumOfSquares += epsilon;
        float scale = 1.0f / TornadoMath.sqrt(sumOfSquares);

        output.set(0, scale);
    }

    @Test
    public void testSerialRmsNorm() throws TornadoExecutionPlanException {
        final int size = 1024;
        final float epsilon = 1e-5f;

        FloatArray input = new FloatArray(size);
        FloatArray output = new FloatArray(1); // Just one value for the scale factor
        FloatArray outputSeq = new FloatArray(1);

        // Initialize data
        fillRandomData(input, -2.0f, 2.0f);

        // Run sequential version
        serialRmsNormSequential(outputSeq, input, size, epsilon);

        // Set up TornadoVM execution - just need one thread
        WorkerGrid worker = new WorkerGrid1D(1);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, input).task("t0", TransformerKernelsTest::serialRmsNorm, new KernelContext(), output, input, size,
                epsilon).transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        // Verify results
        assertEquals(outputSeq.get(0), output.get(0), DELTA);
    }

    @Test
    public void testReductionOneBlockTwoStepApproach() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 128; // Work group size
        final int numWorkGroups = size / localSize;
        final float ermsNorm = 1e-5f;

        FloatArray input = new FloatArray(size);
        FloatArray output = new FloatArray(numWorkGroups + 1); // +1 for final result
        FloatArray outputSeq = new FloatArray(numWorkGroups + 1);

        // Initialize data with random values
        fillRandomData(input, -2.0f, 2.0f);

        output.init(0.0f);

        // Run sequential version for comparison
        reductionOneBlockSequentialX(outputSeq, input, size, ermsNorm);

        // Set up TornadoVM execution - first kernel (partial sums)
        WorkerGrid worker1 = new WorkerGrid1D(size);
        GridScheduler scheduler1 = new GridScheduler("s0.t0", worker1);
        worker1.setLocalWork(localSize, 1, 1);

        // Set up TornadoVM execution - second kernel (final reduction)
        WorkerGrid worker2 = new WorkerGrid1D(1); // Single thread for final reduction
        GridScheduler scheduler2 = new GridScheduler("s0.t1", worker2);
        worker2.setLocalWork(1, 1, 1);

        scheduler2.addWorkerGrid("s0.t0", worker1);

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, input, output)
                // First kernel: compute partial sums
                .task("t0", TransformerKernelsTest::reductionPartialSums, new KernelContext(), output, input, size, localSize)
                // Second kernel: compute final reduction
                .task("t1", TransformerKernelsTest::reductionFinalNormalization, new KernelContext(), output, size, ermsNorm).transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        // Execute with multiple schedulers
        executionPlan.withGridScheduler(scheduler2) // For second kernel
                .execute();

        // Verify results
        assertEquals(outputSeq.get(0), output.get(0), DELTA);

        executionPlan.freeDeviceMemory();
    }

}