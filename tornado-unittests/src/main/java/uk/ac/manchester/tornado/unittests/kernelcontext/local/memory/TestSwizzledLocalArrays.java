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
package uk.ac.manchester.tornado.unittests.kernelcontext.local.memory;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * How to run?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.local.memory.TestSwizzledLocalArrays
 * </code>
 * </p>
 */
public class TestSwizzledLocalArrays extends TornadoTestBase {

    public static void swizzleDoubleKernel(KernelContext context, HalfFloatArray in, HalfFloatArray out) {
        final int stride = 16; // 16 fp16 cols per row → 32-byte row stride

        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;

        int row = localIdx / stride;
        int col = localIdx % stride;

        HalfFloat[] tile = context.allocateHalfFloatLocalArray(256);

        // Store input into local memory via the swizzled accessor.
        context.swizzleStoreFp16Stride32(tile, row, col, stride, in.get(globalIdx));
        context.localBarrier();

        // Load back via the swizzled accessor, double, write to global output.
        HalfFloat v = context.swizzleLoadFp16Stride32(tile, row, col, stride);
        HalfFloat doubled = HalfFloat.add(v, v);
        out.set(globalIdx, doubled);
    }

    public static void swizzleDoubleKernelStride16(KernelContext context,
                                                   HalfFloatArray in, HalfFloatArray out) {
        final int stride = 8;  // 8 fp16 cols per row → 16-byte row stride

        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;

        int row = localIdx / stride;
        int col = localIdx % stride;

        HalfFloat[] tile = context.allocateHalfFloatLocalArray(128);

        context.swizzleStoreFp16Stride16(tile, row, col, stride, in.get(globalIdx));
        context.localBarrier();

        HalfFloat v = context.swizzleLoadFp16Stride16(tile, row, col, stride);
        HalfFloat doubled = HalfFloat.add(v, v);
        out.set(globalIdx, doubled);
    }

    public static void swizzleReverseKernelInt8(KernelContext context, ByteArray in, ByteArray out) {
        final int stride = 32;  // A-operand stride; B would use 16
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;

        int row = localIdx / stride;
        int col = localIdx % stride;

        byte[] tile = context.allocateByteLocalArray(512);

        context.swizzleStoreInt8(tile, row, col, stride, in.get(globalIdx));
        context.localBarrier();

        int readElem = 511 - localIdx;          // reverse within the 512-element tile
        int rRow = readElem / stride;
        int rCol = readElem % stride;
        byte v = context.swizzleLoadInt8(tile, rRow, rCol, stride);
        out.set(globalIdx, v);
    }

    public static void swizzleLoadConvertToFloatKernel(KernelContext context, HalfFloatArray in, FloatArray out) {
        final int stride = 16;

        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;

        int row = localIdx / stride;
        int col = localIdx % stride;

        HalfFloat[] tile = context.allocateHalfFloatLocalArray(256);

        context.swizzleStoreFp16Stride32(tile, row, col, stride, in.get(globalIdx));
        context.localBarrier();

        HalfFloat v = context.swizzleLoadFp16Stride32(tile, row, col, stride);
        // Convert the swizzle-loaded HalfFloat to float in-kernel.
        // Exercises the HalfFloat -> half-bits rewrite pipeline (TornadoHalfFloatReplacement)
        // applied to a value produced by SwizzledLoadFP16Stride32Node.
        float f = v.getFloat32();
        out.set(globalIdx, f * 2.0f);
    }

    public static void swizzleLoadConvertToFloatStride16Kernel(KernelContext context, HalfFloatArray in, FloatArray out) {
        final int stride = 8;
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int row = localIdx / stride;
        int col = localIdx % stride;

        HalfFloat[] tile = context.allocateHalfFloatLocalArray(128);
        context.swizzleStoreFp16Stride16(tile, row, col, stride, in.get(globalIdx));
        context.localBarrier();
        HalfFloat v = context.swizzleLoadFp16Stride16(tile, row, col, stride);
        float f = v.getFloat32();
        out.set(globalIdx, f * 2.0f);
    }

    public static void swizzleRoundTripKernelInt8Stride16(KernelContext context, ByteArray in, ByteArray out) {
        final int stride = 16;   // narrow tile: 16 int8 cols per row
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;

        int row = localIdx / stride;
        int col = localIdx % stride;

        byte[] tile = context.allocateByteLocalArray(256);   // 16 rows × 16 cols

        context.swizzleStoreInt8(tile, row, col, stride, in.get(globalIdx));
        context.localBarrier();

        // Cross-lane read within the tile (reverse order), same pattern as the
        // stride-32 test: proves store/load agree on the swizzled layout
        // across threads, with no byte arithmetic in the kernel.
        int readElem = 255 - localIdx;
        int rRow = readElem / stride;
        int rCol = readElem % stride;
        byte v = context.swizzleLoadInt8(tile, rRow, rCol, stride);
        out.set(globalIdx, v);
    }


