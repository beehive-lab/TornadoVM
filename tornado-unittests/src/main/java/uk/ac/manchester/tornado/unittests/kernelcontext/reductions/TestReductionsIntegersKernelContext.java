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

import java.util.Random;
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
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The unit-tests in this class implement some Reduction operations (add, max, min) for {@link Integer} type. These unit-tests check the functional operation of some {@link KernelContext} features,
 * such as global thread identifiers, local thread identifiers, the local group size of the associated WorkerGrid, barriers and allocation of local memory.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestReductionsIntegersKernelContext
 * </code>
 */
public class TestReductionsIntegersKernelContext extends TornadoTestBase {

    public static int computeAddSequential(IntArray input) {
        int acc = 0;
        for (int i = 0; i < input.getSize(); i++) {
            acc += input.get(i);
        }
        return acc;
    }

    public static void intReductionAddGlobalMemory(KernelContext context, IntArray a, IntArray b) {
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
            b.set(groupID, a.get(localIdx));
        }
    }

    public static void basicAccessThreadIds(KernelContext context, IntArray a) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;
        int idx = localGroupSize * groupID + localIdx;
        a.set(idx, idx);
    }

    // Copy from Global to Local and Global memory again
    public static void basicAccessThreadIds02(KernelContext context, IntArray a, IntArray b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;
        int idx = localGroupSize * groupID + localIdx;

        int[] localA = context.allocateIntLocalArray(256);
        localA[localIdx] = a.get(idx);
        b.set(idx, localA[localIdx]);
    }

    // Copy from Global to Local and Global memory again
    public static void basicAccessThreadIds03(KernelContext context, IntArray a, IntArray b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;
        int idx = localGroupSize * groupID + localIdx;

        int[] localA = context.allocateIntLocalArray(256);
        localA[localIdx] = a.get(idx);
        localA[localIdx] *= 2;
        b.set(idx, localA[localIdx]);
    }

    // Copy from Global to Local and Global memory again
    public static void basicAccessThreadIds04(KernelContext context, IntArray a, IntArray b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;
        int idx = localGroupSize * groupID + localIdx;

        int[] localA = context.allocateIntLocalArray(1024);
        for (int i = 0; i < 256; i++) {
            localA[i] = 2;
        }

        b.set(idx, localA[localIdx]);
    }

    // Copy from Global to Local and Global memory again
    public static void basicAccessThreadIds05(KernelContext context, IntArray a, IntArray b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;
        int idx = localGroupSize * groupID + localIdx;

        int[] localA = context.allocateIntLocalArray(256);
        for (int i = 0; i < localGroupSize; i++) {
            localA[i] = 2;
        }

        b.set(idx, localA[localIdx]);
    }

    public static void intReductionAddLocalMemory(KernelContext context, IntArray a, IntArray b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        int[] localA = context.allocateIntLocalArray(256);
        localA[localIdx] = a.get(globalIdx);
        context.localBarrier();

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            if (localIdx < stride) {
                localA[localIdx] += localA[localIdx + stride];
            }
            context.localBarrier();
        }
        if (localIdx == 0) {
            b.set(groupID, localA[localIdx]);
        }
    }

    public static void intReductionAddLocalMemory(KernelContext context, IntArray a, IntArray b, int blockDim) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        int[] localA = context.allocateIntLocalArray(blockDim);
        localA[localIdx] = a.get(globalIdx);
        context.localBarrier();

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            if (localIdx < stride) {
                localA[localIdx] += localA[localIdx + stride];
            }
            context.localBarrier();
        }
        if (localIdx == 0) {
            b.set(groupID, localA[localIdx]);
        }
    }

    public static int computeMaxSequential(IntArray input) {
        int acc = 0;
        for (int i = 0; i < input.getSize(); i++) {
            acc = TornadoMath.max(acc, input.get(i));
        }
        return acc;
    }

    private static void intReductionMaxGlobalMemory(KernelContext context, IntArray a, IntArray b) {
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

    public static void intReductionMaxLocalMemory(KernelContext context, IntArray a, IntArray b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        int[] localA = context.allocateIntLocalArray(256);
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

    public static int computeMinSequential(IntArray input) {
        int acc = 0;
        for (int i = 0; i < input.getSize(); i++) {
            acc = TornadoMath.min(acc, input.get(i));
        }
        return acc;
    }

    private static void intReductionMinGlobalMemory(KernelContext context, IntArray a, IntArray b) {
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

    public static void intReductionMinLocalMemory(KernelContext context, IntArray a, IntArray b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        int[] localA = context.allocateIntLocalArray(256);
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
    public void basic() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        IntArray input = new IntArray(size);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::basicAccessThreadIds, context, input) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, input);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(i, input.get(i));
        }
    }

    @Test
    public void basic02() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        IntArray input = new IntArray(size);
        IntArray output = new IntArray(size);

        IntStream.range(0, size).forEach(x -> input.set(x, x));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::basicAccessThreadIds02, context, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(input.get(i), output.get(i));
        }
    }

    @Test
    public void basic03() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        IntArray input = new IntArray(size);
        IntArray output = new IntArray(size);

        IntStream.range(0, size).forEach(x -> input.set(x, x));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::basicAccessThreadIds03, context, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(input.get(i) * 2, output.get(i));
        }
    }

    @Test
    public void basic04() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        IntArray input = new IntArray(size);
        IntArray output = new IntArray(size);

        IntStream.range(0, size).forEach(x -> input.set(x, x));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::basicAccessThreadIds04, context, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(2, output.get(i), 0.0);
        }
    }

    @Test
    public void basic05() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        IntArray input = new IntArray(size);
        IntArray output = new IntArray(size);

        IntStream.range(0, size).forEach(x -> input.set(x, x));

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::basicAccessThreadIds05, context, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(2, output.get(i), 0.0);
        }
    }

    @Test
    public void testIntReductionsAddGlobalMemory() throws TornadoExecutionPlanException {
        final int size = 32;
        final int localSize = 1;
        IntArray input = new IntArray(size);
        IntArray reduce = new IntArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, 2));
        int sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegersKernelContext::intReductionAddGlobalMemory, context, input, reduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce, input);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler) //
                    .execute();
        }

        // Final SUM
        int finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum += reduce.get(i);
        }

        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testIntReductionsAddLocalMemory01() throws TornadoExecutionPlanException {
        final int size = 256;
        final int localSize = 256;
        IntArray input = new IntArray(size);
        IntArray reduce = new IntArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, i));
        int sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestReductionsIntegersKernelContext::intReductionAddLocalMemory, context, input, reduce) //
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
        int finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum += reduce.get(i);
        }

        assertEquals(sequential, finalSum);
    }

    @Test
    public void testIntReductionsAddLocalMemory02() throws TornadoExecutionPlanException {
        final int size = 256;
        final int localSize = 256;
        IntArray input = new IntArray(size);
        IntArray reduce = new IntArray(size / localSize);
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, i));
        int sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, input) //
                .task("t0", TestReductionsIntegersKernelContext::intReductionAddLocalMemory, context, input, reduce, localSize) //
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
        int finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum += reduce.get(i);
        }

        assertEquals(sequential, finalSum);
    }

    @Test
    public void testIntReductionsMaxGlobalMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        IntArray input = new IntArray(size);
        IntArray reduce = new IntArray(size / localSize);
        Random r = new Random();
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, r.nextInt()));
        int sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::intReductionMaxGlobalMemory, context, input, reduce) //
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
        int finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = TornadoMath.max(finalSum, reduce.get(i));
        }

        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testIntReductionsMaxLocalMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        IntArray input = new IntArray(size);
        IntArray reduce = new IntArray(size / localSize);
        Random r = new Random();
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, r.nextInt()));
        int sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::intReductionMaxLocalMemory, context, input, reduce) //
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
        int finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = TornadoMath.max(finalSum, reduce.get(i));
        }

        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testIntReductionsMinGlobalMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        IntArray input = new IntArray(size);
        IntArray reduce = new IntArray(size / localSize);
        Random r = new Random();
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, r.nextInt(10000000)));
        int sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::intReductionMinGlobalMemory, context, input, reduce) //
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
        int finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = TornadoMath.min(finalSum, reduce.get(i));
        }

        assertEquals(sequential, finalSum, 0);
    }

    @Test
    public void testIntReductionsMinLocalMemory() throws TornadoExecutionPlanException {
        final int size = 1024;
        final int localSize = 256;
        IntArray input = new IntArray(size);
        IntArray reduce = new IntArray(size / localSize);
        Random r = new Random();
        IntStream.range(0, input.getSize()).sequential().forEach(i -> input.set(i, r.nextInt(100000)));
        int sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestReductionsIntegersKernelContext::intReductionMinLocalMemory, context, input, reduce) //
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
        int finalSum = 0;
        for (int i = 0; i < reduce.getSize(); i++) {
            finalSum = TornadoMath.min(finalSum, reduce.get(i));
        }

        assertEquals(sequential, finalSum, 0);
    }
}
