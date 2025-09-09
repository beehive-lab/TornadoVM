/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.compute;

import java.util.ArrayList;
import java.util.LongSummaryStatistics;
import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.common.TornadoFunctions;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.utils.QuantizationUtils;

/**
 * </p>
 * <code>
 * $ tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixVectorRowMajor
 * </code>
 *
 */
public class MatrixVectorRowMajor {

    private static final float DELTA = 1e-4f;
    private static final float DELTA_Q = 1e-1f; // the error margin is larger due to quantization
    private static final int WARM_UP_ITERATIONS = 140;
    private static final int BENCHMARK_ITERATIONS = 120;
    private static final Random random = new Random(42); // Fixed seed for reproducibility
    private static int LOCAL_WORK_GROUP_SIZE = 32; // Number of threads per workgroup
    private static final int TILE_SIZE = 256;
    private static final int BLOCK_SIZE = 32;
    private static final int OPTIMIZED_BLOCK_SIZE = 256;

    /**
     * Fills an array with random data in the specified range
     */
    private static void fillRandomData(FloatArray array, float min, float max) {
        float range = max - min;
        for (int i = 0; i < array.getSize(); i++) {
            array.set(i, min + random.nextFloat() * range);
        }
    }

    private static void fillRandomDataFp16(HalfFloatArray array, float min, float max) {
        float range = max - min;
        for (int i = 0; i < array.getSize(); i++) {
            array.set(i, new HalfFloat(min + random.nextInt() * range));
        }
    }

    /**
     * Sequential implementation of matrix-vector multiplication
     */
    public static void matrixVectorSequential(FloatArray x, FloatArray hb, FloatArray w, int n, int d) {
        for (int i = 0; i < d; i++) {
            float sum = 0.0f;
            int rowOffset = i * n;
            for (int j = 0; j < n; j++) {
                sum += w.get(rowOffset + j) * x.get(j);
            }
            hb.set(i, sum);
        }
    }

    public static void matrixVectorParallel(FloatArray x, FloatArray hb, FloatArray w, int n, int d) {
        for (@Parallel int i = 0; i < d; i++) {
            float sum = 0.0f;
            int rowOffset = i * n;
            for (int j = 0; j < n; j++) {
                sum += w.get(rowOffset + j) * x.get(j);
            }
            hb.set(i, sum);
        }
    }

