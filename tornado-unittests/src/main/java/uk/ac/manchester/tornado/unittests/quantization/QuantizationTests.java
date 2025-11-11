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
package uk.ac.manchester.tornado.unittests.quantization;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.utils.QuantizationUtils;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.quantization.QuantizationTests
 * </code>
 * </p>
 */
public class QuantizationTests extends TornadoTestBase {
    // CHECKSTYLE:OFF

    private static final int TILE_SIZE = 128;

    public static void performDP4A(Int8Array a, Int8Array b, IntArray result) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            int dot = QuantizationUtils.dp4a(a, i * 4, b, i * 4,0);
            result.set(i, dot);
        }
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

    /**
     * Tiled helper method to compute the dot product for a single row using DP4A and local memory.
     * This kernel returns the final integer sum for the row.
     */
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


    public static int matrixVectorPacked(KernelContext context, int localSize, Int8Array w_quant, Int8Array x_quant, int n) {
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

    /**
     * A sequential, host-side implementation of the entire process for verification.
     */
    private void matrixVectorSequential(FloatArray output, FloatArray weights, FloatArray input, int n, int d) {
        for (int i = 0; i < d; i++) {
            float sum = 0.0f;
            for (int j = 0; j < n; j++) {
                sum += weights.get(i * n + j) * input.get(j);
            }
            output.set(i, sum);
        }
    }

    private static void fillRandomData(FloatArray array, float min, float max, Random random) {
        float range = max - min;
        for (int i = 0; i < array.getSize(); i++) {
            array.set(i, min + random.nextFloat() * range);
        }
    }

    public static void matrixVectorGenericVectorized(KernelContext context, FloatArray x, FloatArray output, Int8Array weightsQ, HalfFloatArray weightScales, int dim1, int dim0, int localWorkGroupSize) {

        // One row per workgroup
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        // Early exit if this workgroup is beyond output dimension
        if (rowId >= dim0) {
            return;
        }

        float sum = matrixVectorRowMajorOptimizedVectorized(
                context, localWorkGroupSize, x, weightsQ, weightScales, dim1
        );

        // Thread 0 writes the result
        if (localId == 0) {
            output.set(rowId, sum);
        }
    }

    public static float matrixVectorRowMajorOptimizedVectorized(KernelContext context, int localSize, FloatArray x, Int8Array weightsQ, HalfFloatArray weightScales, int n) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;
        int blockSize = 32;

        // Allocate local memory for reduction
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

        // Handle remaining elements
        for (int j = ((n / 4) * 4) + localId; j < n; j += localSize) {
            int blockIdx = j / blockSize;
            float scale = weightScales.get(scalesRowOffset + blockIdx).getFloat32();
            partialSum += ((float) weightsQ.get(rowOffset + j) * scale) * x.get(j);
        }

        // Store partial sum
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

    public static void matrixVectorGenericDP4A4Way(KernelContext context, Int8Array w_quant, Int8Array x_quant, FloatArray output, int n, int d, int localWorkGroupSize, FloatArray w_scale, FloatArray x_scale) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        if (rowId >= d) {
            return;
        }

        float sum = matrixVectorDP4A4Way(context, localWorkGroupSize, w_quant, x_quant, n,
                w_scale.get(0), x_scale.get(0));

        if (localId == 0) {
            output.set(rowId, sum);
        }
    }

    public static float matrixVectorDP4A4Way(KernelContext context, int localSize, Int8Array w_quant, Int8Array x_quant, int n, float w_scale, float x_scale) {
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

        // Handle remaining elements in groups of 4
        int remaining = ((n / 16) * 16) + localId * 4;
        for (int j = remaining; j < n; j += localSize * 4) {
            if (j + 3 < n) {
                partialSum = QuantizationUtils.dp4a(w_quant, rowOffset + j, x_quant, j, partialSum);
            }
        }

        // Store partial sum
        localSum[localId] = partialSum;
        context.localBarrier();

        // Parallel reduction
        for (int stride2 = localSize / 2; stride2 > 0; stride2 >>= 1) {
            if (localId < stride2) {
                localSum[localId] += localSum[localId + stride2];
            }
            context.localBarrier();
        }

        // Convert to float and apply scale once at the end
        return localSum[0] * combinedScale;
    }

    public static void quantizeWeightsToQ8(FloatArray weightsFP32,
                                           Int8Array outQ,
                                           HalfFloatArray outScales,
                                           int rows,
                                           int cols, int blockSize) {
        if ((cols % blockSize) != 0) {
            throw new IllegalArgumentException("cols must be multiple of BLOCK_SIZE=" + blockSize);
        }
        int blocksPerRow = cols / blockSize;

        for (int r = 0; r < rows; r++) {
            int rowBase = r * cols;
            for (int b = 0; b < blocksPerRow; b++) {
                int blockStart = rowBase + b * blockSize;

                // compute max abs (Q8_0 format)
                float maxAbs = 0.0f;
                for (int i = 0; i < blockSize; i++) {
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
                for (int i = 0; i < blockSize; i++) {
                    float val = weightsFP32.get(blockStart + i);
                    int q = Math.round(val * inv);
                    if (q > 127) q = 127;
                    else if (q < -127) q = -127;
                    outQ.set(blockStart + i, (byte) q);
                }
            }
        }
    }

    @Test
    public void testDP4A() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        
        int N = 512;
        Int8Array a = new Int8Array(N);
        Int8Array b = new Int8Array(N);
        IntArray result = new IntArray(N / 4);
        IntArray resultSeq = new IntArray(N / 4);

        Random r = new Random();
        IntStream.range(0, N).sequential().forEach(i -> {
           a.set(i, (byte) r.nextInt());
           b.set(i, (byte) r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
                .task("t0", QuantizationTests::performDP4A, a, b, result)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan tornadoExecutor = new TornadoExecutionPlan(immutableTaskGraph)) {
            tornadoExecutor.execute();
        }

        performDP4A(a, b, resultSeq);

        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(resultSeq.get(i), result.get(i), 0.0001);
        }
    }

    @Test
    public void testQuantization() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.SPIRV);

        Random random = new Random(42);
        int local_workgroup_size = 128;
        int inputDim = 8192;   // Default input dimension (columns)

        FloatArray input = new FloatArray(inputDim);
        fillRandomData(input, -1.0f, 1.0f, random);

        FloatArray x_scale = new FloatArray(1);
        FloatArray x_max = new FloatArray(1);
        FloatArray inv_scale = new FloatArray(1);
        Int8Array x_quant = new Int8Array(input.getSize());

        WorkerGrid1D worker = new WorkerGrid1D(inputDim);
        GridScheduler scheduler = new GridScheduler();
        scheduler.addWorkerGrid("s0_quant.scales", worker);
        scheduler.addWorkerGrid("s0_quant.quantize", worker);
        worker.setLocalWork(local_workgroup_size, 1, 1);

        int numGroups = (inputDim + local_workgroup_size - 1) / local_workgroup_size;

        TaskGraph taskGraphQ = new TaskGraph("s0_quant")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, x_max, inv_scale)
                .task("scales", QuantizationTests::reductionCalculateMax, new KernelContext(), x_max, input, x_scale, inv_scale, local_workgroup_size, inputDim)
                .task("quantize", QuantizationTests::quantizeKernelContext,new KernelContext(), input, inv_scale, x_quant)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, x_quant, x_scale);

        ImmutableTaskGraph immutableTaskGraphQ = taskGraphQ.snapshot();
        try (TornadoExecutionPlan tornadoExecutor = new TornadoExecutionPlan(immutableTaskGraphQ)) {
            tornadoExecutor.withGridScheduler(scheduler).execute();
        }

        for (int i = 0; i < input.getSize(); i++) {
            assertEquals(input.get(i), x_quant.get(i) * x_scale.get(0), 0.01f);
        }

    }

    @Test
    public void testMatrixVectorDP4AKernelContext() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        
        Random random = new Random(42);
        int local_workgroup_size = 32;
        int inputDim = 8192;   // Default input dimension (columns)
        int outputDim = 2048; // Default output dimension (rows)

        FloatArray input = new FloatArray(inputDim);
        FloatArray weights = new FloatArray(inputDim * outputDim);
        FloatArray output = new FloatArray(outputDim);
        FloatArray outputSeq = new FloatArray(outputDim);
        fillRandomData(input, -1.0f, 1.0f, random);
        fillRandomData(weights, -0.1f, 0.1f, random);

        // already quantized

        Int8Array w_quant = new Int8Array(weights.getSize());
        FloatArray w_scale = new FloatArray(1);
        quantizeFloatArray(weights, w_quant, w_scale);

        FloatArray x_scale = new FloatArray(1);
        int maxNumGroups = (inputDim + local_workgroup_size - 1) / local_workgroup_size;
        FloatArray x_max = new FloatArray(maxNumGroups);
        FloatArray inv_scale = new FloatArray(1);
        Int8Array x_quant = new Int8Array(input.getSize());

        WorkerGrid1D workerQuant = new WorkerGrid1D(inputDim);
        WorkerGrid1D workerDp4a = new WorkerGrid1D(local_workgroup_size * inputDim);

        GridScheduler schedulerDp4a = new GridScheduler();
        schedulerDp4a.addWorkerGrid("s0_quant_kc.scales", workerQuant);
        schedulerDp4a.addWorkerGrid("s0_quant_kc.quantize", workerQuant);
        schedulerDp4a.addWorkerGrid("s0_quant_kc.dp4amatvec", workerDp4a);

        workerQuant.setLocalWork(local_workgroup_size, 1, 1);
        workerDp4a.setLocalWork(local_workgroup_size, 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0_quant_kc")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, x_max, inv_scale, w_quant, w_scale)
                .task("scales", QuantizationTests::reductionCalculateMax, new KernelContext(), x_max, input, x_scale, inv_scale, local_workgroup_size, inputDim)
                .task("quantize", QuantizationTests::quantizeKernelContext,new KernelContext(), input, inv_scale, x_quant)
                .task("dp4amatvec", QuantizationTests::matrixVectorGenericDP4A, new KernelContext(), w_quant, x_quant,
                        output, inputDim, outputDim, local_workgroup_size, w_scale, x_scale)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output, x_quant, x_scale, x_max, inv_scale);

        ImmutableTaskGraph immutabletaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan tornadoExecutor = new TornadoExecutionPlan(immutabletaskGraph)) {
            TornadoExecutionResult result = tornadoExecutor.withGridScheduler(schedulerDp4a).execute();
            System.out.println("Execution result: " + result);
        }

        System.out.println("First 10 max values (per-workgroup maxes):");
        for (int i = 0; i < 10; i++) {
            System.out.print(x_max.get(i) + " ");
        }
        System.out.println();

        matrixVectorSequential(outputSeq, weights, input, inputDim, outputDim);

        for (int i = 0; i < output.getSize(); i++) {
            assertEquals(outputSeq.get(i), output.get(i), 0.1f);
        }

    }

    @Test
    public void testMatrixVectorDP4AKernelLocalMemory() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        
        Random random = new Random(42);
        int local_workgroup_size = 32;
        int inputDim = 8192;   // Default input dimension (columns)
        int outputDim = 2048; // Default output dimension (rows)

        FloatArray input = new FloatArray(inputDim);
        FloatArray weights = new FloatArray(inputDim * outputDim);
        FloatArray output = new FloatArray(outputDim);
        FloatArray outputSeq = new FloatArray(outputDim);
        fillRandomData(input, -1.0f, 1.0f, random);
        fillRandomData(weights, -0.1f, 0.1f, random);

        // already quantized
        Int8Array w_quant = new Int8Array(weights.getSize());
        FloatArray w_scale = new FloatArray(1);
        quantizeFloatArray(weights, w_quant, w_scale);

        FloatArray x_scale = new FloatArray(1);
        int maxNumGroups = (inputDim + local_workgroup_size - 1) / local_workgroup_size;
        FloatArray x_max = new FloatArray(maxNumGroups);
        FloatArray inv_scale = new FloatArray(1);
        Int8Array x_quant = new Int8Array(input.getSize());

        WorkerGrid1D workerQuant = new WorkerGrid1D(inputDim);
        WorkerGrid1D workerDp4a = new WorkerGrid1D(local_workgroup_size * inputDim);

        GridScheduler schedulerDp4a = new GridScheduler();
        schedulerDp4a.addWorkerGrid("s0_quant_kc.scales", workerQuant);
        schedulerDp4a.addWorkerGrid("s0_quant_kc.quantize", workerQuant);
        schedulerDp4a.addWorkerGrid("s0_quant_kc.dp4amatvec", workerDp4a);

        workerQuant.setLocalWork(local_workgroup_size, 1, 1);
        workerDp4a.setLocalWork(local_workgroup_size, 1, 1);


        TaskGraph taskGraph = new TaskGraph("s0_quant_kc")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, x_max, inv_scale, w_quant, w_scale)
                .task("scales", QuantizationTests::reductionCalculateMax, new KernelContext(), x_max, input, x_scale, inv_scale, local_workgroup_size, inputDim)
                .task("quantize", QuantizationTests::quantizeKernelContext,new KernelContext(), input, inv_scale, x_quant)
                .task("dp4amatvec", QuantizationTests::matrixVectorGenericLocalMemory, new KernelContext(), w_quant, x_quant,
                        output, inputDim, outputDim, local_workgroup_size, w_scale, x_scale)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutabletaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan tornadoExecutor = new TornadoExecutionPlan(immutabletaskGraph)) {
            tornadoExecutor.withGridScheduler(schedulerDp4a).execute();
        }

        matrixVectorSequential(outputSeq, weights, input, inputDim, outputDim);

        for (int i = 0; i < output.getSize(); i++) {
           assertEquals(outputSeq.get(i), output.get(i), 0.1f);
        }

    }

    @Test
    public void testMatrixVectorDP4AKernelPacked() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        
        Random random = new Random(42);
        int local_workgroup_size = 32;
        int inputDim = 8192;   // Default input dimension (columns)
        int outputDim = 2048; // Default output dimension (rows)

        FloatArray input = new FloatArray(inputDim);
        FloatArray weights = new FloatArray(inputDim * outputDim);
        FloatArray output = new FloatArray(outputDim);
        FloatArray outputSeq = new FloatArray(outputDim);
        fillRandomData(input, -1.0f, 1.0f, random);
        fillRandomData(weights, -0.1f, 0.1f, random);

        // already quantized
        Int8Array w_quant = new Int8Array(weights.getSize());
        FloatArray w_scale = new FloatArray(1);
        quantizeFloatArray(weights, w_quant, w_scale);

        FloatArray x_scale = new FloatArray(1);
        int maxNumGroups = (inputDim + local_workgroup_size - 1) / local_workgroup_size;
        FloatArray x_max = new FloatArray(maxNumGroups);
        FloatArray inv_scale = new FloatArray(1);
        Int8Array x_quant = new Int8Array(input.getSize());

        WorkerGrid1D workerQuant = new WorkerGrid1D(inputDim);
        WorkerGrid1D workerDp4a = new WorkerGrid1D(local_workgroup_size * inputDim);

        GridScheduler schedulerDp4a = new GridScheduler();
        schedulerDp4a.addWorkerGrid("s0_quant_kc.scales", workerQuant);
        schedulerDp4a.addWorkerGrid("s0_quant_kc.quantize", workerQuant);
        schedulerDp4a.addWorkerGrid("s0_quant_kc.dp4amatvec", workerDp4a);

        workerQuant.setLocalWork(local_workgroup_size, 1, 1);
        workerDp4a.setLocalWork(local_workgroup_size, 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0_quant_kc")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, x_max, inv_scale, w_quant, w_scale)
                .task("scales", QuantizationTests::reductionCalculateMax, new KernelContext(), x_max, input, x_scale, inv_scale, local_workgroup_size, inputDim)
                .task("quantize", QuantizationTests::quantizeKernelContext,new KernelContext(), input, inv_scale, x_quant)
                .task("dp4amatvec", QuantizationTests::matrixVectorGenericPacked, new KernelContext(), w_quant, x_quant,
                        output, inputDim, outputDim, local_workgroup_size, w_scale, x_scale)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output, x_quant, x_scale, inv_scale, x_max);

        ImmutableTaskGraph immutabletaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan tornadoExecutor = new TornadoExecutionPlan(immutabletaskGraph)) {
            tornadoExecutor.withGridScheduler(schedulerDp4a).execute();
        }

        matrixVectorSequential(outputSeq, weights, input, inputDim, outputDim);

        for (int i = 0; i < output.getSize(); i++) {
            assertEquals(outputSeq.get(i), output.get(i), 0.1f);
        }

    }

    @Test
    public void testMatrixVector4WayDP4AKernel() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        
        Random random = new Random(42);
        int local_workgroup_size = 32;
        int inputDim = 8192;   // Default input dimension (columns)
        int outputDim = 2048; // Default output dimension (rows)

        FloatArray input = new FloatArray(inputDim);
        FloatArray weights = new FloatArray(inputDim * outputDim);
        FloatArray output = new FloatArray(outputDim);
        FloatArray outputSeq = new FloatArray(outputDim);
        fillRandomData(input, -1.0f, 1.0f, random);
        fillRandomData(weights, -0.1f, 0.1f, random);

        // already quantized
        Int8Array w_quant = new Int8Array(weights.getSize());
        FloatArray w_scale = new FloatArray(1);
        quantizeFloatArray(weights, w_quant, w_scale);

        FloatArray x_scale = new FloatArray(1);
        int maxNumGroups = (inputDim + local_workgroup_size - 1) / local_workgroup_size;
        FloatArray x_max = new FloatArray(maxNumGroups);
        FloatArray inv_scale = new FloatArray(1);
        Int8Array x_quant = new Int8Array(input.getSize());

        WorkerGrid1D workerQuant = new WorkerGrid1D(inputDim);
        WorkerGrid1D workerDp4a = new WorkerGrid1D(local_workgroup_size * inputDim);

        GridScheduler schedulerDp4a = new GridScheduler();
        schedulerDp4a.addWorkerGrid("s0_quant_kc.scales", workerQuant);
        schedulerDp4a.addWorkerGrid("s0_quant_kc.quantize", workerQuant);
        schedulerDp4a.addWorkerGrid("s0_quant_kc.dp4amatvec", workerDp4a);

        workerQuant.setLocalWork(local_workgroup_size, 1, 1);
        workerDp4a.setLocalWork(local_workgroup_size, 1, 1);

        TaskGraph taskGraph = new TaskGraph("s0_quant_kc")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, x_max, inv_scale, w_quant, w_scale)
                .task("scales", QuantizationTests::reductionCalculateMax, new KernelContext(), x_max, input, x_scale, inv_scale, local_workgroup_size, inputDim)
                .task("quantize", QuantizationTests::quantizeKernelContext,new KernelContext(), input, inv_scale, x_quant)
                .task("dp4amatvec", QuantizationTests::matrixVectorGenericDP4A4Way, new KernelContext(), w_quant, x_quant,
                        output, inputDim, outputDim, local_workgroup_size, w_scale, x_scale)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output, x_quant, x_scale, inv_scale, x_max);

        ImmutableTaskGraph immutabletaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan tornadoExecutor = new TornadoExecutionPlan(immutabletaskGraph)) {
            tornadoExecutor.withGridScheduler(schedulerDp4a).execute();
        }

        matrixVectorSequential(outputSeq, weights, input, inputDim, outputDim);

        for (int i = 0; i < output.getSize(); i++) {
            assertEquals(outputSeq.get(i), output.get(i), 0.1f);
        }

    }

    @Test
    public void testMatrixVectorVectorized() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.SPIRV);

        Random random = new Random(42);
        int localWorkgroupSize = 64;
        int blockSize = 32;
        int inputDim = 8192;   // Default input dimension (columns)
        int outputDim = 2048; // Default output dimension (rows)

        FloatArray input = new FloatArray(inputDim);
        FloatArray weights = new FloatArray(inputDim * outputDim);
        FloatArray output = new FloatArray(outputDim);
        FloatArray outputSeq = new FloatArray(outputDim);
        fillRandomData(input, -1.0f, 1.0f, random);
        fillRandomData(weights, -0.1f, 0.1f, random);

        // already quantized
        Int8Array w_quant = new Int8Array(weights.getSize());
        FloatArray w_scale = new FloatArray(1);
        quantizeFloatArray(weights, w_quant, w_scale);

        Int8Array weightsQuantized = new Int8Array(inputDim * outputDim);
        int weightBlocksPerRow = inputDim / blockSize;
        HalfFloatArray weightsScales = new HalfFloatArray(outputDim * weightBlocksPerRow);
        quantizeWeightsToQ8(weights, weightsQuantized, weightsScales, outputDim, inputDim, blockSize);

        WorkerGrid1D hybridWorker = new WorkerGrid1D(outputDim * localWorkgroupSize);
        hybridWorker.setLocalWork(localWorkgroupSize, 1, 1);
        GridScheduler scheduler = new GridScheduler("s0.t0", hybridWorker);
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, weightsQuantized, weightsScales)
                .task("t0", QuantizationTests::matrixVectorGenericVectorized, new KernelContext(), input, output, weightsQuantized, weightsScales, inputDim, outputDim, localWorkgroupSize)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        try (TornadoExecutionPlan tornadoExecutor = new TornadoExecutionPlan(immutableTaskGraph)) {
            tornadoExecutor.withGridScheduler(scheduler).execute();
        }

        matrixVectorSequential(outputSeq, weights, input, inputDim, outputDim);

        for (int i = 0; i < output.getSize(); i++) {
            assertEquals(outputSeq.get(i), output.get(i), 0.1f);
        }

    }

    // CHECKSTYLE:ON
}
