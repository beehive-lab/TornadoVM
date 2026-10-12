/*
 * Copyright (c) 2024, 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.compute;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Test to check the infinity values are properly replaced for code gen.
 *
 * <p>
 * How to run?
 * </p>
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.compute.MMwithBytes
 * </code>
 *
 */
public class MMwithBytes extends TornadoTestBase {

    @Test
    public void testMatrixMultiplicationWithBytes() throws TornadoExecutionPlanException {

        // Define matrix/vector dimensions
        final int dim = 128;  // example dimension, adjust as necessary
        final int numRows = 1024; // example number of rows for ByteArray
        final int blockSize = 32;
        final int bytesPerBlock = 2 + blockSize;
        final int blocksPerRow = dim / blockSize; // matmulTornado addresses blockIndex = (idx*dim + j) / blockSize,
                                                   // i.e. dim/blockSize blocks per row, not just one.

        // Initialize input data
        ByteArray byteArrayWeights = new ByteArray(numRows * blocksPerRow * bytesPerBlock);
        FloatArray inputVector = new FloatArray(dim);
        FloatArray outputVector = new FloatArray(numRows);

        // Populate the ByteArray with dummy quantized weights and scales
        for (int i = 0; i < numRows; i++) {
            for (int block = 0; block < blocksPerRow; block++) {
                int offset = (i * blocksPerRow + block) * bytesPerBlock;
                byteArrayWeights.set(offset, (byte) 0);          // scale byte 1
                byteArrayWeights.set(offset + 1, (byte) 0);      // scale byte 2
                for (int j = 2; j < bytesPerBlock; j++) {
                    byteArrayWeights.set(offset + j, (byte) ((i * 3f) * 255 - 128)); // random quantized values
                }
            }
        }

        // Populate the input vector with random floats
        for (int i = 0; i < dim; i++) {
            inputVector.set(i, (float) Math.random());
        }

        // Expected output should be initialized to zero
        outputVector.init(0.0f);

        // Create the execution plan
        WorkerGrid workerGrid = new WorkerGrid1D(numRows);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);
        workerGrid.setGlobalWork(numRows, 1, 1);
        workerGrid.setLocalWork(32, 1, 1);

