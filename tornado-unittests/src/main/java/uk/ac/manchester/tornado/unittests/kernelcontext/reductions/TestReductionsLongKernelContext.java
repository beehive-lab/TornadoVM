/*
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
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

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

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
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The unit-tests in this class implement some Reduction operations (add, max, min) for {@link Long} type. These unit-tests check the functional operation of some {@link KernelContext} features, such
 * as global thread identifiers, local thread identifiers, the local group size of the associated WorkerGrid, barriers and allocation of local memory.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestReductionsLongKernelContext
 * </code>
 */
public class TestReductionsLongKernelContext extends TornadoTestBase {

    public static long computeAddSequential(LongArray input) {
        long acc = 0;
        for (int i = 0; i < input.getSize(); i++) {
            acc += input.get(i);
        }
        return acc;
    }

    public static void longReductionAddGlobalMemory(KernelContext context, LongArray a, LongArray b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID
        int id = localGroupSize * groupID + localIdx;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                a.set(id, a.get(id) + a.get(id + stride));
            }
        }
        if (localIdx == 0) {
            b.set(groupID, a.get(id));
        }
    }

    public static void longReductionAddLocalMemory(KernelContext context, LongArray a, LongArray b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        long[] localA = context.allocateLongLocalArray(256);
        localA[localIdx] = a.get(globalIdx);
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] += localA[localIdx + stride];
            }
        }
        if (localIdx == 0) {
            b.set(groupID, localA[0]);
        }
    }

    public static void longReductionAddLocalMemory(KernelContext context, LongArray a, LongArray b, int blockDim) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        long[] localA = context.allocateLongLocalArray(blockDim);
        localA[localIdx] = a.get(globalIdx);
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] += localA[localIdx + stride];
            }
        }
        if (localIdx == 0) {
            b.set(groupID, localA[0]);
        }
    }

    public static long computeMaxSequential(LongArray input) {
        long acc = 0;
        for (int i = 0; i < input.getSize(); i++) {
            acc = TornadoMath.max(acc, input.get(i));
        }
        return acc;
    }

    private static void longReductionMaxGlobalMemory(KernelContext context, LongArray a, LongArray b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID
        int id = localGroupSize * groupID + localIdx;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                a.set(id, TornadoMath.max(a.get(id), a.get(id + stride)));
            }
        }
        if (localIdx == 0) {
            b.set(groupID, a.get(id));
        }
    }

    public static void longReductionMaxLocalMemory(KernelContext context, LongArray a, LongArray b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        long[] localA = context.allocateLongLocalArray(256);
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

    public static long computeMinSequential(LongArray input) {
        long acc = 0;
        for (int i = 0; i < input.getSize(); i++) {
            acc = TornadoMath.min(acc, input.get(i));
        }
        return acc;
    }

    private static void longReductionMinGlobalMemory(KernelContext context, LongArray a, LongArray b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID
        int id = localGroupSize * groupID + localIdx;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                a.set(id, TornadoMath.min(a.get(id), a.get(id + stride)));
            }
        }
        if (localIdx == 0) {
            b.set(groupID, a.get(id));
        }
    }

    public static void longReductionMinLocalMemory(KernelContext context, LongArray a, LongArray b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        long[] localA = context.allocateLongLocalArray(256);
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
    public void testLongReductionsAddGlobalMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        LongArray input = new LongArray(size);
        LongArray reduce = new LongArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, i));
        long sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("taskGraph.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsLongKernelContext::longReductionAddGlobalMemory, context, input, reduce) //
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
        long finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum += reduce.get(i);
        }

        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testLongReductionsAddLocalMemory01() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        LongArray input = new LongArray(size);
        LongArray reduce = new LongArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, i));
        long sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.addWorkerGrid("taskGraph.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsLongKernelContext::longReductionAddLocalMemory, context, input, reduce) //
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
        long finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum += reduce.get(i);
        }

        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testLongReductionsAddLocalMemory02() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        LongArray input = new LongArray(size);
        LongArray reduce = new LongArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, i));
        long sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.addWorkerGrid("taskGraph.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsLongKernelContext::longReductionAddLocalMemory, context, input, reduce, localSize) //
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
        long finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum += reduce.get(i);
        }

        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testLongReductionsMaxGlobalMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        LongArray input = new LongArray(size);
        LongArray reduce = new LongArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, i));
        long sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("taskGraph.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsLongKernelContext::longReductionMaxGlobalMemory, context, input, reduce) //
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
        long finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = TornadoMath.max(finalSum, reduce.get(i));
        }

        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testLongReductionsMaxLocalMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        LongArray input = new LongArray(size);
        LongArray reduce = new LongArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, i));
        long sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("taskGraph.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsLongKernelContext::longReductionMaxLocalMemory, context, input, reduce) //
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
        long finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = TornadoMath.max(finalSum, reduce.get(i));
        }

        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testLongReductionsMinGlobalMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        LongArray input = new LongArray(size);
        LongArray reduce = new LongArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, i));
        long sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("taskGraph.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsLongKernelContext::longReductionMinGlobalMemory, context, input, reduce) //
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
        long finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = TornadoMath.min(finalSum, reduce.get(i));
        }

        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testLongReductionsMinLocalMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        LongArray input = new LongArray(size);
        LongArray reduce = new LongArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, i));
        long sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("taskGraph.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("taskGraph") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsLongKernelContext::longReductionMinLocalMemory, context, input, reduce) //
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
        long finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = TornadoMath.min(finalSum, reduce.get(i));
        }

        assertEquals(sequential, finalSum, 0);
    }
}
