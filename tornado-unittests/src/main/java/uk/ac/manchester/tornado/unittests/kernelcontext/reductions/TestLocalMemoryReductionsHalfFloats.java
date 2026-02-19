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
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestLocalMemoryReductionsHalfFloats
 * </code>
 * </p>
 */
public class TestLocalMemoryReductionsHalfFloats extends TornadoTestBase {

    public static HalfFloat computeAddSequential(HalfFloatArray input) {
        HalfFloat acc = new HalfFloat(0);
        for (int i = 0; i < input.getSize(); i++) {
            acc = HalfFloat.add(acc, input.get(i));
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
    private static void halfFloatReductionAddLocalMemory(KernelContext context, HalfFloatArray a, HalfFloatArray b) {

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
        HalfFloat[] localA = context.allocateHalfFloatLocalArray(256);

        // Copy data from global memory to local memory.
        localA[localIdx] = a.get(globalIdx);

        // Compute the reduction in local memory
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] = HalfFloat.add(localA[localIdx], localA[localIdx + stride]);
            }
        }
//
//        // Copy result of the full reduction within the work-group into global memory.
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
    private static void halfFloatReductionAddLocalMemory(KernelContext context, HalfFloatArray a, HalfFloatArray b, int blockDim) {

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
        HalfFloat[] localA = context.allocateHalfFloatLocalArray(blockDim);

        // Copy data from global memory to local memory.
        localA[localIdx] = a.get(globalIdx);

        // Compute the reduction in local memory
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] = HalfFloat.add(localA[localIdx], localA[localIdx + stride]);
            }
        }

        // Copy result of the full reduction within the work-group into global memory.
        if (localIdx == 0) {
            b.set(groupID, localA[0]);
        }
    }

    public static HalfFloat computeMaxSequential(HalfFloatArray input) {
        HalfFloat acc = new HalfFloat(0);
        for (int i = 0; i < input.getSize(); i++) {
            acc = TornadoMath.max(acc, input.get(i));
        }
        return acc;
    }

    public static void halfFloatReductionMaxLocalMemory(KernelContext context, HalfFloatArray a, HalfFloatArray b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        HalfFloat[] localA = context.allocateHalfFloatLocalArray(256);
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

    public static HalfFloat computeMinSequential(HalfFloatArray input) {
        HalfFloat acc = new HalfFloat(0);
        for (int i = 0; i < input.getSize(); i++) {
            acc = TornadoMath.min(acc, input.get(i));
        }
        return acc;
    }

    public static void halfFloatReductionMinLocalMemory(KernelContext context, HalfFloatArray a, HalfFloatArray b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        HalfFloat[] localA = context.allocateHalfFloatLocalArray(256);
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
    public void testHalfFloatReductionsAddLocalMemory01() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        HalfFloatArray input = new HalfFloatArray(size);
        HalfFloatArray reduce = new HalfFloatArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, new HalfFloat(i)));
        HalfFloat sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestLocalMemoryReductionsHalfFloats::halfFloatReductionAddLocalMemory, context, input, reduce) //
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
        HalfFloat finalSum = new HalfFloat(0);
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = HalfFloat.add(finalSum, reduce.get(i));
        }

        assertEquals(sequential.getFloat32(), finalSum.getFloat32(), 0);
    }

    @Test
    public void testHalfFloatReductionsAddLocalMemory02() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        HalfFloatArray input = new HalfFloatArray(size);
        HalfFloatArray reduce = new HalfFloatArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, new HalfFloat(i)));
        HalfFloat sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestLocalMemoryReductionsHalfFloats::halfFloatReductionAddLocalMemory, context, input, reduce, localSize) //
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
        HalfFloat finalSum = new HalfFloat(0);
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = HalfFloat.add(finalSum, reduce.get(i));
        }

        assertEquals(sequential.getFloat32(), finalSum.getFloat32(), 0);
    }

    @Test
    public void testHalfFloatReductionsMaxLocalMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        HalfFloatArray input = new HalfFloatArray(size);
        HalfFloatArray reduce = new HalfFloatArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, new HalfFloat(i)));
        HalfFloat sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestLocalMemoryReductionsHalfFloats::halfFloatReductionMaxLocalMemory, context, input, reduce) //
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
        HalfFloat finalSum = new HalfFloat(0);
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = TornadoMath.max(finalSum, reduce.get(i));
        }

        assertEquals(sequential.getFloat32(), finalSum.getFloat32(), 0);
    }

    @Test
    public void testHalfFloatReductionsMinLocalMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        HalfFloatArray input = new HalfFloatArray(size);
        HalfFloatArray reduce = new HalfFloatArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, new HalfFloat(i)));
        HalfFloat sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestLocalMemoryReductionsHalfFloats::halfFloatReductionMinLocalMemory, context, input, reduce) //
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
        HalfFloat finalSum = new HalfFloat(0);
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = TornadoMath.min(finalSum, reduce.get(i));
        }

        assertEquals(sequential.getFloat32(), finalSum.getFloat32(), 0);
    }
    
}