    @Test
    public void testSwizzleLoadStoreFp16Stride32() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);
        // Tile geometry: 16 rows × 16 fp16 cols = 256 elements per work-group.
        // Stride 32 bytes = 16 fp16 cols, which matches FP16_STRIDE_32 policy.
        final int rowsPerTile = 16;
        final int colsPerTile = 16;
        final int tileElements = rowsPerTile * colsPerTile; // 256
        final int numTiles = 4;
        final int size = tileElements * numTiles; // 1024
        final int localSize = tileElements;       // one thread per element

        HalfFloatArray input = new HalfFloatArray(size);
        HalfFloatArray output = new HalfFloatArray(size);
        IntStream.range(0, size).forEach(i -> input.set(i, new HalfFloat((float) i)));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestSwizzledLocalArrays::swizzleDoubleKernel, context, input, output)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(input.get(i).getFloat32() * 2.0f, output.get(i).getFloat32(), 0.0f);
        }
    }

    @Test
    public void testSwizzleLoadStoreFp16Stride16() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);
        // Tile geometry: 16 rows × 8 fp16 cols = 128 elements per work-group.
        // Stride 16 bytes = 8 fp16 cols, which matches FP16_STRIDE_16 policy.
        final int rowsPerTile = 16;
        final int colsPerTile = 8;
        final int tileElements = rowsPerTile * colsPerTile; // 128
        final int numTiles = 8;
        final int size = tileElements * numTiles; // 1024
        final int localSize = tileElements;       // one thread per element

        HalfFloatArray input = new HalfFloatArray(size);
        HalfFloatArray output = new HalfFloatArray(size);
        IntStream.range(0, size).forEach(i -> input.set(i, new HalfFloat((float) i)));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestSwizzledLocalArrays::swizzleDoubleKernelStride16, context, input, output)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(input.get(i).getFloat32() * 2.0f, output.get(i).getFloat32(), 0.0f);
        }
    }

    @Test
    public void testSwizzleLoadStoreInt8() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);
        final int rowsPerTile = 16;
        final int colsPerTile = 32;          // A-operand layout: 32 int8 = 32 bytes/row
        final int tileElements = rowsPerTile * colsPerTile; // 512
        final int numTiles = 2;
        final int size = tileElements * numTiles; // 1024
        final int localSize = tileElements;

        ByteArray input = new ByteArray(size);
        ByteArray output = new ByteArray(size);
        IntStream.range(0, size).forEach(i -> input.set(i, (byte) (i % 60)));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestSwizzledLocalArrays::swizzleReverseKernelInt8, context, input, output)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        for (int t = 0; t < numTiles; t++) {
            for (int e = 0; e < tileElements; e++) {
                int globalIdx = t * tileElements + e;
                int srcGlobalIdx = t * tileElements + (tileElements - 1 - e);
                assertEquals(input.get(srcGlobalIdx), output.get(globalIdx));
            }
        }
    }

    @Test
    public void testSwizzleLoadConvertToFloat() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        final int rowsPerTile = 16;
        final int colsPerTile = 16;
        final int tileElements = rowsPerTile * colsPerTile;  // 256
        final int numTiles = 4;
        final int size = tileElements * numTiles;            // 1024
        final int localSize = tileElements;

        HalfFloatArray input = new HalfFloatArray(size);
        FloatArray output = new FloatArray(size);
        IntStream.range(0, size).forEach(i -> input.set(i, new HalfFloat((float) i)));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestSwizzledLocalArrays::swizzleLoadConvertToFloatKernel, context, input, output)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        for (int i = 0; i < size; i++) {
            // Allow modest fp16->fp32 conversion tolerance for larger values.
            assertEquals(input.get(i).getFloat32() * 2.0f, output.get(i), 1e-2f);
        }
    }

    @Test
    public void testSwizzleLoadConvertToFloatStride16() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        final int rowsPerTile = 16;
        final int colsPerTile = 8;                           // narrow tile: 8 fp16 cols
        final int tileElements = rowsPerTile * colsPerTile;  // 128
        final int numTiles = 8;
        final int size = tileElements * numTiles;            // 1024
        final int localSize = tileElements;

        HalfFloatArray input = new HalfFloatArray(size);
        FloatArray output = new FloatArray(size);
        IntStream.range(0, size).forEach(i -> input.set(i, new HalfFloat((float) i)));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestSwizzledLocalArrays::swizzleLoadConvertToFloatStride16Kernel, context, input, output)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        for (int i = 0; i < size; i++) {
            // Allow modest fp16->fp32 conversion tolerance.
            assertEquals(input.get(i).getFloat32() * 2.0f, output.get(i), 1e-2f);
        }
    }

    @Test
    public void testSwizzleLoadStoreInt8Stride16() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        final int rowsPerTile = 16;
        final int colsPerTile = 16;                          // narrow int8 tile
        final int tileElements = rowsPerTile * colsPerTile;  // 256
        final int numTiles = 4;
        final int size = tileElements * numTiles;            // 1024
        final int localSize = tileElements;

        ByteArray input = new ByteArray(size);
        ByteArray output = new ByteArray(size);
        IntStream.range(0, size).forEach(i -> input.set(i, (byte) (i % 60)));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestSwizzledLocalArrays::swizzleRoundTripKernelInt8Stride16, context, input, output)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        // Cross-lane: output[i] should equal input[srcGlobalIdx], where srcGlobalIdx
        // is the reverse position within the same tile.
        for (int t = 0; t < numTiles; t++) {
            for (int e = 0; e < tileElements; e++) {
                int globalIdx = t * tileElements + e;
                int srcGlobalIdx = t * tileElements + (tileElements - 1 - e);
                assertEquals("Mismatch at globalIdx=" + globalIdx,
                        input.get(srcGlobalIdx), output.get(globalIdx));
            }
        }
    }

}