        // Define the TaskGraph for matrix multiplication
        TaskGraph taskGraph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, byteArrayWeights, inputVector).task("t0", MMwithBytes::matmulTornado, new KernelContext(),
                byteArrayWeights, inputVector, outputVector, dim).transferToHost(DataTransferMode.EVERY_EXECUTION, outputVector);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        // Validation: Manually check output values or use precomputed expected output
        // For simplicity, check that no NaNs or Infinity are in the result
        for (int i = 0; i < numRows; i++) {
            float result = outputVector.get(i);
            assertFalse("Output contains NaN at index " + i, Float.isNaN(result));
            assertFalse("Output contains Infinity at index " + i, Float.isInfinite(result));
        }
    }

    public static void matmulTornado(KernelContext context, ByteArray thisx, FloatArray that, FloatArray out, int dim1) {
        final int blockSize = 32; // Assuming this is the block size used in quantization
        final int bytesPerBlock = 2 + blockSize; // 2 bytes for scale + block_size bytes for values

        int idx = context.globalIdx;

        float result = 0f;
        int thisOffset = idx * dim1;

        for (int j = 0; j < dim1; j++) {
            int index = thisOffset + j;
            // Calculate block position
            int blockIndex = index / blockSize;
            int withinBlockIndex = index % blockSize;
            int blockOffset = blockIndex * bytesPerBlock;

            // Read scale (float16) for this block
            int scaleByte1 = thisx.get(blockOffset) & 0xFF;
            int scaleByte2 = thisx.get(blockOffset + 1) & 0xFF;
            short scaleFloat16 = (short) ((scaleByte2 << 8) | scaleByte1);
            float scale = decodeFloat16(scaleFloat16);

            // Read quantized value
            byte quantized = thisx.get(blockOffset + 2 + withinBlockIndex);

            // Dequantize and multiply
            result += (quantized * scale) * that.get(j);
        }

        out.set(idx, result);

    }

    private static float decodeFloat16(short value) {
        int sign = (value & 0x8000) >>> 15;
        int exp = (value & 0x7C00) >>> 10;
        int frac = value & 0x03FF;

        // Handle special cases
        if (exp == 0x1F) {
            return sign == 0 ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        }
        if (exp == 0) {
            if (frac == 0) {
                return sign == 0 ? 0.0f : -0.0f;
            }
            float result = frac * pow2(-24);
            return sign == 0 ? result : -result;
        }

        float result = 1.0f + (frac / 1024.0f);
        result *= pow2(exp - 15);
        return sign == 0 ? result : -result;
    }

    private static float pow2(int n) {
        if (n >= 0) {
            if (n < 31) {
                return (float) (1 << n);
            }
            return Float.POSITIVE_INFINITY;
        }
        if (n > -150) {
            return 1.0f / (1 << -n);
        }
        return 0.0f;
    }

    public static void positiveInfinity(FloatArray positiveInfinity) {
        for (@Parallel int i = 0; i < positiveInfinity.getSize(); i++) {
            if (positiveInfinity.get(i) != Float.POSITIVE_INFINITY) {
                positiveInfinity.set(i, Float.NEGATIVE_INFINITY);
            }
        }
    }

    @Test
    public void testPositiveInfinity() throws TornadoExecutionPlanException {
        final int n = 1024;
        FloatArray positiveInfinityArray = new FloatArray(n);

        positiveInfinityArray.init(Float.POSITIVE_INFINITY);

        runInPlace(MMwithBytes::positiveInfinity, positiveInfinityArray);

        // Validate that all values remain Float.POSITIVE_INFINITY after TornadoVM processing
        assertAllEqual(Float.POSITIVE_INFINITY, positiveInfinityArray);
    }

    public static void negativeInfinity(FloatArray negativeInfinityArray) {
        for (@Parallel int i = 0; i < negativeInfinityArray.getSize(); i++) {
            if (negativeInfinityArray.get(i) != Float.NEGATIVE_INFINITY) {
                negativeInfinityArray.set(i, Float.POSITIVE_INFINITY);
            }
        }
    }

    @Test
    public void testNegativeInfinity() throws TornadoExecutionPlanException {
        final int n = 1024;
        FloatArray negativeInfinityArray = new FloatArray(n);

        negativeInfinityArray.init(Float.NEGATIVE_INFINITY);

        runInPlace(MMwithBytes::negativeInfinity, negativeInfinityArray);

        // Validate that all values remain Float.NEGATIVE_INFINITY after TornadoVM processing
        assertAllEqual(Float.NEGATIVE_INFINITY, negativeInfinityArray);
    }

    public static void negativeInfinityAssignment(FloatArray x) {
        for (@Parallel int i = 0; i < x.getSize(); i++) {
            x.set(i, Float.NEGATIVE_INFINITY);
        }
    }

    @Test
    public void testNegativeInfinityAssignment() throws TornadoExecutionPlanException {
        final int n = 1024;
        FloatArray negativeInfinityArray = new FloatArray(n);

        negativeInfinityArray.init(2f);

        runInPlace(MMwithBytes::negativeInfinityAssignment, negativeInfinityArray);

        // Validate that all values remain Float.NEGATIVE_INFINITY after TornadoVM processing
        assertAllEqual(Float.NEGATIVE_INFINITY, negativeInfinityArray);
    }

    /** Runs {@code kernel} over {@code array} on the device, copying it in once and back after each run. */
    private static void runInPlace(Task1<FloatArray> kernel, FloatArray array) throws TornadoExecutionPlanException {
        var taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, array) //
                .task("t0", kernel, array) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, array);
        try (var executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }
    }

    private static void assertAllEqual(float expected, FloatArray actual) {
        for (int i = 0; i < actual.getSize(); i++) {
            assertEquals("index " + i, expected, actual.get(i), 0.0f);
        }
    }
}
