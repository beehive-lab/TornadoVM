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
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
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

    private static final int TILE_SIZE = 256;

    public static void performDP4A(Int8Array a, Int8Array b, IntArray result) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            int dot = QuantizationUtils.dp4a(a, i * 4, b, i * 4,0);
            result.set(i, dot);
        }
    }

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

    public static void matrixVectorGenericDequantize(KernelContext context, Int8Array w_quant, Int8Array x_quant, FloatArray output, int n, int d, int localWorkGroupSize, FloatArray w_scale, FloatArray x_scale) {
        int rowId = context.groupIdx;

        if (rowId >= d) {
            return;
        }

        // 1. Each workgroup computes the integer dot product for one row.
        int intSum = matrixVectorSimpleDP4A(context, localWorkGroupSize, w_quant, x_quant, n); //matrixVectorOptimizedDP4A(context, localWorkGroupSize, w_quant, x_quant, n); //matrixVectorTiledDP4A(context, localWorkGroupSize, w_quant, x_quant, n);

        // 2. Only thread 0 performs the final dequantization and write.
        // This is efficient because the expensive part is done in parallel.
        if (context.localIdx == 0) {
            // Fused dequantization and scaling
            float finalValue = (float) intSum * w_scale.get(0) * x_scale.get(0);
            output.set(rowId, finalValue);
        }
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

    /**
     * Tiled helper method to compute the dot product for a single row using DP4A and local memory.
     * This kernel returns the final integer sum for the row.
     */
    public static int matrixVectorTiledDP4A(KernelContext context, int localSize, Int8Array w_quant, Int8Array x_quant, int n) {
        int rowId = context.groupIdx;
        int localId = context.localIdx;

        // Local memory to hold a tile of the x_quant vector
        byte[] x_tile = context.allocateByteLocalArray(TILE_SIZE);

        // Local memory for the parallel reduction
        int[] localSum = context.allocateIntLocalArray(localSize);

        int rowOffset = rowId * n;
        int totalSum = 0;

        // Loop over the input vectors in tiles
        for (int tileStart = 0; tileStart < n; tileStart += TILE_SIZE) {

            // 1. Cooperatively load a tile of x_quant from global to local memory
            // Each thread loads multiple elements to fill the tile.
            for (int i = localId; i < TILE_SIZE && (tileStart + i) < n; i += localSize) {
                x_tile[i] = (byte) x_quant.get(tileStart + i);
            }

            // Synchronize to ensure the entire x_tile is loaded before proceeding
            context.localBarrier();

            // 2. Compute partial dot product using data from the local memory tile
            // Each thread computes its share of the work for this tile.
            int partialSum = 0;
            // The loop bound is now TILE_SIZE, not n.
            for (int j = localId * 4; j < TILE_SIZE; j += localSize * 4) {
                if (tileStart + j < n) { // Boundary check for the last tile
                    partialSum = QuantizationUtils.dp4a(w_quant, rowOffset + tileStart + j, x_tile, j, partialSum);
                }
            }
            totalSum += partialSum;

            // Synchronize to ensure all threads are done with the current tile before loading the next one
            context.localBarrier();
        }

        // Store each thread's total sum in local memory for reduction
        localSum[localId] = totalSum;
        context.localBarrier();

        // 3. Perform parallel reduction on the final sums
        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            if (localId < stride) {
                localSum[localId] += localSum[localId + stride];
            }
            context.localBarrier();
        }

        // The final integer result for the row is in localSum[0]
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

    @Test
    public void testDP4A() throws TornadoExecutionPlanException {
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

        TaskGraph taskGraphQ = new TaskGraph("s0_quant")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, x_max, inv_scale)
                .task("scales", QuantizationTests::reductionCalculateMax, new KernelContext(), x_max, input, x_scale, inv_scale, local_workgroup_size)
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
        Random random = new Random(42);
        int local_workgroup_size = 64;
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
        FloatArray x_max = new FloatArray(1);
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


        TaskGraph taskGraph2 = new TaskGraph("s0_quant_kc")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, x_max, inv_scale, w_quant, w_scale)
                .task("scales", QuantizationTests::reductionCalculateMax, new KernelContext(), x_max, input, x_scale, inv_scale, local_workgroup_size)
                .task("quantize", QuantizationTests::quantizeKernelContext,new KernelContext(), input, inv_scale, x_quant)
                .task("dp4amatvec", QuantizationTests::matrixVectorGenericDP4A, new KernelContext(), w_quant, x_quant,
                        output, inputDim, outputDim, local_workgroup_size, w_scale, x_scale)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph2 = taskGraph2.snapshot();
        try (TornadoExecutionPlan tornadoExecutor = new TornadoExecutionPlan(immutableTaskGraph2)) {
            tornadoExecutor.withGridScheduler(schedulerDp4a).execute();
        }

        matrixVectorSequential(outputSeq, weights, input, inputDim, outputDim);

        for (int i = 0; i < output.getSize(); i++) {
            assertEquals(outputSeq.get(i), output.get(i), 0.1f);
        }

    }

    @Test
    public void testMatrixVectorDP4AKernelContextTiled() throws TornadoExecutionPlanException {
        Random random = new Random(42);
        int local_workgroup_size = 64;
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
        FloatArray x_max = new FloatArray(1);
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


        TaskGraph taskGraph2 = new TaskGraph("s0_quant_kc")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, x_quant, x_scale, x_max, inv_scale, w_quant, w_scale)
                .task("scales", QuantizationTests::reductionCalculateMax, new KernelContext(), x_max, input, x_scale, inv_scale, local_workgroup_size)
                .task("quantize", QuantizationTests::quantizeKernelContext,new KernelContext(), input, inv_scale, x_quant)
                .task("dp4amatvec", QuantizationTests::matrixVectorGenericDequantize, new KernelContext(), w_quant, x_quant,
                        output, inputDim, outputDim, local_workgroup_size, w_scale, x_scale)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph2 = taskGraph2.snapshot();
        try (TornadoExecutionPlan tornadoExecutor = new TornadoExecutionPlan(immutableTaskGraph2)) {
            tornadoExecutor.withGridScheduler(schedulerDp4a).execute();
        }

        matrixVectorSequential(outputSeq, weights, input, inputDim, outputDim);

        for (int i = 0; i < output.getSize(); i++) {
            //System.out.println("Expected: " + outputSeq.get(i) + " actual: " + output.get(i));
           assertEquals(outputSeq.get(i), output.get(i), 0.1f);
        }

    }

    // CHECKSTYLE:ON
}
