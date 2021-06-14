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
 * min) for {@link Float} type. These unit-tests check the functional operation
 * of some {@link KernelContext} features, such as global thread identifiers,
 * local thread identifiers, the local group size of the associated WorkerGrid,
 * barriers and allocation of local memory.
 */
public class TestReductionsFloatsKernelContext extends TornadoTestBase {

    public static float computeAddSequential(float[] input) {
        float acc = 0;
        for (float v : input) {
            acc += v;
        }
        return acc;
    }

    public static void floatReductionAddGlobalMemory(KernelContext context, float[] a, float[] b) {
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
    public void testFloatReductionsAddGlobalMemory() {
        final int size = 1024;
        final int localSize = 256;
        float[] input = new float[size];
        float[] reduce = new float[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        float sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsFloatsKernelContext::floatReductionAddGlobalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        float finalSum = 0;
        for (float v : reduce) {
            finalSum += v;
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static void floatReductionAddLocalMemory(KernelContext context, float[] a, float[] b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
        int groupID = context.groupIdx; // Expose Group ID

        float[] localA = context.allocateFloatLocalArray(256);
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
    public void testFloatReductionsAddLocalMemory() {
        final int size = 1024;
        final int localSize = 256;
        float[] input = new float[size];
        float[] reduce = new float[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        float sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsFloatsKernelContext::floatReductionAddLocalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        float finalSum = 0;
        for (float v : reduce) {
            finalSum += v;
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static float computeMaxSequential(float[] input) {
        float acc = 0;
        for (float v : input) {
            acc = TornadoMath.max(acc, v);
        }
        return acc;
    }

    private static void floatReductionMaxGlobalMemory(KernelContext context, float[] a, float[] b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
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
    public void testFloatReductionsMaxGlobalMemory() {
        final int size = 1024;
        final int localSize = 256;
        float[] input = new float[size];
        float[] reduce = new float[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        float sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsFloatsKernelContext::floatReductionMaxGlobalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        float finalSum = 0;
        for (float v : reduce) {
            finalSum = TornadoMath.max(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static void floatReductionMaxLocalMemory(KernelContext context, float[] a, float[] b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
        int groupID = context.groupIdx; // Expose Group ID

        float[] localA = context.allocateFloatLocalArray(256);
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
    public void testFloatReductionsMaxLocalMemory() {
        final int size = 1024;
        final int localSize = 256;
        float[] input = new float[size];
        float[] reduce = new float[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        float sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsFloatsKernelContext::floatReductionMaxLocalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        float finalSum = 0;
        for (float v : reduce) {
            finalSum = TornadoMath.max(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static float computeMinSequential(float[] input) {
        float acc = 0;
        for (float v : input) {
            acc = TornadoMath.min(acc, v);
        }
        return acc;
    }

    private static void floatReductionMinGlobalMemory(KernelContext context, float[] a, float[] b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
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
    public void testFloatReductionsMinGlobalMemory() {
        final int size = 1024;
        final int localSize = 256;
        float[] input = new float[size];
        float[] reduce = new float[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        float sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsFloatsKernelContext::floatReductionMinGlobalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        float finalSum = 0;
        for (float v : reduce) {
            finalSum = TornadoMath.min(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static void floatReductionMinLocalMemory(KernelContext context, float[] a, float[] b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
        int groupID = context.groupIdx; // Expose Group ID

        float[] localA = context.allocateFloatLocalArray(256);
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
    public void testFloatReductionsMinLocalMemory() {
        final int size = 1024;
        final int localSize = 256;
        float[] input = new float[size];
        float[] reduce = new float[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        float sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0") //
                .streamIn(input, localSize) //
                .task("t0", TestReductionsFloatsKernelContext::floatReductionMinLocalMemory, context, input, reduce) //
                .streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        float finalSum = 0;
        for (float v : reduce) {
            finalSum = TornadoMath.min(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }
}