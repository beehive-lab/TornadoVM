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
package uk.ac.manchester.tornado.unittests.kernelcontext.reductions;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;


/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestLocalMemoryReductionsBytes
 * </code>
 * </p>
 */
public class TestLocalMemoryReductionsBytes extends TornadoTestBase {

    public static byte computeAddSequential(ByteArray input) {
        byte acc = 0;
        for (int i = 0; i < input.getSize(); i++) {
            acc += input.get(i);
        }
        return acc;
    }

    /**
     * Parallel reduction in TornadoVM using Local Memory.
     *
     * @param context
     *     {@link KernelContext}
     * @param a
     *     input array
     * @param b
     *     output array
     */
    private static void byteReductionAddLocalMemory(KernelContext context, ByteArray a, ByteArray b) {

        // Access to the global thread-id
        int globalIdx = context.globalIdx;

        // Access to the local thread-id (id within the work-group)
        int localIdx = context.localIdx;

        // Obtain the number of threads per work-group
        int localGroupSize = context.localGroupSizeX;

        // Obtain the group-ID
        int groupID = context.groupIdx;

        // Allocate an array in local memory (using the OpenCL terminology), or shared
        // memory with NVIDIA PTX.
        byte[] localA = context.allocateByteLocalArray(256);

        // Copy data from global memory to local memory.
        localA[localIdx] = a.get(globalIdx);

        // Compute the reduction in local memory
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] += localA[localIdx + stride];
            }
        }

        // Copy result of the full reduction within the work-group into global memory.
        if (localIdx == 0) {
            b.set(groupID, localA[0]);
        }
    }

    /**
     * Parallel reduction in TornadoVM using Local Memory.
     *
     * @param context
     *     {@link KernelContext}
     * @param a
     *     input array
     * @param b
     *     output array
     */
    private static void byteReductionAddLocalMemory(KernelContext context, ByteArray a, ByteArray b, int blockDim) {

        // Access to the global thread-id
        int globalIdx = context.globalIdx;

        // Access to the local thread-id (id within the work-group)
        int localIdx = context.localIdx;

        // Obtain the number of threads per work-group
        int localGroupSize = context.localGroupSizeX;

        // Obtain the group-ID
        int groupID = context.groupIdx;

        // Allocate an array in local memory (using the OpenCL terminology), or shared
        // memory with NVIDIA PTX.
        byte[] localA = context.allocateByteLocalArray(blockDim);

        // Copy data from global memory to local memory.
        localA[localIdx] = a.get(globalIdx);

        // Compute the reduction in local memory
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] += localA[localIdx + stride];
            }
        }

        // Copy result of the full reduction within the work-group into global memory.
        if (localIdx == 0) {
            b.set(groupID, localA[0]);
        }
    }

    public static byte computeMaxSequential(ByteArray input) {
        byte acc = 0;
        for (int i = 0; i < input.getSize(); i++) {
            acc = TornadoMath.max(acc, input.get(i));
        }
        return acc;
    }

    public static void byteReductionMaxLocalMemory(KernelContext context, ByteArray a, ByteArray b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        byte[] localA = context.allocateByteLocalArray(256);
        localA[localIdx] = a.get(globalIdx);
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] = TornadoMath.max(localA[localIdx], localA[localIdx + stride]);
            }
        }
        if (localIdx == 0) {
            b.set(groupID, localA[0]);
        }
    }

    public static byte computeMinSequential(ByteArray input) {
        byte acc = 0;
        for (int i = 0; i < input.getSize(); i++) {
            acc = TornadoMath.min(acc, input.get(i));
        }
        return acc;
    }

    public static void byteReductionMinLocalMemory(KernelContext context, ByteArray a, ByteArray b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        byte[] localA = context.allocateByteLocalArray(256);
        localA[localIdx] = a.get(globalIdx);
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] = TornadoMath.min(localA[localIdx], localA[localIdx + stride]);
            }
        }
        if (localIdx == 0) {
            b.set(groupID, localA[0]);
        }
    }

    @Test
    public void testByteReductionsAddLocalMemory01() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        ByteArray input = new ByteArray(size);
        ByteArray reduce = new ByteArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, (byte) i));
        byte sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestLocalMemoryReductionsBytes::byteReductionAddLocalMemory, context, input, reduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        // Final SUM
        byte finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum += reduce.get(i);
        }

        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testByteReductionsAddLocalMemory02() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        ByteArray input = new ByteArray(size);
        ByteArray reduce = new ByteArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, (byte) i));
        byte sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestLocalMemoryReductionsBytes::byteReductionAddLocalMemory, context, input, reduce, localSize) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        // Final SUM
        byte finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum += reduce.get(i);
        }

        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testByteReductionsMaxLocalMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        ByteArray input = new ByteArray(size);
        ByteArray reduce = new ByteArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, (byte) i));
        byte sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestLocalMemoryReductionsBytes::byteReductionMaxLocalMemory, context, input, reduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        // Final SUM
        byte finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = TornadoMath.max(finalSum, reduce.get(i));
        }

        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testByteReductionsMinLocalMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        ByteArray input = new ByteArray(size);
        ByteArray reduce = new ByteArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, (byte) i));
        byte sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestLocalMemoryReductionsBytes::byteReductionMinLocalMemory, context, input, reduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        // Final SUM
        byte finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = TornadoMath.min(finalSum, reduce.get(i));
        }

        assertEquals(sequential, finalSum, 0);
    }

}
