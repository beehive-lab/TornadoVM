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
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoAPIException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
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
    private static final int TILE_SIZE = 128;
    private static final int BLOCK_SIZE = 32;

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

    public static void reductionCalculateMax(KernelContext context, FloatArray max, FloatArray x, FloatArray x_scale, FloatArray inv_scale, int localMemSize, int arraySize) {
        int gid = context.globalIdx;
        int lid = context.localIdx;
        int groupId = context.groupIdx;
        int groupSize = context.localGroupSizeX;

        float[] localX = context.allocateFloatLocalArray(localMemSize);

        if (gid < arraySize) {
            localX[lid] = TornadoMath.abs(x.get(gid));
        } else {
            localX[lid] = 0.0f;
        }

        for (int stride = (groupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (lid < stride) {
                localX[lid] = TornadoMath.max(localX[lid], localX[lid + stride]);
            }
        }

        if (lid == 0) {
            max.set(groupId, localX[0]);
        }

        if (gid == 0) {
            int numGroups = (arraySize + groupSize - 1) / groupSize;

            float max_abs_val = 0.0f;
            for (int i = 0; i < numGroups; i++) {
                max_abs_val = TornadoMath.max(max_abs_val, max.get(i));
            }

            max.set(0, max_abs_val);

            float scale = (max_abs_val == 0.0f) ? 1.0f : max_abs_val / 127.0f;
            inv_scale.set(0, 1.0f / scale);
            x_scale.set(0, scale);
        }
    }

    public static void quantizeKernelContext(KernelContext context, FloatArray x, FloatArray inv_scale, Int8Array x_quant) {
        int gid = context.globalIdx;

        float scale = inv_scale.get(0);
        x_quant.set(gid, (byte) TornadoMath.floor((x.get(gid) * scale) + 0.5f));
    }

    public static void quantizeWeightsToQ8(FloatArray weightsFP32, Int8Array outQ, HalfFloatArray outScales, int rows, int cols) {
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

    public static void quantizeWeightsToQ4_0(FloatArray weightsFP32, Int8Array outQ, HalfFloatArray outScales, int rows, int cols) {
        int blocksPerRow = cols / BLOCK_SIZE;

        for (int r = 0; r < rows; r++) {
            int rowBase = r * cols;
            for (int b = 0; b < blocksPerRow; b++) {
                int blockStart = rowBase + b * BLOCK_SIZE;

                // compute max abs (Q4_0 format)
                float maxAbs = 0.0f;
                for (int i = 0; i < BLOCK_SIZE; i++) {
                    float v = weightsFP32.get(blockStart + i);
                    float a = Math.abs(v);
                    if (a > maxAbs) maxAbs = a;
                }

                // For Q4_0, values are quantized to [-7, 7] range (4-bit signed)
                float scale = (maxAbs == 0.0f) ? 0.0f : (maxAbs / 7.0f);

                // store scale as HalfFloat (matches GGUF Q4_0 format)
                int globalBlockIdx = r * blocksPerRow + b;
                outScales.set(globalBlockIdx, new HalfFloat(scale));

                float inv = (scale == 0.0f) ? 0.0f : 1.0f / scale;

                // quantize and pack block - 2 values per byte
                for (int i = 0; i < BLOCK_SIZE; i += 2) {
                    float val1 = weightsFP32.get(blockStart + i);
                    float val2 = weightsFP32.get(blockStart + i + 1);

                    int q1 = Math.round(val1 * inv);
                    int q2 = Math.round(val2 * inv);

                    // Clamp to 4-bit range [-7, 7]
                    if (q1 > 7) q1 = 7;
                    else if (q1 < -7) q1 = -7;
                    if (q2 > 7) q2 = 7;
                    else if (q2 < -7) q2 = -7;

                    // Pack two 4-bit values into one byte
                    // Lower 4 bits: first value, Upper 4 bits: second value
                    byte packed = (byte) ((q1 & 0x0F) | ((q2 & 0x0F) << 4));
                    outQ.set(blockStart / 2 + i / 2, packed);
                }
            }
        }
    }

    public static void matrixVectorGenericFinal(KernelContext context, FloatArray x, FloatArray output, Int8Array weightsQ, HalfFloatArray weightScales, int dim1, int dim0, int localWorkGroupSize) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        if (rowId >= dim0) {
            return;
        }

        float sum = matrixVectorRowMajorOptimizedQ8_0Final(context, localWorkGroupSize, x, weightsQ, weightScales, dim1);

        // Thread 0 writes the result
        if (localId == 0) {
            output.set(rowId, sum);
        }
    }

    /**
     * Helper method to compute dot product for a single row with Q8_0 quantized weights.
     * Uses 4-way unrolling for better performance.
     */
    public static float matrixVectorRowMajorOptimizedQ8_0Final(KernelContext context, int localSize, FloatArray x, Int8Array weightsQ, HalfFloatArray weightScales, int n) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;
        int blockSize = 32;

        float[] localSums = context.allocateFloatLocalArray(localSize);

        int rowOffset = rowId * n;
        int scalesRowOffset = rowId * (n / blockSize);

        // 4-way unrolling
        float partialSum1 = 0.0f;
        float partialSum2 = 0.0f;
        float partialSum3 = 0.0f;
        float partialSum4 = 0.0f;

        // Main loop - process 4 elements at a time
        for (int j = localId * 4; j < n - 3; j += localSize * 4) {
            int blockIdx = j / blockSize;
            float scale = weightScales.get(scalesRowOffset + blockIdx).getFloat32();

            // Dequantize and multiply
            partialSum1 += ((float) weightsQ.get(rowOffset + j) * scale) * x.get(j);
            partialSum2 += ((float) weightsQ.get(rowOffset + j + 1) * scale) * x.get(j + 1);
            partialSum3 += ((float) weightsQ.get(rowOffset + j + 2) * scale) * x.get(j + 2);
            partialSum4 += ((float) weightsQ.get(rowOffset + j + 3) * scale) * x.get(j + 3);
        }

        float partialSum = partialSum1 + partialSum2 + partialSum3 + partialSum4;

        for (int j = ((n / 4) * 4) + localId; j < n; j += localSize) {
            int blockIdx = j / blockSize;
            float scale = weightScales.get(scalesRowOffset + blockIdx).getFloat32();
            partialSum += ((float) weightsQ.get(rowOffset + j) * scale) * x.get(j);
        }

        localSums[localId] = partialSum;
        context.localBarrier();

        // Parallel reduction
        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSums[localId] += localSums[localId + stride];
            }
            context.localBarrier();
        }

        return localSums[0];
    }

    public static void matrixVectorGenericQ4_0(KernelContext context, FloatArray x, FloatArray output, Int8Array weightsQ, HalfFloatArray weightScales, int dim1, int dim0, int localWorkGroupSize) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        if (rowId >= dim0) {
            return;
        }

        float sum = matrixVectorRowMajorOptimizedQ4_0(context, localWorkGroupSize, x, weightsQ, weightScales, dim1);

        // Thread 0 writes the result
        if (localId == 0) {
            output.set(rowId, sum);
        }
    }

    /**
     * Helper method to compute dot product for a single row with Q4_0 quantized weights.
     * Q4_0 packs two 4-bit values per byte.
     */
    public static float matrixVectorRowMajorOptimizedQ4_0(KernelContext context, int localSize, FloatArray x, Int8Array weightsQ, HalfFloatArray weightScales, int n) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;
        int blockSize = 32;

        float[] localSums = context.allocateFloatLocalArray(localSize);

        int rowOffset = rowId * n / 2; // Q4_0 uses half the storage (2 values per byte)
        int scalesRowOffset = rowId * (n / blockSize);

        float partialSum = 0.0f;

        // Process elements - each byte contains 2 quantized values
        for (int j = localId * 2; j < n; j += localSize * 2) {
            int blockIdx = j / blockSize;
            float scale = weightScales.get(scalesRowOffset + blockIdx).getFloat32();

            // Read packed byte
            byte packed = (byte) weightsQ.get(rowOffset + j / 2);

            // Unpack two 4-bit values
            // Lower 4 bits: first value
            int q1 = (packed & 0x0F);
            // Upper 4 bits: second value
            int q2 = ((packed >> 4) & 0x0F);

            // Convert from unsigned to signed 4-bit [-7, 7]
            if (q1 > 7) q1 -= 16;
            if (q2 > 7) q2 -= 16;

            // Dequantize and multiply
            partialSum += ((float) q1 * scale) * x.get(j);
            if (j + 1 < n) {
                partialSum += ((float) q2 * scale) * x.get(j + 1);
            }
        }

        localSums[localId] = partialSum;
        context.localBarrier();

        // Parallel reduction
        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSums[localId] += localSums[localId + stride];
            }
            context.localBarrier();
        }

        return localSums[0];
    }

    public static float matrixVectorRowMajorOptimizedDP4A(KernelContext context, int localSize, Int8Array w_quant, Int8Array x_quant, int n, float w_scale, float x_scale) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        if (localId >= localSize) {
            return 0.0f;
        }

        int[] localSum = context.allocateIntLocalArray(localSize);
        int rowOffset = rowId * n;
        int partialSum = 0;

        for (int j = localId * 4; j < n; j += localSize * 4) {
            partialSum = QuantizationUtils.dp4a(w_quant, rowOffset + j, x_quant, j, partialSum);
        }

        localSum[localId] = partialSum;
        context.localBarrier();

        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSum[localId] += localSum[localId + stride];
            }
            context.localBarrier();
        }

        if (localId == 0) {
            return QuantizationUtils.dequantizeFusedResult(localSum[0], w_scale, x_scale);
        }
        return 0.0f;
    }


    public static void matrixVectorGenericDP4A(KernelContext context, Int8Array w_quant, Int8Array x_quant, FloatArray output, int n, int d, int localWorkGroupSize, FloatArray w_scale, FloatArray x_scale) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        if (rowId >= d) {
            return;
        }

        float sum = matrixVectorRowMajorOptimizedDP4A(context, localWorkGroupSize, w_quant, x_quant, n, w_scale.get(0), x_scale.get(0));

        if (localId == 0) {
            output.set(rowId, sum);
        }
    }

    public static void matrixVectorGenericLocalMemory(KernelContext context, Int8Array w_quant, Int8Array x_quant, FloatArray output, int n, int d, int localWorkGroupSize, FloatArray w_scale, FloatArray x_scale) {
        int rowId = context.groupIdx;

        if (rowId >= d) {
            return;
        }

        int intSum = matrixVectorDP4ALocalMemory(context, localWorkGroupSize, w_quant, x_quant, n); //matrixVectorOptimizedDP4A(context, localWorkGroupSize, w_quant, x_quant, n); //matrixVectorTiledDP4A(context, localWorkGroupSize, w_quant, x_quant, n);

        if (context.localIdx == 0) {
            float finalValue = (float) intSum * w_scale.get(0) * x_scale.get(0);
            output.set(rowId, finalValue);
        }
    }

    public static int matrixVectorDP4ALocalMemory(KernelContext context, int localSize, Int8Array w_quant, Int8Array x_quant, int n) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        // Local memory to hold a tile of the x_quant vector
        byte[] x_tile = context.allocateByteLocalArray(TILE_SIZE);

        int[] localSum = context.allocateIntLocalArray(localSize);

        int rowOffset = rowId * n;
        int totalSum = 0;

        for (int tileStart = 0; tileStart < n; tileStart += TILE_SIZE) {
            for (int i = localId; i < TILE_SIZE && (tileStart + i) < n; i += localSize) {
                x_tile[i] = (byte) x_quant.get(tileStart + i);
            }

            context.localBarrier();

            int partialSum = 0;
            for (int j = localId * 4; j < TILE_SIZE; j += localSize * 4) {
                if (tileStart + j < n) {
                    partialSum = QuantizationUtils.dp4a(w_quant, rowOffset + tileStart + j, x_tile, j, partialSum);
                }
            }
            totalSum += partialSum;

            context.localBarrier();
        }

        localSum[localId] = totalSum;
        context.localBarrier();

        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSum[localId] += localSum[localId + stride];
            }
            context.localBarrier();
        }

        return localSum[0];
    }

    public static void matrixVectorGenericPacked(KernelContext context, Int8Array w_quant, Int8Array x_quant, FloatArray output, int n, int d, int localWorkGroupSize, FloatArray w_scale, FloatArray x_scale) {
        int rowId = context.groupIdx;

        if (rowId >= d) {
            return;
        }

        int intSum = matrixVectorPacked(context, localWorkGroupSize, w_quant, x_quant, n);

        if (context.localIdx == 0) {
            float finalValue = (float) intSum * w_scale.get(0) * x_scale.get(0);
            output.set(rowId, finalValue);
        }
    }


    public static int matrixVectorPacked(KernelContext context, int localSize, Int8Array w_quant,
                                         Int8Array x_quant, int n) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        int[] x_tile_packed = context.allocateIntLocalArray(TILE_SIZE / 4);
        int[] localSum = context.allocateIntLocalArray(localSize);

        int rowOffset = rowId * n;
        int totalSum = 0;

        int nPacked = n / 4;

        for (int tileStart = 0; tileStart < nPacked; tileStart += TILE_SIZE / 4) {
            int tileSize = Math.min(TILE_SIZE / 4, nPacked - tileStart);

            for (int i = localId; i < tileSize; i += localSize) {
                int globalIdx = (tileStart + i) * 4;
                if (globalIdx < n) {
                    byte b0 = (byte) x_quant.get(globalIdx);
                    byte b1 = (byte) (globalIdx + 1 < n ? x_quant.get(globalIdx + 1) : 0);
                    byte b2 = (byte) (globalIdx + 2 < n ? x_quant.get(globalIdx + 2) : 0);
                    byte b3 = (byte) (globalIdx + 3 < n ? x_quant.get(globalIdx + 3) : 0);

                    int packed = (b0 & 0xFF) | ((b1 & 0xFF) << 8) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 24);
                    x_tile_packed[i] = packed;
                }
            }

            context.localBarrier();

            int partialSum = 0;
            for (int i = localId; i < tileSize; i += localSize) {
                int w_globalIdx = rowOffset + (tileStart + i) * 4;
                if (w_globalIdx < rowOffset + n) {
                    byte b0 = (byte) w_quant.get(w_globalIdx);
                    byte b1 = (byte) (w_globalIdx + 1 < rowOffset + n ? w_quant.get(w_globalIdx + 1) : 0);
                    byte b2 = (byte) (w_globalIdx + 2 < rowOffset + n ? w_quant.get(w_globalIdx + 2) : 0);
                    byte b3 = (byte) (w_globalIdx + 3 < rowOffset + n ? w_quant.get(w_globalIdx + 3) : 0);

                    int w_packed = (b0 & 0xFF) | ((b1 & 0xFF) << 8) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 24);

                    partialSum = QuantizationUtils.dp4a_packed(w_packed, x_tile_packed[i], partialSum);
                }
            }

            totalSum += partialSum;
            context.localBarrier();
        }

        localSum[localId] = totalSum;
        context.localBarrier();

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

    public static void matrixVectorGeneric4WayDP4A(KernelContext context, Int8Array w_quant, Int8Array x_quant, FloatArray output, int n, int d, int localWorkGroupSize, FloatArray w_scale, FloatArray x_scale) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        if (rowId >= d) {
            return;
        }

        float sum = matrixVectorDP4A4Way(context, localWorkGroupSize, w_quant, x_quant, n, w_scale.get(0), x_scale.get(0));

        if (localId == 0) {
            output.set(rowId, sum);
        }
    }

    public static float matrixVectorDP4A4Way(KernelContext context, int localSize,
                                                  Int8Array w_quant, Int8Array x_quant, int n,
                                                  float w_scale, float x_scale) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        int[] localSum = context.allocateIntLocalArray(localSize);

        int rowOffset = rowId * n;
        float combinedScale = w_scale * x_scale;

        // Process 16 bytes (4 dp4a operations) per iteration
        int partialSum = 0;
        int stride = localSize * 16;  // Each thread processes 16 bytes per iteration
        int limit = n - 15;

        for (int j = localId * 16; j < limit; j += stride) {
            int wOffset = rowOffset + j;

            // 4x unrolled DP4A calls - processes 16 bytes
            partialSum = QuantizationUtils.dp4a(w_quant, wOffset, x_quant, j, partialSum);
            partialSum = QuantizationUtils.dp4a(w_quant, wOffset + 4, x_quant, j + 4, partialSum);
            partialSum = QuantizationUtils.dp4a(w_quant, wOffset + 8, x_quant, j + 8, partialSum);
            partialSum = QuantizationUtils.dp4a(w_quant, wOffset + 12, x_quant, j + 12, partialSum);
        }

        int remaining = ((n / 16) * 16) + localId * 4;
        for (int j = remaining; j < n; j += localSize * 4) {
            if (j + 3 < n) {
                partialSum = QuantizationUtils.dp4a(w_quant, rowOffset + j, x_quant, j, partialSum);
            }
        }

        localSum[localId] = partialSum;
        context.localBarrier();

        for (int stride2 = localSize / 2; stride2 > 0; stride2 >>= 1) {
            if (localId < stride2) {
                localSum[localId] += localSum[localId + stride2];
            }
            context.localBarrier();
        }


        return localSum[0] * combinedScale;
    }

    private static boolean isPTXBackend() {
        int driverIndex = TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getBackendIndex();
        TornadoVMBackendType backend = TornadoRuntimeProvider.getTornadoRuntime().getBackendType(driverIndex);
        return backend == TornadoVMBackendType.PTX;
    }

    private static void assertBackend() {
        if (!isPTXBackend()) {
            throw new TornadoAPIException("DP4A is a PTX instruction. It is not supported for other backends.", new Exception());
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

        boolean supportsDP4A = isPTXBackend();

        System.out.println("Configuration:");
        System.out.println("- Input dimension (columns): " + inputDim);
        System.out.println("- Output dimension (rows): " + outputDim);
        System.out.println("- Local work group size: " + LOCAL_WORK_GROUP_SIZE);
        System.out.println("- Backend: " + TornadoRuntimeProvider.getTornadoRuntime().getBackendType(
                TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getBackendIndex()));
        System.out.println("- DP4A benchmarks enabled: " + supportsDP4A);
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
        FloatArray outputQ8Vec = new FloatArray(outputDim);
        FloatArray outputQ4Vec = new FloatArray(outputDim);
        FloatArray outputFp16 = new FloatArray(outputDim);

        // DP4A-specific arrays (only if enabled)
        FloatArray outputQ8DP4A = supportsDP4A ? new FloatArray(outputDim) : null;
        FloatArray outputQ8DP4APacked = supportsDP4A ? new FloatArray(outputDim) : null;
        FloatArray outputQ8DP4ALocal = supportsDP4A ? new FloatArray(outputDim) : null;
        FloatArray outputQ84DP4A = supportsDP4A ? new FloatArray(outputDim) : null;

        System.out.println("Initializing data...");
        fillRandomData(input, -1.0f, 1.0f);
        fillRandomData(weights, -0.1f, 0.1f);
        fillRandomDataFp16(fp16weights, -0.1f, 0.1f);

        // Timing arrays
        ArrayList<Long> sequentialTimers = new ArrayList<>();
        ArrayList<Long> kernelContextTimers = new ArrayList<>();
        ArrayList<Long> parallelTimers = new ArrayList<>();
        ArrayList<Long> q8VectorizedTimers = new ArrayList<>();
        ArrayList<Long> q4VectorizedTimers = new ArrayList<>();
        ArrayList<Long> fp16Timers = new ArrayList<>();

        ArrayList<Long> q8Dp4aTimers = supportsDP4A ? new ArrayList<>() : null;
        ArrayList<Long> q8Dp4aLocalTimers = supportsDP4A ? new ArrayList<>() : null;
        ArrayList<Long> q8Dp4aPackedTimers = supportsDP4A ? new ArrayList<>() : null;
        ArrayList<Long> q8Dp4a4WayTimers = supportsDP4A ? new ArrayList<>() : null;

        System.out.println("Setting up TornadoVM execution...");

        // Standard benchmarks (always run)
        WorkerGrid1D worker = new WorkerGrid1D(outputDim * LOCAL_WORK_GROUP_SIZE);
        GridScheduler scheduler = new GridScheduler("s0.t0", worker);
        worker.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weights)
                .task("t0", MatrixVectorRowMajor::matrixVectorGeneric, new KernelContext(), input,
                        outputParallel, weights, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputParallel);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        TaskGraph taskGraphPure = new TaskGraph("s1")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weights)
                .task("t0", MatrixVectorRowMajor::matrixVectorParallel, input, outputPureTornado, weights, inputDim, outputDim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputPureTornado);

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

        // Q8 vectorized
        Int8Array weightsQuantized = new Int8Array(inputDim * outputDim);
        int weightBlocksPerRow = inputDim / BLOCK_SIZE;
        HalfFloatArray weightsScales = new HalfFloatArray(outputDim * weightBlocksPerRow);
        quantizeWeightsToQ8(weights, weightsQuantized, weightsScales, outputDim, inputDim);

        WorkerGrid1D q8VectorWorker = new WorkerGrid1D(outputDim * LOCAL_WORK_GROUP_SIZE);
        q8VectorWorker.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);
        GridScheduler schedulerQ8Vectorized = new GridScheduler("vectorized.t0", q8VectorWorker);

        TaskGraph taskGraphQ8Vectorized = new TaskGraph("vectorized")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weightsQuantized, weightsScales)
                .task("t0", MatrixVectorRowMajor::matrixVectorGenericFinal, new KernelContext(), input, outputQ8Vec,
                        weightsQuantized, weightsScales, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputQ8Vec);

        ImmutableTaskGraph immutableTaskGraphQ8Vectorized = taskGraphQ8Vectorized.snapshot();

        // Q4_0 vectorized
        Int8Array weightsQuantizedQ4 = new Int8Array(inputDim * outputDim / 2); // Q4_0 uses half the storage
        HalfFloatArray weightsScalesQ4 = new HalfFloatArray(outputDim * weightBlocksPerRow);
        quantizeWeightsToQ4_0(weights, weightsQuantizedQ4, weightsScalesQ4, outputDim, inputDim);

        WorkerGrid1D q4VectorWorker = new WorkerGrid1D(outputDim * LOCAL_WORK_GROUP_SIZE);
        q4VectorWorker.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);
        GridScheduler schedulerQ4Vectorized = new GridScheduler("vectorized_q4.t0", q4VectorWorker);

        TaskGraph taskGraphQ4Vectorized = new TaskGraph("vectorized_q4")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weightsQuantizedQ4, weightsScalesQ4)
                .task("t0", MatrixVectorRowMajor::matrixVectorGenericQ4_0, new KernelContext(), input, outputQ4Vec,
                        weightsQuantizedQ4, weightsScalesQ4, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputQ4Vec);

        ImmutableTaskGraph immutableTaskGraphQ4Vectorized = taskGraphQ4Vectorized.snapshot();

        // DP4A benchmarks (only setup if PTX)
        ImmutableTaskGraph immutableTaskGraphDp4a = null;
        ImmutableTaskGraph immutableTaskGraphDp4aPacked = null;
        ImmutableTaskGraph immutableTaskGraphDp4aLocal = null;
        ImmutableTaskGraph immutableTaskGraphDp4a4Way = null;

        GridScheduler schedulerDp4a = null;
        GridScheduler schedulerDp4aPacked = null;
        GridScheduler schedulerDp4aLocalMem = null;
        GridScheduler schedulerDp4a4Way = null;

        if (supportsDP4A) {
            System.out.println("Setting up DP4A benchmarks...");

            Int8Array w_quant = new Int8Array(weights.getSize());
            FloatArray w_scale = new FloatArray(1);
            quantizeFloatArray(weights, w_quant, w_scale);

            FloatArray x_scale = new FloatArray(1);
            int maxNumGroups = (inputDim + LOCAL_WORK_GROUP_SIZE - 1) / LOCAL_WORK_GROUP_SIZE;
            FloatArray x_max = new FloatArray(maxNumGroups);
            FloatArray inv_scale = new FloatArray(1);
            Int8Array x_quant = new Int8Array(input.getSize());

            WorkerGrid1D workerQuant = new WorkerGrid1D(inputDim);
            WorkerGrid1D workerDp4a = new WorkerGrid1D(LOCAL_WORK_GROUP_SIZE * inputDim);

            // DP4A standard
            schedulerDp4a = new GridScheduler();
            schedulerDp4a.addWorkerGrid("s0_quant_kc.scales", workerQuant);
            schedulerDp4a.addWorkerGrid("s0_quant_kc.quantize", workerQuant);
            schedulerDp4a.addWorkerGrid("s0_quant_kc.dp4amatvec", workerDp4a);

            workerQuant.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);
            workerDp4a.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);

            TaskGraph taskGraphDp4a = new TaskGraph("s0_quant_kc")
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, x_max, inv_scale, w_quant, w_scale)
                    .task("scales", MatrixVectorRowMajor::reductionCalculateMax, new KernelContext(), x_max, input, x_scale, inv_scale, LOCAL_WORK_GROUP_SIZE, inputDim)
                    .task("quantize", MatrixVectorRowMajor::quantizeKernelContext, new KernelContext(), input, inv_scale, x_quant)
                    .task("dp4amatvec", MatrixVectorRowMajor::matrixVectorGenericDP4A, new KernelContext(), w_quant, x_quant,
                            outputQ8DP4A, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE, w_scale, x_scale)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, outputQ8DP4A);

            immutableTaskGraphDp4a = taskGraphDp4a.snapshot();

            // DP4A packed
            WorkerGrid1D workerQuantPacked = new WorkerGrid1D(inputDim);
            WorkerGrid1D workerDp4aPacked = new WorkerGrid1D(LOCAL_WORK_GROUP_SIZE * inputDim);

            schedulerDp4aPacked = new GridScheduler();
            schedulerDp4aPacked.addWorkerGrid("s0_quant_kc_packed.scales", workerQuantPacked);
            schedulerDp4aPacked.addWorkerGrid("s0_quant_kc_packed.quantize", workerQuantPacked);
            schedulerDp4aPacked.addWorkerGrid("s0_quant_kc_packed.dp4amatvec", workerDp4aPacked);

            workerQuantPacked.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);
            workerDp4aPacked.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);

            TaskGraph taskGraphDp4aPacked = new TaskGraph("s0_quant_kc_packed")
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, x_max, inv_scale, w_quant, w_scale)
                    .task("scales", MatrixVectorRowMajor::reductionCalculateMax, new KernelContext(), x_max, input, x_scale, inv_scale, LOCAL_WORK_GROUP_SIZE, inputDim)
                    .task("quantize", MatrixVectorRowMajor::quantizeKernelContext, new KernelContext(), input, inv_scale, x_quant)
                    .task("dp4amatvec", MatrixVectorRowMajor::matrixVectorGenericPacked, new KernelContext(), w_quant, x_quant,
                            outputQ8DP4APacked, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE, w_scale, x_scale)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, outputQ8DP4APacked);

            immutableTaskGraphDp4aPacked = taskGraphDp4aPacked.snapshot();

            // DP4A local memory
            WorkerGrid1D workerQuantLocalMem = new WorkerGrid1D(inputDim);
            WorkerGrid1D workerDp4aLocalMem = new WorkerGrid1D(LOCAL_WORK_GROUP_SIZE * inputDim);

            schedulerDp4aLocalMem = new GridScheduler();
            schedulerDp4aLocalMem.addWorkerGrid("s0_quant_kc_local.scales", workerQuantLocalMem);
            schedulerDp4aLocalMem.addWorkerGrid("s0_quant_kc_local.quantize", workerQuantLocalMem);
            schedulerDp4aLocalMem.addWorkerGrid("s0_quant_kc_local.dp4amatvec", workerDp4aLocalMem);

            workerQuantLocalMem.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);
            workerDp4aLocalMem.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);

            TaskGraph taskGraphDp4aLocal = new TaskGraph("s0_quant_kc_local")
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, x_max, inv_scale, w_quant, w_scale)
                    .task("scales", MatrixVectorRowMajor::reductionCalculateMax, new KernelContext(), x_max, input, x_scale, inv_scale, LOCAL_WORK_GROUP_SIZE, inputDim)
                    .task("quantize", MatrixVectorRowMajor::quantizeKernelContext, new KernelContext(), input, inv_scale, x_quant)
                    .task("dp4amatvec", MatrixVectorRowMajor::matrixVectorGenericLocalMemory, new KernelContext(), w_quant, x_quant,
                            outputQ8DP4ALocal, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE, w_scale, x_scale)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, outputQ8DP4ALocal);

            immutableTaskGraphDp4aLocal = taskGraphDp4aLocal.snapshot();

            // DP4A 4-way
            WorkerGrid1D workerQuant4Way = new WorkerGrid1D(inputDim);
            WorkerGrid1D workerDp4a4way = new WorkerGrid1D(LOCAL_WORK_GROUP_SIZE * inputDim);

            schedulerDp4a4Way = new GridScheduler();
            schedulerDp4a4Way.addWorkerGrid("s0_quant_kc_4way.scales", workerQuant4Way);
            schedulerDp4a4Way.addWorkerGrid("s0_quant_kc_4way.quantize", workerQuant4Way);
            schedulerDp4a4Way.addWorkerGrid("s0_quant_kc_4way.dp4amatvec", workerDp4a4way);

            workerQuant4Way.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);
            workerDp4a4way.setLocalWork(LOCAL_WORK_GROUP_SIZE, 1, 1);

            TaskGraph taskGraphDp4a4Way = new TaskGraph("s0_quant_kc_4way")
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, x_max, inv_scale, w_quant, w_scale)
                    .task("scales", MatrixVectorRowMajor::reductionCalculateMax, new KernelContext(), x_max, input, x_scale, inv_scale, LOCAL_WORK_GROUP_SIZE, inputDim)
                    .task("quantize", MatrixVectorRowMajor::quantizeKernelContext, new KernelContext(), input, inv_scale, x_quant)
                    .task("dp4amatvec", MatrixVectorRowMajor::matrixVectorGeneric4WayDP4A, new KernelContext(), w_quant, x_quant,
                            outputQ84DP4A, inputDim, outputDim, LOCAL_WORK_GROUP_SIZE, w_scale, x_scale)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, outputQ84DP4A);

            immutableTaskGraphDp4a4Way = taskGraphDp4a4Way.snapshot();
        }

        // Warm-up and benchmark sequential
        System.out.println("Warming up sequential implementation...");
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            matrixVectorSequential(input, outputSeq, weights, inputDim, outputDim);
        }

        System.out.println("Benchmarking sequential implementation...");
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            matrixVectorSequential(input, outputSeq, weights, inputDim, outputDim);
            long end = System.nanoTime();
            sequentialTimers.add(end - start);
        }

        // Create execution plans
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        TornadoExecutionPlan executionPlan2 = new TornadoExecutionPlan(immutableTaskGraphParallel);
        TornadoExecutionPlan executionPlan3 = new TornadoExecutionPlan(immutableTaskGraphFp16);
        TornadoExecutionPlan executionPlanQ8Vectorized = new TornadoExecutionPlan(immutableTaskGraphQ8Vectorized);
        TornadoExecutionPlan executionPlanQ4Vectorized = new TornadoExecutionPlan(immutableTaskGraphQ4Vectorized);

        TornadoExecutionPlan executionPlanQ8Dp4a = supportsDP4A ? new TornadoExecutionPlan(immutableTaskGraphDp4a) : null;
        TornadoExecutionPlan executionPlanQ8Dp4aPacked = supportsDP4A ? new TornadoExecutionPlan(immutableTaskGraphDp4aPacked) : null;
        TornadoExecutionPlan executionPlanQ8Dp4aLocal = supportsDP4A ? new TornadoExecutionPlan(immutableTaskGraphDp4aLocal) : null;
        TornadoExecutionPlan executionPlanQ8Dp4a4Way = supportsDP4A ? new TornadoExecutionPlan(immutableTaskGraphDp4a4Way) : null;

        // Warm-up standard benchmarks
        System.out.println("Warming up parallel implementation...");
        executionPlan.withGridScheduler(scheduler);
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

        executionPlanQ8Vectorized.withGridScheduler(schedulerQ8Vectorized);
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            executionPlanQ8Vectorized.withGridScheduler(schedulerQ8Vectorized).execute();
        }

        executionPlanQ4Vectorized.withGridScheduler(schedulerQ4Vectorized);
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            executionPlanQ4Vectorized.withGridScheduler(schedulerQ4Vectorized).execute();
        }

        // Warm-up DP4A benchmarks (if enabled)
        if (supportsDP4A) {
            System.out.println("Warming up DP4A implementations...");

            executionPlanQ8Dp4a.withGridScheduler(schedulerDp4a);
            for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
                executionPlanQ8Dp4a.withGridScheduler(schedulerDp4a).execute();
            }

            executionPlanQ8Dp4aPacked.withGridScheduler(schedulerDp4aPacked);
            for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
                executionPlanQ8Dp4aPacked.withGridScheduler(schedulerDp4aPacked).execute();
            }

            executionPlanQ8Dp4aLocal.withGridScheduler(schedulerDp4aLocalMem);
            for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
                executionPlanQ8Dp4aLocal.withGridScheduler(schedulerDp4aLocalMem).execute();
            }

            executionPlanQ8Dp4a4Way.withGridScheduler(schedulerDp4a4Way);
            for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
                executionPlanQ8Dp4a4Way.withGridScheduler(schedulerDp4a4Way).execute();
            }
        }

        // Benchmark standard implementations
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
            executionPlanQ8Vectorized.withGridScheduler(schedulerQ8Vectorized).execute();
            long end = System.nanoTime();
            q8VectorizedTimers.add(end - start);
        }

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            executionPlanQ4Vectorized.withGridScheduler(schedulerQ4Vectorized).execute();
            long end = System.nanoTime();
            q4VectorizedTimers.add(end - start);
        }

        // Benchmark DP4A implementations (if enabled)
        if (supportsDP4A) {
            System.out.println("Benchmarking DP4A implementations...");

            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                executionPlanQ8Dp4a.withGridScheduler(schedulerDp4a).execute();
                long end = System.nanoTime();
                q8Dp4aTimers.add(end - start);
            }

            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                executionPlanQ8Dp4aPacked.withGridScheduler(schedulerDp4aPacked).execute();
                long end = System.nanoTime();
                q8Dp4aPackedTimers.add(end - start);
            }

            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                executionPlanQ8Dp4aLocal.withGridScheduler(schedulerDp4aLocalMem).execute();
                long end = System.nanoTime();
                q8Dp4aLocalTimers.add(end - start);
            }

            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                executionPlanQ8Dp4a4Way.withGridScheduler(schedulerDp4a4Way).execute();
                long end = System.nanoTime();
                q8Dp4a4WayTimers.add(end - start);
            }
        }

        // Validate results
        System.out.println("Validating results...");
        boolean isValid = true;
        float maxError = 0.0f;
        float maxError2 = 0.0f;
        float maxError3 = 0.0f;
        float maxError4 = 0.0f;
        float maxError4b = 0.0f;
        float maxError5 = 0.0f;
        float maxError6 = 0.0f;
        float maxError7 = 0.0f;
        float maxError8 = 0.0f;

        for (int i = 0; i < outputDim; i++) {
            float error = Math.abs(outputSeq.get(i) - outputParallel.get(i));
            maxError = Math.max(maxError, error);

            float error2 = Math.abs(outputSeq.get(i) - outputPureTornado.get(i));
            maxError2 = Math.max(maxError2, error2);

            float error3 = Math.abs(outputSeq.get(i) - outputFp16.get(i));
            maxError3 = Math.max(maxError3, error3);

            float error4 = Math.abs(outputSeq.get(i) - outputQ8Vec.get(i));
            maxError4 = Math.max(maxError4, error4);

            float error4b = Math.abs(outputSeq.get(i) - outputQ4Vec.get(i));
            maxError4b = Math.max(maxError4b, error4b);

            if (error > DELTA) {
                System.out.printf("[KernelContext] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n",
                        i, outputSeq.get(i), outputParallel.get(i), error);
                isValid = false;
            }

            if (error2 > DELTA) {
                System.out.printf("[@Parallel] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n",
                        i, outputSeq.get(i), outputPureTornado.get(i), error2);
                isValid = false;
            }

            if (error3 > DELTA) {
                System.out.printf("[KernelContext FP16] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n",
                        i, outputSeq.get(i), outputFp16.get(i), error3);
                isValid = false;
            }

            if (error4 > DELTA_Q) {
                System.out.printf("[Q8 Vectorized] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n",
                        i, outputSeq.get(i), outputQ8Vec.get(i), error4);
                isValid = false;
            }

            if (error4b > DELTA_Q) {
                System.out.printf("[Q4_0 Vectorized] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n",
                        i, outputSeq.get(i), outputQ4Vec.get(i), error4b);
                isValid = false;
            }

            if (supportsDP4A) {
                float error5 = Math.abs(outputSeq.get(i) - outputQ8DP4A.get(i));
                maxError5 = Math.max(maxError5, error5);

                float error6 = Math.abs(outputSeq.get(i) - outputQ8DP4APacked.get(i));
                maxError6 = Math.max(maxError6, error6);

                float error7 = Math.abs(outputSeq.get(i) - outputQ8DP4ALocal.get(i));
                maxError7 = Math.max(maxError7, error7);

                float error8 = Math.abs(outputSeq.get(i) - outputQ84DP4A.get(i));
                maxError8 = Math.max(maxError8, error8);

                if (error5 > DELTA_Q) {
                    System.out.printf("[DP4A] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n",
                            i, outputSeq.get(i), outputQ8DP4A.get(i), error5);
                    isValid = false;
                }

                if (error6 > DELTA_Q) {
                    System.out.printf("[DP4A Packed] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n",
                            i, outputSeq.get(i), outputQ8DP4APacked.get(i), error6);
                    isValid = false;
                }

                if (error7 > DELTA_Q) {
                    System.out.printf("[DP4A Local Memory] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n",
                            i, outputSeq.get(i), outputQ8DP4ALocal.get(i), error7);
                    isValid = false;
                }

                if (error8 > DELTA_Q) {
                    System.out.printf("[DP4A 4-WAY] Error at index %d: Expected %.6f, Actual %.6f, Diff %.6f\n",
                            i, outputSeq.get(i), outputQ84DP4A.get(i), error8);
                    isValid = false;
                }
            }
        }

        if (isValid) {
            System.out.println("Validation PASSED ✓");
        } else {
            System.out.println("[KernelContext] Maximum error: " + maxError);
            System.out.println("[@Parallel] Maximum error: " + maxError2);
            System.out.println("[KernelContext FP16] Maximum error: " + maxError3);
            System.out.println("[Q8 Vectorized] Maximum error: " + maxError4);
            System.out.println("[Q4_0 Vectorized] Maximum error: " + maxError4b);

            if (supportsDP4A) {
                System.out.println("[Q8 DP4A] Maximum error: " + maxError5);
                System.out.println("[Q8 DP4A Packed] Maximum error: " + maxError6);
                System.out.println("[Q8 DP4A Local Memory] Maximum error: " + maxError7);
                System.out.println("[Q8 DP4A 4-WAY] Maximum error: " + maxError8);
            }
        }

        // Compute performance statistics
        LongSummaryStatistics statsSeq = sequentialTimers.stream().mapToLong(Long::longValue).summaryStatistics();
        LongSummaryStatistics statsKernelContext = kernelContextTimers.stream().mapToLong(Long::longValue).summaryStatistics();
        LongSummaryStatistics statsParallel = parallelTimers.stream().mapToLong(Long::longValue).summaryStatistics();
        LongSummaryStatistics statsFp16 = fp16Timers.stream().mapToLong(Long::longValue).summaryStatistics();
        LongSummaryStatistics statsQ8Vectorized = q8VectorizedTimers.stream().mapToLong(Long::longValue).summaryStatistics();
        LongSummaryStatistics statsQ4Vectorized = q4VectorizedTimers.stream().mapToLong(Long::longValue).summaryStatistics();

        LongSummaryStatistics statsQ8Dp4a = supportsDP4A ? q8Dp4aTimers.stream().mapToLong(Long::longValue).summaryStatistics() : null;
        LongSummaryStatistics statsQ8Dp4aPacked = supportsDP4A ? q8Dp4aPackedTimers.stream().mapToLong(Long::longValue).summaryStatistics() : null;
        LongSummaryStatistics statsQ8Dp4aLocal = supportsDP4A ? q8Dp4aLocalTimers.stream().mapToLong(Long::longValue).summaryStatistics() : null;
        LongSummaryStatistics statsQ8Dp4a4Way = supportsDP4A ? q8Dp4a4WayTimers.stream().mapToLong(Long::longValue).summaryStatistics() : null;

        // Calculate GFLOP/s
        long flopsPerRow = 2L * inputDim;
        long totalFlops = flopsPerRow * outputDim;
        double seqGFlops = (totalFlops * 1e-9) / (statsSeq.getAverage() * 1e-9);
        double kernelContextGFlops = (totalFlops * 1e-9) / (statsKernelContext.getAverage() * 1e-9);
        double parallelGFlops = (totalFlops * 1e-9) / (statsParallel.getAverage() * 1e-9);
        double fp16GFlops = (totalFlops * 1e-9) / (statsFp16.getAverage() * 1e-9);
        double q8VectorizedGFlops = (totalFlops * 1e-9) / (statsQ8Vectorized.getAverage() * 1e-9);
        double q4VectorizedGFlops = (totalFlops * 1e-9) / (statsQ4Vectorized.getAverage() * 1e-9);

        Double q8Dp4aGFlops = supportsDP4A ? (totalFlops * 1e-9) / (statsQ8Dp4a.getAverage() * 1e-9) : null;
        Double q8Dp4aPackedGFlops = supportsDP4A ? (totalFlops * 1e-9) / (statsQ8Dp4aPacked.getAverage() * 1e-9) : null;
        Double q8Dp4aLocalGFlops = supportsDP4A ? (totalFlops * 1e-9) / (statsQ8Dp4aLocal.getAverage() * 1e-9) : null;
        Double q8Dp4a4WayGFlops = supportsDP4A ? (totalFlops * 1e-9) / (statsQ8Dp4a4Way.getAverage() * 1e-9) : null;

        // Report results
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

        System.out.println("Q8 Vectorized:");
        System.out.printf("  Average time: %.3f ms\n", statsQ8Vectorized.getAverage() / 1_000_000);
        System.out.printf("  Min time: %.3f ms\n", (double) statsQ8Vectorized.getMin() / 1_000_000);
        System.out.printf("  Max time: %.3f ms\n", (double) statsQ8Vectorized.getMax() / 1_000_000);
        System.out.printf("  Performance: %.2f GFLOP/s\n", q8VectorizedGFlops);

        System.out.println("Q4_0 Vectorized:");
        System.out.printf("  Average time: %.3f ms\n", statsQ4Vectorized.getAverage() / 1_000_000);
        System.out.printf("  Min time: %.3f ms\n", (double) statsQ4Vectorized.getMin() / 1_000_000);
        System.out.printf("  Max time: %.3f ms\n", (double) statsQ4Vectorized.getMax() / 1_000_000);
        System.out.printf("  Performance: %.2f GFLOP/s\n", q4VectorizedGFlops);

        if (supportsDP4A) {
            System.out.println("Q8 DP4A:");
            System.out.printf("  Average time: %.3f ms\n", statsQ8Dp4a.getAverage() / 1_000_000);
            System.out.printf("  Min time: %.3f ms\n", (double) statsQ8Dp4a.getMin() / 1_000_000);
            System.out.printf("  Max time: %.3f ms\n", (double) statsQ8Dp4a.getMax() / 1_000_000);
            System.out.printf("  Performance: %.2f GFLOP/s\n", q8Dp4aGFlops);

            System.out.println("Q8 DP4A Packed:");
            System.out.printf("  Average time: %.3f ms\n", statsQ8Dp4aPacked.getAverage() / 1_000_000);
            System.out.printf("  Min time: %.3f ms\n", (double) statsQ8Dp4aPacked.getMin() / 1_000_000);
            System.out.printf("  Max time: %.3f ms\n", (double) statsQ8Dp4aPacked.getMax() / 1_000_000);
            System.out.printf("  Performance: %.2f GFLOP/s\n", q8Dp4aPackedGFlops);

            System.out.println("Q8 DP4A Local Memory:");
            System.out.printf("  Average time: %.3f ms\n", statsQ8Dp4aLocal.getAverage() / 1_000_000);
            System.out.printf("  Min time: %.3f ms\n", (double) statsQ8Dp4aLocal.getMin() / 1_000_000);
            System.out.printf("  Max time: %.3f ms\n", (double) statsQ8Dp4aLocal.getMax() / 1_000_000);
            System.out.printf("  Performance: %.2f GFLOP/s\n", q8Dp4aLocalGFlops);

            System.out.println("Q8 DP4A 4-WAY:");
            System.out.printf("  Average time: %.3f ms\n", statsQ8Dp4a4Way.getAverage() / 1_000_000);
            System.out.printf("  Min time: %.3f ms\n", (double) statsQ8Dp4a4Way.getMin() / 1_000_000);
            System.out.printf("  Max time: %.3f ms\n", (double) statsQ8Dp4a4Way.getMax() / 1_000_000);
            System.out.printf("  Performance: %.2f GFLOP/s\n", q8Dp4a4WayGFlops);
        }

        // Speedup calculations
        double speedup = statsSeq.getAverage() / statsKernelContext.getAverage();
        System.out.printf("\nSpeedup: KernelContext vs Java %.2fx\n", speedup);

        double speedup2 = statsSeq.getAverage() / statsParallel.getAverage();
        System.out.printf("Speedup: @Parallel vs Java %.2fx\n", speedup2);

        double speedup3 = statsParallel.getAverage() / statsKernelContext.getAverage();
        System.out.printf("Speedup: KernelContext vs @Parallel %.2fx\n", speedup3);

        double speedup4 = statsKernelContext.getAverage() / statsQ8Vectorized.getAverage();
        System.out.printf("Speedup: Q8 Vectorized vs KernelContext %.2fx\n", speedup4);

        double speedup5 = statsFp16.getAverage() / statsQ8Vectorized.getAverage();
        System.out.printf("Speedup: Q8 Vectorized vs KernelContext FP16 %.2fx\n", speedup5);

        double speedup5b = statsFp16.getAverage() / statsQ4Vectorized.getAverage();
        System.out.printf("Speedup: Q4_0 Vectorized vs KernelContext FP16 %.2fx\n", speedup5b);

        double speedup5c = statsQ8Vectorized.getAverage() / statsQ4Vectorized.getAverage();
        System.out.printf("Speedup: Q4_0 Vectorized vs Q8 Vectorized %.2fx\n", speedup5c);

        if (supportsDP4A) {
            double speedup6 = statsFp16.getAverage() / statsQ8Dp4a.getAverage();
            System.out.printf("Speedup: Q8 DP4A vs KernelContext FP16 %.2fx\n", speedup6);

            double speedup7 = statsFp16.getAverage() / statsQ8Dp4aPacked.getAverage();
            System.out.printf("Speedup: Q8 DP4A Packed vs KernelContext FP16 %.2fx\n", speedup7);

            double speedup8 = statsFp16.getAverage() / statsQ8Dp4aLocal.getAverage();
            System.out.printf("Speedup: Q8 DP4A Local vs KernelContext FP16 %.2fx\n", speedup8);

            double speedup9 = statsFp16.getAverage() / statsQ8Dp4a4Way.getAverage();
            System.out.printf("Speedup: Q8 DP4A 4-Way vs KernelContext FP16 %.2fx\n", speedup9);
        } else {
            System.out.println("\n[DP4A benchmarks skipped - not running on PTX backend]");
        }
    }
}