    /**
     * Optimized implementation using KernelContext API with a row major approach
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
        float sum = matrixVectorRowMajorOptimized(context, localSize, x, w, n);

        // Thread 0 in each workgroup writes the final result
        if (localId == 0) {
            hb.set(rowId, sum);
        }
    }

    /**
     * Helper method to compute the dot product for a single row in an optimized way
     */
    public static float matrixVectorRowMajorOptimized(KernelContext context, int localSize, FloatArray x, FloatArray w, int n) {
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
     * Optimized implementation using KernelContext API with a row major approach for FP16
     */
    public static void matrixVectorGenericFP16(KernelContext context, FloatArray x, FloatArray hb, HalfFloatArray w, int n, int d, int localWorkGroupSize) {
        // One row per workgroup (not per thread)
        int rowId = context.groupIdx;
        int localId = context.localIdx;
        int localSize = localWorkGroupSize;

        // Early exit if this workgroup is beyond our output dimension
        if (rowId >= d) {
            return;
        }
        float sum = matrixVectorRowMajorOptimizedFP16(context, localSize, x, w, n);

        // Thread 0 in each workgroup writes the final result
        if (localId == 0) {
            hb.set(rowId, sum);
        }
    }

    /**
     * Helper method to compute the dot product for a single row in an optimized way for FP16
     */
    public static float matrixVectorRowMajorOptimizedFP16(KernelContext context, int localSize, FloatArray x, HalfFloatArray w, int n) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        // Allocate local memory for reduction
        float[] localSum = context.allocateFloatLocalArray(localSize);

        int rowOffset = rowId * n;

        // Each thread calculates partial dot product
        float partialSum = 0.0f;
        for (int j = localId; j < n; j += localSize) {
            int matrixIdx = rowOffset + j;
            partialSum += w.get(matrixIdx).getFloat32() * x.get(j);
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
     * Method to calculate the max value and inverted scale to quantize the vector
     */
    public static void reductionCalculateMax(KernelContext context, FloatArray max, FloatArray x,  FloatArray x_scale, FloatArray inv_scale, int localMemSize) {
        int gid = context.globalIdx;
        int lid = context.localIdx;
        int groupId = context.groupIdx;
        int groupSize = context.localGroupSizeX;

        // Allocate local memory with the provided size
        float[] localX = context.allocateFloatLocalArray(localMemSize);

        // Load input value to local memory
        localX[lid] = x.get(gid);

        for (int stride = (groupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (lid < stride) {
                localX[lid] = TornadoMath.max(localX[lid], TornadoMath.abs(localX[lid + stride]));
            }
        }

        if (lid == 0) {
            // Store the partial sum from each workgroup
            max.set(groupId, localX[0]);
        }

        if (gid == 0) {
            // Calculate inverted scale
            float max_abs_val = 0.0f;
            for (int i = 0; i < (8192 / localMemSize); i++) {
                max_abs_val = TornadoMath.max(max_abs_val, max.get(i));
            }
            float scale = (max.get(0) == 0.0f) ? 1.0f : max.get(0) / 127.0f;
            inv_scale.set(0, 1.0f / scale);
            x_scale.set(0, scale);
        }
    }

    /**
     * Method that performs the quantization of the vector
     */
    public static void quantizeKernelContext(KernelContext context, FloatArray x, FloatArray inv_scale, Int8Array x_quant) {
        int gid = context.globalIdx;

        float scale = inv_scale.get(0);
        x_quant.set(gid, (byte) TornadoMath.floor((x.get(gid) * scale) + 0.5f));
    }

//    /**
//     * Helper method to compute the dot product for a single row using DP4A
//     */
//    public static float matrixVectorRowMajorOptimizedDP4A(KernelContext context, int localSize, Int8Array w_quant, Int8Array x_quant, int n, float w_scale, float x_scale) {
//        int rowId = context.groupIdx;
//        int localId = context.localIdx;
//
//        if (localId >= localSize) {
//            return 0.0f;
//        }
//
//        int[] localSum = context.allocateIntLocalArray(localSize);
//        int rowOffset = rowId * n;
//        int partialSum = 0;
//
//        for (int j = localId * 4; j < n; j += localSize * 4) {
//            partialSum = QuantizationUtils.dp4a(w_quant, rowOffset + j, x_quant, j, partialSum);
//        }
//
//        localSum[localId] = partialSum;
//        context.localBarrier();
//
//        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
//            if (localId < stride) {
//                localSum[localId] += localSum[localId + stride];
//            }
//            context.localBarrier();
//        }
//
//        if (localId == 0) {
//            return QuantizationUtils.dequantizeFusedResult(localSum[0], w_scale, x_scale);
//        }
//        return 0.0f;
//    }
//
//
//    /**
//     * Optimized implementation using KernelContext API with a row major approach and DP4A
//     */
//    public static void matrixVectorGenericDP4A(KernelContext context, Int8Array w_quant, Int8Array x_quant, FloatArray output, int n, int d, int localWorkGroupSize, FloatArray w_scale, FloatArray x_scale) {
//        int rowId = context.groupIdx;
//        int localId = context.localIdx;
//
//        if (rowId >= d) {
//            return;
//        }
//
//        float sum = matrixVectorRowMajorOptimizedDP4A(context, localWorkGroupSize, w_quant, x_quant, n, w_scale.get(0), x_scale.get(0));
//
//        if (localId == 0) {
//            output.set(rowId, sum);
//        }
//    }

//    public static void matrixVectorGenericDP4A(KernelContext context, Int8Array w_quant, Int8Array x_quant, FloatArray output, int n, int d, int localWorkGroupSize, FloatArray w_scale, FloatArray x_scale) {
//        int rowId = context.groupIdx;
//
//        if (rowId >= d) {
//            return;
//        }
//
//        // 1. Each workgroup computes the integer dot product for one row.
//        int intSum = matrixVectorTiledDP4A(context, localWorkGroupSize, w_quant, x_quant, n);
//
//        // 2. Only thread 0 performs the final dequantization and write.
//        // This is efficient because the expensive part is done in parallel.
//        if (context.localIdx == 0) {
//            // Fused dequantization and scaling
//            float finalValue = (float) intSum * w_scale.get(0) * x_scale.get(0);
//            output.set(rowId, finalValue);
//        }
//    }
//
//    /**
//     * Tiled helper method to compute the dot product for a single row using DP4A and local memory.
//     * This kernel returns the final integer sum for the row.
//     */
//    public static int matrixVectorTiledDP4A(KernelContext context, int localSize, Int8Array w_quant, Int8Array x_quant, int n) {
//        int rowId = context.groupIdx;
//        int localId = context.localIdx;
//
//        // Local memory to hold a tile of the x_quant vector
//        byte[] x_tile = context.allocateByteLocalArray(TILE_SIZE);
//
//        // Local memory for the parallel reduction
//        int[] localSum = context.allocateIntLocalArray(localSize);
//
//        int rowOffset = rowId * n;
//        int totalSum = 0;
//
//        // Loop over the input vectors in tiles
//        for (int tileStart = 0; tileStart < n; tileStart += TILE_SIZE) {
//
//            // 1. Cooperatively load a tile of x_quant from global to local memory
//            // Each thread loads multiple elements to fill the tile.
//            for (int i = localId; i < TILE_SIZE && (tileStart + i) < n; i += localSize) {
//                x_tile[i] = (byte) x_quant.get(tileStart + i);
//            }
//
//            // Synchronize to ensure the entire x_tile is loaded before proceeding
//            context.localBarrier();
//
//            // 2. Compute partial dot product using data from the local memory tile
//            // Each thread computes its share of the work for this tile.
//            int partialSum = 0;
//            // The loop bound is now TILE_SIZE, not n.
//            for (int j = localId * 4; j < TILE_SIZE; j += localSize * 4) {
//                if (tileStart + j < n) { // Boundary check for the last tile
//                    partialSum = QuantizationUtils.dp4a(w_quant, rowOffset + tileStart + j, x_tile, j, partialSum);
//                }
//            }
//            totalSum += partialSum;
//
//            // Synchronize to ensure all threads are done with the current tile before loading the next one
//            context.localBarrier();
//        }
//
//        // Store each thread's total sum in local memory for reduction
//        localSum[localId] = totalSum;
//        context.localBarrier();
//
//        // 3. Perform parallel reduction on the final sums
//        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
//            if (localId < stride) {
//                localSum[localId] += localSum[localId + stride];
//            }
//            context.localBarrier();
//        }
//
//        // The final integer result for the row is in localSum[0]
//        return localSum[0];
//    }
    public static void matrixVectorGenericDP4A(KernelContext context, Int8Array w_quant, Int8Array x_quant, FloatArray output, int n, int d, int localWorkGroupSize, FloatArray w_scale, FloatArray x_scale) {
        int rowId = context.groupIdx;

        if (rowId >= d) {
            return;
        }

        // 1. Each workgroup computes the integer dot product for one row.
        int intSum = matrixVectorSimpleDP4A(context, localWorkGroupSize, w_quant, x_quant, n); //matrixVectorTiledDP4A(context, localWorkGroupSize, w_quant, x_quant, n);

        // 2. Only thread 0 performs the final dequantization and write.
        // This is efficient because the expensive part is done in parallel.
        if (context.localIdx == 0) {
            // Fused dequantization and scaling
            float finalValue = (float) intSum * w_scale.get(0) * x_scale.get(0);
            output.set(rowId, finalValue);
        }
    }

    public static int matrixVectorSimpleDP4A(KernelContext context, int localSize, Int8Array w_quant,
                                             Int8Array x_quant, int n) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        int[] localSum = context.allocateIntLocalArray(localSize);
        int rowOffset = rowId * n;

        // Direct computation without tiling
        int partialSum = 0;

        // Process 4 elements at a time using DP4A
        for (int j = localId * 4; j < n; j += localSize * 4) {
            if (j + 3 < n) {
                partialSum = QuantizationUtils.dp4a(w_quant, rowOffset + j, x_quant, j, partialSum);
            } else {
                // Handle remaining elements individually
                for (int k = j; k < n && k < j + 4; k++) {
                    partialSum += w_quant.get(rowOffset + k) * x_quant.get(k);
                }
            }
        }

        localSum[localId] = partialSum;
        context.localBarrier();

        // Parallel reduction
        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSum[localId] += localSum[localId + stride];
            }
            context.localBarrier();
        }

        return localSum[0];
    }

    public static int matrixVectorOptimizedDP4A(KernelContext context, int localSize, Int8Array w_quant,
                                                Int8Array x_quant, int n) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        // Use 32-bit integers to store 4 packed INT8 values for better memory access
        int[] x_tile_packed = context.allocateIntLocalArray(TILE_SIZE / 4);
        int[] localSum = context.allocateIntLocalArray(localSize);

        int rowOffset = rowId * n;
        int totalSum = 0;

        // Process in chunks of 4 elements (suitable for DP4A)
        int nPacked = n / 4;

        for (int tileStart = 0; tileStart < nPacked; tileStart += TILE_SIZE / 4) {
            int tileSize = Math.min(TILE_SIZE / 4, nPacked - tileStart);

            // Cooperatively load packed data (4 INT8 values per int)
            for (int i = localId; i < tileSize; i += localSize) {
                int globalIdx = (tileStart + i) * 4;
                if (globalIdx < n) {
                    // Pack 4 consecutive INT8 values into one int
                    int packed = 0;
                    for (int j = 0; j < 4 && globalIdx + j < n; j++) {
                        int val = x_quant.get(globalIdx + j) & 0xFF;
                        packed |= (val << (j * 8));
                    }
                    x_tile_packed[i] = packed;
                }
            }

            context.localBarrier();

            // Compute partial dot product using DP4A
            int partialSum = 0;
            for (int i = localId; i < tileSize; i += localSize) {
                int w_globalIdx = rowOffset + (tileStart + i) * 4;
                if (w_globalIdx < rowOffset + n) {
                    // Pack weight values
                    int w_packed = 0;
                    for (int j = 0; j < 4 && w_globalIdx + j < rowOffset + n; j++) {
                        int val = w_quant.get(w_globalIdx + j) & 0xFF;
                        w_packed |= (val << (j * 8));
                    }

                    // Use DP4A operation - you'll need to implement this in QuantizationUtils
                    partialSum = QuantizationUtils.dp4a_packed(w_packed, x_tile_packed[i], partialSum);
                }
            }

            totalSum += partialSum;
            context.localBarrier();
        }

