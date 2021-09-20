/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The unit-tests in this class implement some Reduction operations (add, max,
 * min) for {@link Integer} type. These unit-tests check the functional
 * operation of some {@link KernelContext} features, such as global thread
 * identifiers, local thread identifiers, the local group size of the associated
 * WorkerGrid, barriers and allocation of local memory.
 */
public class TestReductionsIntegersKernelContext extends TornadoTestBase {

    public static int computeAddSequential(int[] input) {
        int acc = 0;
        for (int v : input) {
            acc += v;
        }
        return acc;
    }

    public static void intReductionAddGlobalMemory(KernelContext context, int[] a, int[] b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID
        int id = localGroupSize * groupID + localIdx;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                a[id] += a[id + stride];
            }
        }
        if (localIdx == 0) {
            b[groupID] = a[localIdx];
        }
    }

    public static void basicAccessThreadIds(KernelContext context, int[] a) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;
        int idx = localGroupSize * groupID + localIdx;
        a[idx] = idx;
    }

    @Test
    public void basic() {
        final int size = 1024;
        final int localSize = 256;
        int[] input = new int[size];

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::basicAccessThreadIds, context, input) //
                .streamOut(input);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        for (int i = 0; i < size; i++) {
            assertEquals(i, input[i]);
        }
    }

    // Copy from Global to Local and Global memory again
    public static void basicAccessThreadIds02(KernelContext context, int[] a, int[] b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;
        int idx = localGroupSize * groupID + localIdx;

        int[] localA = context.allocateIntLocalArray(256);
        localA[localIdx] = a[idx];
        b[idx] = localA[localIdx];
    }

    @Test
    public void basic02() {
        final int size = 1024;
        final int localSize = 256;
        int[] input = new int[size];
        int[] output = new int[size];

        IntStream.range(0, size).forEach(x -> input[x] = x);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::basicAccessThreadIds02, context, input, output) //
                .streamOut(output);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        for (int i = 0; i < size; i++) {
            assertEquals(input[i], output[i]);
        }
    }

    // Copy from Global to Local and Global memory again
    public static void basicAccessThreadIds03(KernelContext context, int[] a, int[] b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;
        int idx = localGroupSize * groupID + localIdx;

        int[] localA = context.allocateIntLocalArray(256);
        localA[localIdx] = a[idx];
        localA[localIdx] *= 2;
        b[idx] = localA[localIdx];
    }

    @Test
    public void basic03() {
        final int size = 1024;
        final int localSize = 256;
        int[] input = new int[size];
        int[] output = new int[size];

        IntStream.range(0, size).forEach(x -> input[x] = x);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::basicAccessThreadIds03, context, input, output) //
                .streamOut(output);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        for (int i = 0; i < size; i++) {
            assertEquals(input[i] * 2, output[i]);
        }
    }

    // Copy from Global to Local and Global memory again
    public static void basicAccessThreadIds04(KernelContext context, int[] a, int[] b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;
        int idx = localGroupSize * groupID + localIdx;

        int[] localA = context.allocateIntLocalArray(1024);
        for (int i = 0; i < 256; i++) {
            localA[i] = 2;
        }

        b[idx] = localA[localIdx];
    }

    @Test
    public void basic04() {
        final int size = 1024;
        final int localSize = 256;
        int[] input = new int[size];
        int[] output = new int[size];

        IntStream.range(0, size).forEach(x -> input[x] = x);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::basicAccessThreadIds04, context, input, output) //
                .streamOut(output);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        for (int i = 0; i < size; i++) {
            assertEquals(2, output[i], 0.0);
        }
    }

    // Copy from Global to Local and Global memory again
    public static void basicAccessThreadIds05(KernelContext context, int[] a, int[] b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx;
        int idx = localGroupSize * groupID + localIdx;

        int[] localA = context.allocateIntLocalArray(256);
        for (int i = 0; i < localGroupSize; i++) {
            localA[i] = 2;
        }

        b[idx] = localA[localIdx];
    }

    @Test
    public void basic05() {
        final int size = 1024;
        final int localSize = 256;
        int[] input = new int[size];
        int[] output = new int[size];

        IntStream.range(0, size).forEach(x -> input[x] = x);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::basicAccessThreadIds05, context, input, output) //
                .streamOut(output);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        for (int i = 0; i < size; i++) {
            assertEquals(2, output[i], 0.0);
        }
    }

    @Test
    public void testIntReductionsAddGlobalMemory() {
        final int size = 32;
        final int localSize = 1;
        int[] input = new int[size];
        int[] reduce = new int[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = 2);
        int sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input) //
                .task("t0", TestReductionsIntegersKernelContext::intReductionAddGlobalMemory, context, input, reduce) //
                .streamOut(reduce, input);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        int finalSum = 0;
        for (int v : reduce) {
            finalSum += v;
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static void intReductionAddLocalMemory(KernelContext context, int[] a, int[] b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        int[] localA = context.allocateIntLocalArray(256);
        localA[localIdx] = a[globalIdx];
        context.localBarrier();

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            if (localIdx < stride) {
                localA[localIdx] += localA[localIdx + stride];
            }
            context.localBarrier();
        }
        if (localIdx == 0) {
            b[groupID] = localA[localIdx];
        }
    }

    @Test
    public void testIntReductionsAddLocalMemory() {
        final int size = 256;
        final int localSize = 256;
        int[] input = new int[size];
        int[] reduce = new int[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        int sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .task("t0", TestReductionsIntegersKernelContext::intReductionAddLocalMemory, context, input, reduce) //
                .streamOut(reduce);

        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        int finalSum = 0;
        for (int v : reduce) {
            finalSum += v;
        }

        assertEquals(sequential, finalSum);
    }

    public static int computeMaxSequential(int[] input) {
        int acc = 0;
        for (int v : input) {
            acc = TornadoMath.max(acc, v);
        }
        return acc;
    }

    private static void intReductionMaxGlobalMemory(KernelContext context, int[] a, int[] b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID
        int id = localGroupSize * groupID + localIdx;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                a[id] = TornadoMath.max(a[id], a[id + stride]);
            }
        }
        if (localIdx == 0) {
            b[groupID] = a[id];
        }
    }

    @Test
    public void testIntReductionsMaxGlobalMemory() {
        final int size = 1024;
        final int localSize = 256;
        int[] input = new int[size];
        int[] reduce = new int[size / localSize];
        Random r = new Random();
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = r.nextInt());
        int sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::intReductionMaxGlobalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        int finalSum = 0;
        for (int v : reduce) {
            finalSum = TornadoMath.max(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static void intReductionMaxLocalMemory(KernelContext context, int[] a, int[] b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        int[] localA = context.allocateIntLocalArray(256);
        localA[localIdx] = a[globalIdx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] = TornadoMath.max(localA[localIdx], localA[localIdx + stride]);
            }
        }
        if (localIdx == 0) {
            b[groupID] = localA[0];
        }
    }

    @Test
    public void testIntReductionsMaxLocalMemory() {
        final int size = 1024;
        final int localSize = 256;
        int[] input = new int[size];
        int[] reduce = new int[size / localSize];
        Random r = new Random();
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = r.nextInt());
        int sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::intReductionMaxLocalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        int finalSum = 0;
        for (int v : reduce) {
            finalSum = TornadoMath.max(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static int computeMinSequential(int[] input) {
        int acc = 0;
        for (int v : input) {
            acc = TornadoMath.min(acc, v);
        }
        return acc;
    }

    private static void intReductionMinGlobalMemory(KernelContext context, int[] a, int[] b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID
        int id = localGroupSize * groupID + localIdx;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                a[id] = TornadoMath.min(a[id], a[id + stride]);
            }
        }
        if (localIdx == 0) {
            b[groupID] = a[id];
        }
    }

    @Test
    public void testIntReductionsMinGlobalMemory() {
        final int size = 1024;
        final int localSize = 256;
        int[] input = new int[size];
        int[] reduce = new int[size / localSize];
        Random r = new Random();
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = r.nextInt(10000000));
        int sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::intReductionMinGlobalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        int finalSum = 0;
        for (int v : reduce) {
            finalSum = TornadoMath.min(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static void intReductionMinLocalMemory(KernelContext context, int[] a, int[] b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        int[] localA = context.allocateIntLocalArray(256);
        localA[localIdx] = a[globalIdx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] = TornadoMath.min(localA[localIdx], localA[localIdx + stride]);
            }
        }
        if (localIdx == 0) {
            b[groupID] = localA[0];
        }
    }

    @Test
    public void testIntReductionsMinLocalMemory() {
        final int size = 1024;
        final int localSize = 256;
        int[] input = new int[size];
        int[] reduce = new int[size / localSize];
        Random r = new Random();
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = r.nextInt(100000));
        int sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsIntegersKernelContext::intReductionMinLocalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        int finalSum = 0;
        for (int v : reduce) {
            finalSum = TornadoMath.min(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }
}