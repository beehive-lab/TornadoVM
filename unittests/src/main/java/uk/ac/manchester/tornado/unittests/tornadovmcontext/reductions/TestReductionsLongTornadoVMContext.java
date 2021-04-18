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
package uk.ac.manchester.tornado.unittests.tornadovmcontext.reductions;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridTask;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoVMContext;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The unit-tests in this class implement some Reduction operations (add, max,
 * min) for {@link Long} type. These unit-tests check the functional operation
 * of some {@link TornadoVMContext} features, such as global thread identifiers,
 * local thread identifiers, the local group size of the associated WorkerGrid,
 * barriers and allocation of local memory.
 */
public class TestReductionsLongTornadoVMContext extends TornadoTestBase {

    public static long computeAddSequential(long[] input) {
        long acc = 0;
        for (long v : input) {
            acc += v;
        }
        return acc;
    }

    public static void longReductionAddGlobalMemory(TornadoVMContext context, long[] a, long[] b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
        int groupID = context.groupIdx; // Expose Group ID
        int id = localGroupSize * groupID + localIdx;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                a[id] += a[id + stride];
            }
        }
        if (localIdx == 0) {
            b[groupID] = a[id];
        }
    }

    @Test
    public void testLongReductionsAddGlobalMemory() {
        final int size = 1024;
        final int localSize = 256;
        long[] input = new long[size];
        long[] reduce = new long[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        long sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask("s0.t0", worker);
        TornadoVMContext context = new TornadoVMContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsLongTornadoVMContext::longReductionAddGlobalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridTask);

        // Final SUM
        long finalSum = 0;
        for (long v : reduce) {
            finalSum += v;
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static void longReductionAddLocalMemory(TornadoVMContext context, long[] a, long[] b) {
        int globalIdx = context.threadIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
        int groupID = context.groupIdx; // Expose Group ID

        long[] localA = context.allocateLongLocalArray(256);
        localA[localIdx] = a[globalIdx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] += localA[localIdx + stride];
            }
        }
        if (localIdx == 0) {
            b[groupID] = localA[0];
        }
    }

    @Test
    public void testLongReductionsAddLocalMemory() {
        final int size = 1024;
        final int localSize = 256;
        long[] input = new long[size];
        long[] reduce = new long[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        long sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.setWorkerGrid("s0.t0", worker);
        TornadoVMContext context = new TornadoVMContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsLongTornadoVMContext::longReductionAddLocalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridTask);

        // Final SUM
        long finalSum = 0;
        for (long v : reduce) {
            finalSum += v;
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static long computeMaxSequential(long[] input) {
        long acc = 0;
        for (long v : input) {
            acc = Math.max(acc, v);
        }
        return acc;
    }

    private static void longReductionMaxGlobalMemory(TornadoVMContext context, long[] a, long[] b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
        int groupID = context.groupIdx; // Expose Group ID
        int id = localGroupSize * groupID + localIdx;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                a[id] = Math.max(a[id], a[id + stride]);
            }
        }
        if (localIdx == 0) {
            b[groupID] = a[id];
        }
    }

    @Test
    public void testLongReductionsMaxGlobalMemory() {
        final int size = 1024;
        final int localSize = 256;
        long[] input = new long[size];
        long[] reduce = new long[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        long sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask("s0.t0", worker);
        TornadoVMContext context = new TornadoVMContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsLongTornadoVMContext::longReductionMaxGlobalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridTask);

        // Final SUM
        long finalSum = 0;
        for (long v : reduce) {
            finalSum = Math.max(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static void longReductionMaxLocalMemory(TornadoVMContext context, long[] a, long[] b) {
        int globalIdx = context.threadIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
        int groupID = context.groupIdx; // Expose Group ID

        long[] localA = context.allocateLongLocalArray(256);
        localA[localIdx] = a[globalIdx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] = Math.max(localA[localIdx], localA[localIdx + stride]);
            }
        }
        if (localIdx == 0) {
            b[groupID] = localA[0];
        }
    }

    @Test
    public void testLongReductionsMaxLocalMemory() {
        final int size = 1024;
        final int localSize = 256;
        long[] input = new long[size];
        long[] reduce = new long[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        long sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask("s0.t0", worker);
        TornadoVMContext context = new TornadoVMContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsLongTornadoVMContext::longReductionMaxLocalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridTask);

        // Final SUM
        long finalSum = 0;
        for (long v : reduce) {
            finalSum = Math.max(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static long computeMinSequential(long[] input) {
        long acc = 0;
        for (long v : input) {
            acc = Math.min(acc, v);
        }
        return acc;
    }

    private static void longReductionMinGlobalMemory(TornadoVMContext context, long[] a, long[] b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
        int groupID = context.groupIdx; // Expose Group ID
        int id = localGroupSize * groupID + localIdx;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                a[id] = Math.min(a[id], a[id + stride]);
            }
        }
        if (localIdx == 0) {
            b[groupID] = a[id];
        }
    }

    @Test
    public void testLongReductionsMinGlobalMemory() {
        final int size = 1024;
        final int localSize = 256;
        long[] input = new long[size];
        long[] reduce = new long[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        long sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask("s0.t0", worker);
        TornadoVMContext context = new TornadoVMContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsLongTornadoVMContext::longReductionMinGlobalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridTask);

        // Final SUM
        long finalSum = 0;
        for (long v : reduce) {
            finalSum = Math.min(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static void longReductionMinLocalMemory(TornadoVMContext context, long[] a, long[] b) {
        int globalIdx = context.threadIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
        int groupID = context.groupIdx; // Expose Group ID

        long[] localA = context.allocateLongLocalArray(256);
        localA[localIdx] = a[globalIdx];
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] = Math.min(localA[localIdx], localA[localIdx + stride]);
            }
        }
        if (localIdx == 0) {
            b[groupID] = localA[0];
        }
    }

    @Test
    public void testLongReductionsMinLocalMemory() {
        final int size = 1024;
        final int localSize = 256;
        long[] input = new long[size];
        long[] reduce = new long[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        long sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask("s0.t0", worker);
        TornadoVMContext context = new TornadoVMContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsLongTornadoVMContext::longReductionMinLocalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridTask);

        // Final SUM
        long finalSum = 0;
        for (long v : reduce) {
            finalSum = Math.min(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }
}