        // Store partial sums for reduction
        localSum[localId] = totalSum;
        context.localBarrier();

        // Parallel reduction
        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSum[localId] += localSum[localId + stride];
            }
            context.localBarrier();
        }

        return localSum[0];
    }

    private static void quantizeFloatArray(FloatArray x, Int8Array x_quant, FloatArray x_scale) {
        float max_abs_val = 0.0f;
        for (int i = 0; i < x.getSize(); i++) {
            max_abs_val = TornadoMath.max(max_abs_val, TornadoMath.abs(x.get(i)));
        }
        final float scale = (max_abs_val == 0.0f) ? 1.0f : max_abs_val / 127.0f;
        final float inv_scale = 1.0f / scale;
        for (int i = 0; i < x.getSize(); i++) {
            x_quant.set(i, (byte) TornadoMath.floor((x.get(i) * inv_scale) + 0.5f));
        }
        x_scale.set(0, scale);
    }

    public static void matrixVectorGenericQ8(
            KernelContext context,
            FloatArray activations,     // FP32 input activations
            FloatArray output,          // FP32 output
            Int8Array weightsQ,         // Q8_0 quantized weights
            HalfFloatArray weightsScales, // weight scales per block
            int n,                      // input dimension (cols)
            int d,                      // output dimension (rows)
            int localWorkGroupSize      // can be 32, 64, 128, 256, etc.
    ) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        if (rowId >= d) return;

        // Allocate shared memory based on actual work group size
        float[] localSums = context.allocateFloatLocalArray(localWorkGroupSize);

        float rowSum = 0.0f;
        int blocksPerRow = n / BLOCK_SIZE;
        int rowOffset = rowId * n;

        // Each thread processes multiple blocks - work distribution scales with work group size
        for (int blockIdx = localId; blockIdx < blocksPerRow; blockIdx += localWorkGroupSize) {
            int blockStart = blockIdx * BLOCK_SIZE;
            int globalWeightBlockIdx = rowId * blocksPerRow + blockIdx;

            // Get weight scale for this block (matches GGUF Q8_0 format)
            float weightScale = weightsScales.get(globalWeightBlockIdx).getFloat32();

            // Compute activation scale for this block
            float maxAbs = 0.0f;
            for (int i = 0; i < BLOCK_SIZE; i++) {
                float val = activations.get(blockStart + i);
                float abs = TornadoMath.abs(val);
                if (abs > maxAbs) maxAbs = abs;
            }

            float activationScale = (maxAbs == 0.0f) ? 0.0f : (maxAbs / 127.0f);
            float combinedScale = weightScale * activationScale;

            if (combinedScale == 0.0f) continue;

            float invActivationScale = 1.0f / activationScale;

            // Compute block dot product using dp4a
            int blockSum = 0;
            int weightOffset = rowOffset + blockStart;

            // Process 4 elements at a time using dp4a (TornadoVM native instruction)
            for (int i = 0; i < BLOCK_SIZE; i += 4) {

                // --- STEP 1: Quantize and pack 4 activations with ONE function call ---
                int q_packed = quantizeAndPackFloat4(activations, blockStart + i, invActivationScale);

                // --- STEP 2: Fetch and pack 4 weights ---
                byte w0 = weightsQ.get(weightOffset + i);
                byte w1 = weightsQ.get(weightOffset + i + 1);
                byte w2 = weightsQ.get(weightOffset + i + 2);
                byte w3 = weightsQ.get(weightOffset + i + 3);
                int w_packed = packBytesToInt(w0, w1, w2, w3);

                blockSum = QuantizationUtils.dp4a_packed(w_packed, q_packed, blockSum);
            }

            rowSum += (float) blockSum * combinedScale;
        }

        // Flexible tree reduction - works with any power-of-2 work group size
        localSums[localId] = rowSum;
        context.localBarrier();

        // Tree reduction that adapts to actual work group size
        for (int stride = localWorkGroupSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSums[localId] += localSums[localId + stride];
            }
            context.localBarrier();
        }

        // Write final result
        if (localId == 0) {
            output.set(rowId, localSums[0]);
        }
    }

    private static int packBytesToInt(byte b0, byte b1, byte b2, byte b3) {
        return ((b3 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b1 & 0xFF) << 8) | (b0 & 0xFF);
    }


    private static int quantizeAndPackFloat4(FloatArray values, int startIdx, float invScale) {
        // Perform quantization on 4 elements
        int q0 = (int) TornadoMath.floor((values.get(startIdx) * invScale) + 0.5f);
        int q1 = (int) TornadoMath.floor((values.get(startIdx + 1) * invScale) + 0.5f);
        int q2 = (int) TornadoMath.floor((values.get(startIdx + 2) * invScale) + 0.5f);
        int q3 = (int) TornadoMath.floor((values.get(startIdx + 3) * invScale) + 0.5f);

        // Clamp all 4
        q0 = TornadoMath.clamp(q0, -127, 127);
        q1 = TornadoMath.clamp(q1, -127, 127);
        q2 = TornadoMath.clamp(q2, -127, 127);
        q3 = TornadoMath.clamp(q3, -127, 127);

        // Pack the 4 clamped bytes into a single integer and return
        return ((q3 & 0xFF) << 24) | ((q2 & 0xFF) << 16) | ((q1 & 0xFF) << 8) | (q0 & 0xFF);
    }

    public static void quantizeWeightsToQ8(FloatArray weightsFP32,
                                           Int8Array outQ,
                                           HalfFloatArray outScales,
                                           int rows,
                                           int cols) {
        if ((cols % BLOCK_SIZE) != 0) {
            throw new IllegalArgumentException("cols must be multiple of BLOCK_SIZE=" + BLOCK_SIZE);
        }
        int blocksPerRow = cols / BLOCK_SIZE;

        for (int r = 0; r < rows; r++) {
            int rowBase = r * cols;
            for (int b = 0; b < blocksPerRow; b++) {
                int blockStart = rowBase + b * BLOCK_SIZE;

                // compute max abs (Q8_0 format)
                float maxAbs = 0.0f;
                for (int i = 0; i < BLOCK_SIZE; i++) {
                    float v = weightsFP32.get(blockStart + i);
                    float a = Math.abs(v);
                    if (a > maxAbs) maxAbs = a;
                }

                float scale = (maxAbs == 0.0f) ? 0.0f : (maxAbs / 127.0f);

                // store scale as HalfFloat (matches GGUF format)
                int globalBlockIdx = r * blocksPerRow + b;
                outScales.set(globalBlockIdx, new HalfFloat(scale));

                float inv = (scale == 0.0f) ? 0.0f : 1.0f / scale;

                // quantize block
                for (int i = 0; i < BLOCK_SIZE; i++) {
                    float val = weightsFP32.get(blockStart + i);
                    int q = Math.round(val * inv);
                    if (q > 127) q = 127;
                    else if (q < -127) q = -127;
                    outQ.set(blockStart + i, (byte) q);
                }
            }
        }
    }

    public static void matrixVectorGenericQ8_Flexible(
            KernelContext context,
            FloatArray activations,
            FloatArray output,
            Int8Array weightsQ,
            HalfFloatArray weightsScales,
            int n,
            int d,
            int localWorkGroupSize) {

        // --- Each workgroup computes one output row ---
        int rowId = context.groupIdx;
        int localId = context.localIdx;
        int localSize = localWorkGroupSize;

        if (rowId >= d) {
            return;
        }

        // --- Shared (local) memory allocation ---
        // Staging for the current block of activations.
        // OPTIMIZED_BLOCK_SIZE (e.g., 256) should be a class constant.
        float[] localActivations = context.allocateFloatLocalArray(OPTIMIZED_BLOCK_SIZE);
        // Staging for the max_abs reduction within a block.
        float[] localMaxAbs = context.allocateFloatLocalArray(localSize);
        // Staging for the final reduction of the entire row's sum.
        float[] localSums = context.allocateFloatLocalArray(localSize);

        // Each thread independently accumulates a partial sum for the row.
        float partialRowSum = 0.0f;

        int weightRowOffset = rowId * n;
        int scalesRowOffset = rowId * (n / BLOCK_SIZE); // Assumes original BLOCK_SIZE for scales

        // --- The workgroup processes the row in chunks of OPTIMIZED_BLOCK_SIZE ---
        for (int blockStart = 0; blockStart < n; blockStart += OPTIMIZED_BLOCK_SIZE) {

            // 1. Parallel Load from Global to Shared Memory
            for (int i = localId; i < OPTIMIZED_BLOCK_SIZE; i += localSize) {
                localActivations[i] = activations.get(blockStart + i);
            }
            context.localBarrier(); // Ensure all loads are complete

            // 2. Parallel Reduction in Shared Memory to find maxAbs
            float threadMaxAbs = 0.0f;
            for (int i = localId; i < OPTIMIZED_BLOCK_SIZE; i += localSize) {
                threadMaxAbs = TornadoMath.max(threadMaxAbs, TornadoMath.abs(localActivations[i]));
            }
            localMaxAbs[localId] = threadMaxAbs;
            context.localBarrier();

            for (int stride = localSize / 2; stride > 0; stride >>= 1) {
                if (localId < stride) {
                    localMaxAbs[localId] = TornadoMath.max(localMaxAbs[localId], localMaxAbs[localId + stride]);
                }
                context.localBarrier();
            }
            float maxAbs = localMaxAbs[0]; // All threads read the final maxAbs

            // 3. Calculate Scales
            float activationScale = (maxAbs == 0.0f) ? 0.0f : (maxAbs / 127.0f);
            int originalBlockIdx = blockStart / BLOCK_SIZE;
            float weightScale = weightsScales.get(scalesRowOffset + originalBlockIdx).getFloat32();
            float combinedScale = weightScale * activationScale;

            if (combinedScale == 0.0f) {
                continue; // Skip the math but let threads naturally sync at the next loop iteration
            }

            float invActivationScale = 1.0f / activationScale;

            // 4. Quantize from Shared Memory & compute DP4A
            // Each thread computes its own partial dot product for this block
            int threadBlockSum = 0;
            int weightBlockOffset = weightRowOffset + blockStart;

            for (int i = localId * 4; i < OPTIMIZED_BLOCK_SIZE; i += (localSize * 4)) {
                int q_packed = quantizeAndPackFloat4FromLocal(localActivations, i, invActivationScale);

                byte w0 = weightsQ.get(weightBlockOffset + i);
                byte w1 = weightsQ.get(weightBlockOffset + i + 1);
                byte w2 = weightsQ.get(weightBlockOffset + i + 2);
                byte w3 = weightsQ.get(weightBlockOffset + i + 3);
                int w_packed = packBytesToInt(w0, w1, w2, w3);

                threadBlockSum = QuantizationUtils.dp4a_packed(w_packed, q_packed, threadBlockSum);
            }

            // 5. Each thread dequantizes its own partial sum and adds to its row accumulator.
            // This step is fully parallel, with no synchronization needed inside the loop.
            partialRowSum += (float) threadBlockSum * combinedScale;
        }

        // --- Final Reduction ---
        // All threads have finished processing blocks and have a partialRowSum.
        // Now, we sum them all up using a final parallel reduction.
        localSums[localId] = partialRowSum;
        context.localBarrier(); // Ensure all partial sums are written to shared memory

        // Standard tree-reduction that works for any power-of-2 group size
        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSums[localId] += localSums[localId + stride];
            }
            context.localBarrier();
        }

        // Thread 0 has the final, complete sum and writes it to global memory.
        if (localId == 0) {
            output.set(rowId, localSums[0]);
        }
    }

    // Helper method to quantize from a local array (shared memory)
    private static int quantizeAndPackFloat4FromLocal(float[] values, int startIdx, float invScale) {
        float v0 = values[startIdx];
        float v1 = values[startIdx + 1];
        float v2 = values[startIdx + 2];
        float v3 = values[startIdx + 3];

        int q0 = TornadoMath.clamp((int) TornadoMath.floor((v0 * invScale) + 0.5f), -127, 127);
        int q1 = TornadoMath.clamp((int) TornadoMath.floor((v1 * invScale) + 0.5f), -127, 127);
        int q2 = TornadoMath.clamp((int) TornadoMath.floor((v2 * invScale) + 0.5f), -127, 127);
        int q3 = TornadoMath.clamp((int) TornadoMath.floor((v3 * invScale) + 0.5f), -127, 127);

        return ((q3 & 0xFF) << 24) | ((q2 & 0xFF) << 16) | ((q1 & 0xFF) << 8) | (q0 & 0xFF);
    }

    // --- START: NEW TWO-KERNEL INT8 IMPLEMENTATION ---

    /**
     * KERNEL 1: Quantizes a float vector to q8_0 format, block by block.
     * This kernel is highly memory-bound. One workgroup processes one block.
     */
    public static void quantizeActivations_Q8_0(
            KernelContext context,
            FloatArray activations,
            Int8Array activationsQ,
            FloatArray activationScales,
            int size, int localWorkgroupSize) {

        int blockIdx = context.groupIdx;
        int localId = context.localIdx;
        int localSize = localWorkgroupSize;

        int blockStart = blockIdx * BLOCK_SIZE;
        if (blockStart >= size) {
            return;
        }

        float[] localData = context.allocateFloatLocalArray(BLOCK_SIZE);
        float[] localMaxAbs = context.allocateFloatLocalArray(localSize);

        // 1. Parallel Load to Shared Memory
        for (int i = localId; i < BLOCK_SIZE; i += localSize) {
            localData[i] = activations.get(blockStart + i);
        }
        context.localBarrier();

        // 2. Parallel Reduction in Shared Memory to find maxAbs
        float threadMaxAbs = 0.0f;
        for (int i = localId; i < BLOCK_SIZE; i += localSize) {
            threadMaxAbs = TornadoMath.max(threadMaxAbs, TornadoMath.abs(localData[i]));
        }
        localMaxAbs[localId] = threadMaxAbs;
        context.localBarrier();

        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localMaxAbs[localId] = TornadoMath.max(localMaxAbs[localId], localMaxAbs[localId + stride]);
            }
            context.localBarrier();
        }
        float maxAbs = localMaxAbs[0];

        // 3. Thread 0 calculates and writes the scale
        float scale = (maxAbs == 0.0f) ? 0.0f : (maxAbs / 127.0f);
        if (localId == 0) {
            activationScales.set(blockIdx, scale);
        }

        // 4. Quantize from Shared Memory and Write to Global
        if (scale != 0.0f) {
            float invScale = 1.0f / scale;
            for (int i = localId; i < BLOCK_SIZE; i += localSize) {
                float val = localData[i];
                int q = (int) TornadoMath.floor((val * invScale) + 0.5f);
                activationsQ.set(blockStart + i, (byte) TornadoMath.clamp(q, -127, 127));
            }
        } else {
            // If scale is zero, all quantized values must be zero
            for (int i = localId; i < BLOCK_SIZE; i += localSize) {
                activationsQ.set(blockStart + i, (byte) 0);
            }
        }
    }

    /**
     * KERNEL 2: High-performance int8 x int8 matrix-vector multiplication.
     * This kernel is compute-bound. One workgroup computes one output row.
     */
    public static void matrixVectorQ8xQ8(
            KernelContext context,
            Int8Array weightsQ,
            HalfFloatArray weightScales,
            Int8Array activationsQ,
            FloatArray activationScales,
            FloatArray output,
            int n, int d, int localWorkgroupSize) {

        int rowId = context.groupIdx;
        int localId = context.localIdx;
        int localSize = localWorkgroupSize;

        if (rowId >= d) {
            return;
        }

        byte[] localActivations = context.allocateByteLocalArray(BLOCK_SIZE);
        float[] localSums = context.allocateFloatLocalArray(localSize);

        float partialRowSum = 0.0f;
        int weightRowOffset = rowId * n;
        int scalesRowOffset = rowId * (n / BLOCK_SIZE);

        for (int blockIdx = 0; blockIdx < (n / BLOCK_SIZE); blockIdx++) {
            int blockStart = blockIdx * BLOCK_SIZE;

            // 1. Load block of quantized activations to fast shared memory
            for (int i = localId; i < BLOCK_SIZE; i += localSize) {
                localActivations[i] = activationsQ.get(blockStart + i);
            }
            context.localBarrier(); // Ensure all threads have loaded before proceeding

            // 2. Compute DP4A using cached activations
            int threadBlockSum = 0;
            int weightBlockOffset = weightRowOffset + blockStart;
            for (int i = localId * 4; i < BLOCK_SIZE; i += (localSize * 4)) {
                // Pack 4 activations from shared memory
                byte a0 = localActivations[i];
                byte a1 = localActivations[i + 1];
                byte a2 = localActivations[i + 2];
                byte a3 = localActivations[i + 3];
                int a_packed = packBytesToInt(a0, a1, a2, a3);

                // Pack 4 weights from global memory
                byte w0 = weightsQ.get(weightBlockOffset + i);
                byte w1 = weightsQ.get(weightBlockOffset + i + 1);
                byte w2 = weightsQ.get(weightBlockOffset + i + 2);
                byte w3 = weightsQ.get(weightBlockOffset + i + 3);
                int w_packed = packBytesToInt(w0, w1, w2, w3);

                threadBlockSum = QuantizationUtils.dp4a_packed(w_packed, a_packed, threadBlockSum);
            }

            float weightScale = weightScales.get(scalesRowOffset + blockIdx).getFloat32();
            float activationScale = activationScales.get(blockIdx);
            partialRowSum += (float) threadBlockSum * weightScale * activationScale;

            context.localBarrier();
        }

        localSums[localId] = partialRowSum;
        context.localBarrier();

        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSums[localId] += localSums[localId + stride];
            }
            context.localBarrier();
        }

        if (localId == 0) {
            output.set(rowId, localSums[0]);
        }
    }

    public static void matrixVectorFusedQ8(
            KernelContext context,
            FloatArray activations,
            Int8Array weightsQ,
            HalfFloatArray weightScales,
            FloatArray output,
            int n, int d, int localWorkgroupSize) {

        int rowId = context.groupIdx;
        int localId = context.localIdx;
        int localSize = localWorkgroupSize;

        if (rowId >= d) {
            return;
        }

        float[] localFloatActivations = context.allocateFloatLocalArray(BLOCK_SIZE);
        byte[] localQuantizedActivations = context.allocateByteLocalArray(BLOCK_SIZE);
        float[] localSums = context.allocateFloatLocalArray(localSize);

        float partialRowSum = 0.0f;
        int weightRowOffset = rowId * n;
        int scalesRowOffset = rowId * (n / BLOCK_SIZE);

        for (int blockIdx = 0; blockIdx < (n / BLOCK_SIZE); blockIdx++) {
            int blockStart = blockIdx * BLOCK_SIZE;

            for (int i = localId; i < BLOCK_SIZE; i += localSize) {
                localFloatActivations[i] = activations.get(blockStart + i);
            }
            context.localBarrier();

            float threadMaxAbs = 0.0f;
            for (int i = localId; i < BLOCK_SIZE; i += localSize) {
                threadMaxAbs = TornadoMath.max(threadMaxAbs, TornadoMath.abs(localFloatActivations[i]));
            }
            localSums[localId] = threadMaxAbs;
            context.localBarrier();

            for (int stride = localSize / 2; stride > 0; stride >>= 1) {
                if (localId < stride) {
                    localSums[localId] = TornadoMath.max(localSums[localId], localSums[localId + stride]);
                }
                context.localBarrier();
            }
            float maxAbs = localSums[0];

            float activationScale = (maxAbs == 0.0f) ? 0.0f : (maxAbs / 127.0f);
            if (activationScale != 0.0f) {
                float invScale = 1.0f / activationScale;
                for (int i = localId; i < BLOCK_SIZE; i += localSize) {
                    float val = localFloatActivations[i];
                    int q = (int) TornadoMath.floor((val * invScale) + 0.5f);
                    localQuantizedActivations[i] = (byte) TornadoMath.clamp(q, -127, 127);
                }
            } else {
                for (int i = localId; i < BLOCK_SIZE; i += localSize) {
                    localQuantizedActivations[i] = (byte) 0;
                }
            }
            context.localBarrier();

            int threadBlockSum = 0;
            if (activationScale != 0.0f) {
                int weightBlockOffset = weightRowOffset + blockStart;
                for (int i = localId * 4; i < BLOCK_SIZE; i += (localSize * 4)) {
                    byte a0 = localQuantizedActivations[i];
                    byte a1 = localQuantizedActivations[i + 1];
                    byte a2 = localQuantizedActivations[i + 2];
                    byte a3 = localQuantizedActivations[i + 3];
                    int a_packed = packBytesToInt(a0, a1, a2, a3);

                    byte w0 = weightsQ.get(weightBlockOffset + i);
                    byte w1 = weightsQ.get(weightBlockOffset + i + 1);
                    byte w2 = weightsQ.get(weightBlockOffset + i + 2);
                    byte w3 = weightsQ.get(weightBlockOffset + i + 3);
                    int w_packed = packBytesToInt(w0, w1, w2, w3);

                    threadBlockSum = QuantizationUtils.dp4a_packed(w_packed, a_packed, threadBlockSum);
                }
            }

            float weightScale = weightScales.get(scalesRowOffset + blockIdx).getFloat32();
            partialRowSum += (float) threadBlockSum * weightScale * activationScale;
        }

        localSums[localId] = partialRowSum;
        context.localBarrier();

        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSums[localId] += localSums[localId + stride];
            }
            context.localBarrier();
        }

        if (localId == 0) {
            output.set(rowId, localSums[0]);
        }
    }

    public static void matrixVectorInt8xFloat(
            KernelContext context,
            FloatArray activations,
            Int8Array weightsQ,
            HalfFloatArray weightScales,
            FloatArray output,
            int n, int d, int localWorkgroupSize) {

        int rowId = context.groupIdx;
        int localId = context.localIdx;
        int localSize = localWorkgroupSize;

        if (rowId >= d) {
            return;
        }

        float[] localSums = context.allocateFloatLocalArray(localSize);
        int rowOffset = rowId * n;
        int scalesRowOffset = rowId * (n / BLOCK_SIZE);
        float partialSum = 0.0f;

        for (int j = localId; j < n; j += localSize) {
            byte w_i8 = weightsQ.get(rowOffset + j);
            float w_f32 = (float) w_i8;

            int blockIdx = j / BLOCK_SIZE;
            float scale = weightScales.get(scalesRowOffset + blockIdx).getFloat32();

            partialSum += (w_f32 * scale) * activations.get(j);
        }
        localSums[localId] = partialSum;
        context.localBarrier();

        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSums[localId] += localSums[localId + stride];
            }
            context.localBarrier();
        }

        if (localId == 0) {
            output.set(rowId, localSums[0]);
        }
    }

    public static void matrixVectorInt8xFloatMultiAccum(
            KernelContext context,
            FloatArray activations,
            Int8Array weightsQ,
            HalfFloatArray weightScales,
            FloatArray output,
            int n, int d, int localWorkgroupSize) {

        int rowId = context.groupIdx;
        int localId = context.localIdx;
        int localSize = localWorkgroupSize;

        if (rowId >= d) {
            return;
        }

        float[] localSums = context.allocateFloatLocalArray(localSize);
        int rowOffset = rowId * n;
        int scalesRowOffset = rowId * (n / BLOCK_SIZE);

        float partialSum1 = 0.0f;
        float partialSum2 = 0.0f;
        float partialSum3 = 0.0f;
        float partialSum4 = 0.0f;

        for (int j = localId * 4; j < n - 3; j += localSize * 4) {
            int blockIdx = j / BLOCK_SIZE;
            float scale = weightScales.get(scalesRowOffset + blockIdx).getFloat32();

            partialSum1 += ((float) weightsQ.get(rowOffset + j) * scale) * activations.get(j);
            partialSum2 += ((float) weightsQ.get(rowOffset + j + 1) * scale) * activations.get(j + 1);
            partialSum3 += ((float) weightsQ.get(rowOffset + j + 2) * scale) * activations.get(j + 2);
            partialSum4 += ((float) weightsQ.get(rowOffset + j + 3) * scale) * activations.get(j + 3);
        }

        float partialSum = partialSum1 + partialSum2 + partialSum3 + partialSum4;

        for (int j = ((n / 4) * 4) + localId; j < n; j += localSize) {
            int blockIdx = j / BLOCK_SIZE;
            float scale = weightScales.get(scalesRowOffset + blockIdx).getFloat32();
            partialSum += ((float) weightsQ.get(rowOffset + j) * scale) * activations.get(j);
        }

        localSums[localId] = partialSum;
        context.localBarrier();

        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSums[localId] += localSums[localId + stride];
            }
            context.localBarrier();
        }

        if (localId == 0) {
            output.set(rowId, localSums[0]);
        }
    }

    /**
     * Runs the benchmark for different matrix sizes and reports results
     */
    public static void main(String[] args) {
        System.out.println("Matrix-Vector Multiplication Benchmark");
        System.out.println("======================================");

        // Default parameters
        int inputDim = 8192;   // Default input dimension (columns)
        int outputDim = 2048; // Default output dimension (rows)

        // Parse command line arguments if provided
        if (args.length >= 3) {
            try {
                inputDim = Integer.parseInt(args[0]);
                outputDim = Integer.parseInt(args[1]);
                LOCAL_WORK_GROUP_SIZE = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Error parsing dimensions. Using defaults.");
            }
        }

        System.out.println("Configuration:");
        System.out.println("- Input dimension (columns): " + inputDim);
        System.out.println("- Output dimension (rows): " + outputDim);
        System.out.println("- Local work group size: " + LOCAL_WORK_GROUP_SIZE);
        System.out.println("- Warmup iterations: " + WARM_UP_ITERATIONS);
        System.out.println("- Benchmark iterations: " + BENCHMARK_ITERATIONS);
        System.out.println();

        // Create data arrays
        FloatArray input = new FloatArray(inputDim);
        FloatArray weights = new FloatArray(inputDim * outputDim);
        HalfFloatArray fp16weights = new HalfFloatArray(inputDim * outputDim);
        FloatArray outputParallel = new FloatArray(outputDim);
        FloatArray outputPureTornado = new FloatArray(outputDim);
        FloatArray outputSeq = new FloatArray(outputDim);
        FloatArray outputDp4a = new FloatArray(outputDim);
        FloatArray outputFp16 = new FloatArray(outputDim);

        // Initialize data
        System.out.println("Initializing data...");
        fillRandomData(input, -1.0f, 1.0f);
        fillRandomData(weights, -0.1f, 0.1f);
        fillRandomDataFp16(fp16weights, -0.1f, 0.1f);

        // Arrays for timing measurements
        ArrayList<Long> sequentialTimers = new ArrayList<>();
        ArrayList<Long> kernelContextTimers = new ArrayList<>();
        ArrayList<Long> parallelTimers = new ArrayList<>();
        ArrayList<Long> dp4aMatVecTimers = new ArrayList<>();
        ArrayList<Long> fp16Timers = new ArrayList<>();

        // Set up TornadoVM execution
        System.out.println("Setting up TornadoVM execution...");
        WorkerGrid1D worker = new WorkerGrid1D(outputDim * LOCAL_WORK_GROUP_SIZE);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);
        worker.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weights).task("t0", MatrixVectorRowMajor::matrixVectorGeneric, new KernelContext(), input,
                outputParallel, weights, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE).transferToHost(DataTransferMode.EVERY_EXECUTION, outputParallel);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        TaskGraph taskGraphPure = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weights) //
                .task("t0", MatrixVectorRowMajor::matrixVectorParallel, input, outputPureTornado, weights, inputDim, outputDim) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputPureTornado); //

        ImmutableTaskGraph immutableTaskGraphParallel = taskGraphPure.snapshot();

        WorkerGrid1D workerFp16 = new WorkerGrid1D(outputDim * LOCAL_WORK_GROUP_SIZE);
        GridScheduler schedulerFp16 = new GridScheduler("s3.t0", workerFp16);
        workerFp16.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);

        TaskGraph taskGraphFp16 = new TaskGraph("s3")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, fp16weights)
                .task("t0", MatrixVectorRowMajor::matrixVectorGenericFP16, new KernelContext(), input,
                outputFp16, fp16weights, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputFp16);

        ImmutableTaskGraph immutableTaskGraphFp16 = taskGraphFp16.snapshot();

        // dp4a
        Int8Array weightsQuantized = new Int8Array(inputDim * outputDim);
        int weightBlocksPerRow = inputDim / BLOCK_SIZE;
        HalfFloatArray weightsScales = new HalfFloatArray(outputDim * weightBlocksPerRow);
        quantizeWeightsToQ8(weights, weightsQuantized, weightsScales, outputDim, inputDim);

        WorkerGrid1D hybridWorker = new WorkerGrid1D(outputDim * LOCAL_WORK_GROUP_SIZE);
        hybridWorker.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);
        GridScheduler schedulerDp4a = new GridScheduler("hybrid.t0", hybridWorker);
        TaskGraph taskGraphDp4a = new TaskGraph("hybrid")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weightsQuantized, weightsScales)
                .task("t0", MatrixVectorRowMajor::matrixVectorInt8xFloatMultiAccum, new KernelContext(), input, weightsQuantized, weightsScales, outputDp4a, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputDp4a);

        ImmutableTaskGraph immutableTaskGraphDp4a = taskGraphDp4a.snapshot();
        // ************
//        Int8Array weightsQuantized = new Int8Array(inputDim * outputDim);
//        int weightBlocksPerRow = inputDim / BLOCK_SIZE;
//        HalfFloatArray weightsScales = new HalfFloatArray(outputDim * weightBlocksPerRow);
//        quantizeWeightsToQ8(weights, weightsQuantized, weightsScales, outputDim, inputDim);
//
//        int numActivationBlocks = inputDim / BLOCK_SIZE;
//        Int8Array activationsQuantized = new Int8Array(inputDim);
//        FloatArray activationScales = new FloatArray(numActivationBlocks);
//
//        WorkerGrid1D quantizeWorker = new WorkerGrid1D(numActivationBlocks * LOCAL_WORK_GROUP_SIZE);
//        WorkerGrid1D matVecWorker = new WorkerGrid1D(outputDim * LOCAL_WORK_GROUP_SIZE);
//        GridScheduler schedulerDp4a = new GridScheduler();
//        schedulerDp4a.addWorkerGrid("s4_dp4a.quantize", quantizeWorker);
//        schedulerDp4a.addWorkerGrid("s4_dp4a.dp4amatvec", matVecWorker);
//
//        quantizeWorker.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);
//        matVecWorker.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);
//        TaskGraph taskGraphDp4a = new TaskGraph("s4_dp4a")
//                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, weightsQuantized, weightsScales, activationsQuantized, activationScales)
//                .task("quantize", MatrixVectorRowMajor::quantizeActivations_Q8_0, new KernelContext(), input, activationsQuantized, activationScales, inputDim, LOCAL_WORK_GROUP_SIZE)
//                .task("dp4amatvec", MatrixVectorRowMajor::matrixVectorQ8xQ8, new KernelContext(), weightsQuantized, weightsScales, activationsQuantized, activationScales, outputDp4a, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE)
//                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputDp4a);
//
//        ImmutableTaskGraph immutableTaskGraphDp4a = taskGraphDp4a.snapshot();
        // ************
        //===================
//        Int8Array weightsQuantized = new Int8Array(inputDim * outputDim);
//        int weightBlocksPerRow = inputDim / BLOCK_SIZE;
//        HalfFloatArray weightsScales = new HalfFloatArray(outputDim * weightBlocksPerRow);
//        quantizeWeightsToQ8(weights, weightsQuantized, weightsScales, outputDim, inputDim);
//
//        WorkerGrid1D workerDp4a = new WorkerGrid1D(outputDim * LOCAL_WORK_GROUP_SIZE);
//
//        GridScheduler schedulerDp4a = new GridScheduler();
//        schedulerDp4a.addWorkerGrid("s4_dp4a.dp4amatvec", workerDp4a);
//
//        workerDp4a.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);
//
//        TaskGraph taskGraphDp4a = new TaskGraph("s4_dp4a")
//                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weightsQuantized, weightsScales)
//                .task("dp4amatvec", MatrixVectorRowMajor::matrixVectorGenericQ8, new KernelContext(), input, outputDp4a, weightsQuantized, weightsScales, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE)
//                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputDp4a);
//
//        ImmutableTaskGraph immutableTaskGraphDp4a = taskGraphDp4a.snapshot();
        //===================
//        Int8Array w_quant = new Int8Array(weights.getSize());
//        FloatArray w_scale = new FloatArray(1);
//        quantizeFloatArray(weights, w_quant, w_scale);
//
//        FloatArray x_scale = new FloatArray(1);
//        FloatArray x_max = new FloatArray(1);
//        FloatArray inv_scale = new FloatArray(1);
//        Int8Array x_quant = new Int8Array(input.getSize());
//        //quantizeFloatArray(input, x_quant, x_scale);
//
//        WorkerGrid1D workerQuant = new WorkerGrid1D(inputDim);
//        WorkerGrid1D workerDp4a = new WorkerGrid1D(outputDim * LOCAL_WORK_GROUP_SIZE);
//
//        GridScheduler schedulerDp4a = new GridScheduler();
//        schedulerDp4a.addWorkerGrid("s4_dp4a.scales", workerQuant);
//        schedulerDp4a.addWorkerGrid("s4_dp4a.quantize", workerQuant);
//        schedulerDp4a.addWorkerGrid("s4_dp4a.dp4amatvec", workerDp4a);
//
//        workerQuant.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);
//        workerDp4a.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);
//
//        TaskGraph taskGraphDp4a = new TaskGraph("s4_dp4a")
//                //.transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, w_quant, w_scale)
//                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, x_max, inv_scale, w_quant, w_scale)
//                .task("scales", MatrixVectorRowMajor::reductionCalculateMax, new KernelContext(), x_max, input, x_scale, inv_scale, LOCAL_WORK_GROUP_SIZE)
//                .task("quantize", MatrixVectorRowMajor::quantizeKernelContext,new KernelContext(), input, inv_scale, x_quant)
//                .task("dp4amatvec", MatrixVectorRowMajor::matrixVectorGenericDP4A, new KernelContext(), w_quant, x_quant,
//                        outputDp4a, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE, w_scale, x_scale)
//                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputDp4a);
//
//        ImmutableTaskGraph immutableTaskGraphDp4a = taskGraphDp4a.snapshot();

        // Warm-up sequential version
        System.out.println("Warming up sequential implementation...");
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            matrixVectorSequential(input, outputSeq, weights, inputDim, outputDim);
        }

        // Benchmark sequential version
        System.out.println("Benchmarking sequential implementation...");
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            matrixVectorSequential(input, outputSeq, weights, inputDim, outputDim);
            long end = System.nanoTime();
            sequentialTimers.add(end - start);
        }

        // TornadoVM execution with benchmark
        System.out.println("Benchmarking TornadoVM implementation...");
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        TornadoExecutionPlan executionPlan2 = new TornadoExecutionPlan(immutableTaskGraphParallel);
        TornadoExecutionPlan executionPlan3 = new TornadoExecutionPlan(immutableTaskGraphFp16);
        TornadoExecutionPlan executionPlanDp4a = new TornadoExecutionPlan(immutableTaskGraphDp4a);

        executionPlan.withGridScheduler(scheduler);

        // Warm-up parallel version
        System.out.println("Warming up parallel implementation...");
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            executionPlan2.execute();
        }

        executionPlan3.withGridScheduler(schedulerFp16);

        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            executionPlan3.withGridScheduler(schedulerFp16).execute();
        }

        executionPlanDp4a.withGridScheduler(schedulerDp4a);
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            executionPlanDp4a.withGridScheduler(schedulerDp4a).execute();
        }

        // Benchmark parallel version
        System.out.println("Benchmarking parallel implementation...");
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            executionPlan.withGridScheduler(scheduler).execute();
            long end = System.nanoTime();
            kernelContextTimers.add(end - start);
        }

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            executionPlan2.execute();
            long end = System.nanoTime();
            parallelTimers.add(end - start);
        }

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            executionPlan3.execute();
            long end = System.nanoTime();
            fp16Timers.add(end - start);
        }

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            executionPlanDp4a.withGridScheduler(schedulerDp4a).execute();
            long end = System.nanoTime();
            dp4aMatVecTimers.add(end - start);
        }

        // Validate results
        System.out.println("Validating results...");
        boolean isValid = true;
        float maxError = 0.0f;
        float maxError2 = 0.0f;
        float maxError3 = 0.0f;
        float maxError4 = 0.0f;

        for (int i = 0; i < outputDim; i++) {
            float error = Math.abs(outputSeq.get(i) - outputParallel.get(i));
            maxError = Math.max(maxError, error);

            float error2 = Math.abs(outputSeq.get(i) - outputPureTornado.get(i));
            maxError2 = Math.max(maxError2, error2);

            float error3 = Math.abs(outputSeq.get(i) - outputFp16.get(i));
            maxError3 = Math.max(maxError3, error3);

            float error4 = Math.abs(outputSeq.get(i) - outputDp4a.get(i));
            maxError4 = Math.max(maxError4, error4);

            if (error > DELTA) {
                System.out.printf("[KernelContext] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n", i, outputSeq.get(i), outputParallel.get(i), error);
                isValid = false;
            }

            if (error2 > DELTA) {
                System.out.printf("[@Parallel] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n", i, outputSeq.get(i), outputPureTornado.get(i), error2);
                isValid = false;
            }

            if (error3 > DELTA) {
                System.out.printf("[KernelContext FP16] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n", i, outputSeq.get(i), outputFp16.get(i), error3);
                isValid = false;
            }

            if (error4 > DELTA_Q) {
                System.out.printf("[DP4A] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n", i, outputSeq.get(i), outputDp4a.get(i), error4);
                isValid = false;
            }
        }

        if (isValid) {
            System.out.println("Validation PASSED ✓");
        } else {
            System.out.println("[KernelContext] Maximum error: " + maxError);

            System.out.println("[@Parallel] Maximum error: " + maxError2);

            System.out.println("[KernelContext FP16] Maximum error: " + maxError3);

            System.out.println("[DP4A] Maximum error: " + maxError4);
        }

        // Compute and report performance statistics
        LongSummaryStatistics statsSeq = sequentialTimers.stream().mapToLong(Long::longValue).summaryStatistics();
        LongSummaryStatistics statsKernelContext = kernelContextTimers.stream().mapToLong(Long::longValue).summaryStatistics();
        LongSummaryStatistics statsParallel = parallelTimers.stream().mapToLong(Long::longValue).summaryStatistics();
        LongSummaryStatistics statsFp16 = fp16Timers.stream().mapToLong(Long::longValue).summaryStatistics();
        LongSummaryStatistics statsDp4a = dp4aMatVecTimers.stream().mapToLong(Long::longValue).summaryStatistics();

        // Calculate GFLOP/s (2*inputDim operations per output element)
        long flopsPerRow = 2L * inputDim; // multiply + add for each element
        long totalFlops = flopsPerRow * outputDim;
        double seqGFlops = (totalFlops * 1e-9) / (statsSeq.getAverage() * 1e-9);
        double kernelContextGFlops = (totalFlops * 1e-9) / (statsKernelContext.getAverage() * 1e-9);
        double parallelGFlops = (totalFlops * 1e-9) / (statsParallel.getAverage() * 1e-9);
        double fp16GFlops = (totalFlops * 1e-9) / (statsFp16.getAverage() * 1e-9);
        double dp4aGFlops = (totalFlops * 1e-9) / (statsDp4a.getAverage() * 1e-9);

        System.out.println("\nPerformance Results:");
        System.out.println("====================");
        System.out.printf("Matrix size: %d x %d\n", outputDim, inputDim);

        System.out.println("Sequential Implementation:");
        System.out.printf("  Average time: %.3f ms\n", statsSeq.getAverage() / 1_000_000);
        System.out.printf("  Min time: %.3f ms\n", (double) statsSeq.getMin() / 1_000_000);
        System.out.printf("  Max time: %.3f ms\n", (double) statsSeq.getMax() / 1_000_000);
        System.out.printf("  Performance: %.2f GFLOP/s\n", seqGFlops);

        System.out.println("Parallel Implementation (TornadoVM):");
        System.out.printf("  Average time: %.3f ms\n", statsKernelContext.getAverage() / 1_000_000);
        System.out.printf("  Min time: %.3f ms\n", (double) statsKernelContext.getMin() / 1_000_000);
        System.out.printf("  Max time: %.3f ms\n", (double) statsKernelContext.getMax() / 1_000_000);
        System.out.printf("  Performance: %.2f GFLOP/s\n", kernelContextGFlops);

        System.out.println("Pure TornadoVM @Parallel Implementation (TornadoVM):");
        System.out.printf("  Average time: %.3f ms\n", statsParallel.getAverage() / 1_000_000);
        System.out.printf("  Min time: %.3f ms\n", (double) statsParallel.getMin() / 1_000_000);
        System.out.printf("  Max time: %.3f ms\n", (double) statsParallel.getMax() / 1_000_000);
        System.out.printf("  Performance: %.2f GFLOP/s\n", parallelGFlops);

        System.out.println("Parallel Implementation FP16 (TornadoVM):");
        System.out.printf("  Average time: %.3f ms\n", statsFp16.getAverage() / 1_000_000);
        System.out.printf("  Min time: %.3f ms\n", (double) statsFp16.getMin() / 1_000_000);
        System.out.printf("  Max time: %.3f ms\n", (double) statsFp16.getMax() / 1_000_000);
        System.out.printf("  Performance: %.2f GFLOP/s\n", fp16GFlops);

        System.out.println("DP4A:");
        System.out.printf("  Average time: %.3f ms\n", statsDp4a.getAverage() / 1_000_000);
        System.out.printf("  Min time: %.3f ms\n", (double) statsDp4a.getMin() / 1_000_000);
        System.out.printf("  Max time: %.3f ms\n", (double) statsDp4a.getMax() / 1_000_000);
        System.out.printf("  Performance: %.2f GFLOP/s\n", dp4aGFlops);

        double speedup = statsSeq.getAverage() / statsKernelContext.getAverage();
        System.out.printf("\nSpeedup: KernelContext vs Java %.2fx\n", speedup);

        double speedup2 = statsSeq.getAverage() / statsParallel.getAverage();
        System.out.printf("\nSpeedup: @Parallel vs Java %.2fx\n", speedup2);

        double speedup3 = statsParallel.getAverage() / statsKernelContext.getAverage();
        System.out.printf("\nSpeedup: KernelContext vs @Parallel %.2fx\n", speedup3);

        double speedup4 = statsKernelContext.getAverage() / statsDp4a.getAverage();
        System.out.printf("\nSpeedup: DP4A vs KernelContext %.2fx\n", speedup4);

        double speedup5 = statsFp16.getAverage() / statsDp4a.getAverage();
        System.out.printf("\nSpeedup: DP4A vs KernelContext FP16 %.2fx\n", speedup5);

    }
}
