/*
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
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
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The unit-tests in this class implement reduce-operations such as add, max,
 * and min., using the {@link Double} data type. These unit-tests check the
 * functional operation of some {@link KernelContext} features, such as global
 * thread identifiers, local thread identifiers, the local group size of the
 * associated WorkerGrid, barriers and allocation of local memory.
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestReductionsDoublesKernelContext
 * </code>
 *
 */
public class TestReductionsDoublesKernelContext extends TornadoTestBase {

    public static double computeAddSequential(double[] input) {
        double acc = 0;
        for (double v : input) {
            acc += v;
        }
        return acc;
    }

    public static void doubleReductionAddGlobalMemory(KernelContext context, double[] a, double[] b) {
        // Access the Local Thread ID via the KernelContext
        int localIdx = context.localIdx;

        // Access the Group Size
        int localGroupSize = context.localGroupSizeX;

        // Access the Group-ID
        int groupID = context.groupIdx; // Expose Group ID

        // Compute the thread-id that is running
        int id = localGroupSize * groupID + localIdx;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            // Insert a local barrier to guarantee order in local-memory (OpenCL)
            context.localBarrier();
            if (localIdx < stride) {
                a[id] += a[id + stride];
            }
        }

        if (localIdx == 0) {
            // Copy the result of the reduction
            b[groupID] = a[id];
        }
    }

    @Test
    public void testDoubleReductionsAddGlobalMemory() {
        final int size = 1024;
        final int localSize = 256;
        double[] input = new double[size];
        double[] reduce = new double[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        double sequential = computeAddSequential(input);

        // Create a 1D worker
        WorkerGrid worker = new WorkerGrid1D(size);

        // Attach the Worker to the GridScheduler
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);

        // Create a KernelContext with its own worker
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsDoublesKernelContext::doubleReductionAddGlobalMemory, context, input, reduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);

        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        // Final Reduction
        double finalSum = 0;
        for (double v : reduce) {
            finalSum += v;
        }
        assertEquals(sequential, finalSum, 0);
    }

    /**
     * Parallel reduction in TornadoVM using Local Memory
     *
     * @param context
     *            {@link KernelContext}
     * @param a
     *            input array
     * @param b
     *            output array
     */
    private static void doubleReductionAddLocalMemory(KernelContext context, double[] a, double[] b) {

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
        double[] localA = context.allocateDoubleLocalArray(256);

        // Copy data from global memory to local memory.
        localA[localIdx] = a[globalIdx];

        // Compute the reduction in local memory
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] += localA[localIdx + stride];
            }
        }

        // Copy result of the full reduction within the work-group into global memory.
        if (localIdx == 0) {
            b[groupID] = localA[0];
        }
    }

    @Test
    public void testDoubleReductionsAddLocalMemory() {
        final int size = 1024;
        final int localSize = 256;
        double[] input = new double[size];
        double[] reduce = new double[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        double sequential = computeAddSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsDoublesKernelContext::doubleReductionAddLocalMemory, context, input, reduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        // Final SUM
        double finalSum = 0;
        for (double v : reduce) {
            finalSum += v;
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static double computeMaxSequential(double[] input) {
        double acc = 0;
        for (double v : input) {
            acc = TornadoMath.max(acc, v);
        }
        return acc;
    }

    private static void doubleReductionMaxGlobalMemory(KernelContext context, double[] a, double[] b) {
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
    public void testDoubleReductionsMaxGlobalMemory() {
        final int size = 1024;
        final int localSize = 256;
        double[] input = new double[size];
        double[] reduce = new double[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        double sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsDoublesKernelContext::doubleReductionMaxGlobalMemory, context, input, reduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        // Final SUM
        double finalSum = 0;
        for (double v : reduce) {
            finalSum = TornadoMath.max(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static void doubleReductionMaxLocalMemory(KernelContext context, double[] a, double[] b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        double[] localA = context.allocateDoubleLocalArray(256);
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
    public void testDoubleReductionsMaxLocalMemory() {
        final int size = 1024;
        final int localSize = 256;
        double[] input = new double[size];
        double[] reduce = new double[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        double sequential = computeMaxSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsDoublesKernelContext::doubleReductionMaxLocalMemory, context, input, reduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        // Final SUM
        double finalSum = 0;
        for (double v : reduce) {
            finalSum = TornadoMath.max(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static double computeMinSequential(double[] input) {
        double acc = 0;
        for (double v : input) {
            acc = TornadoMath.min(acc, v);
        }
        return acc;
    }

    private static void doubleReductionMinGlobalMemory(KernelContext context, double[] a, double[] b) {
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
    public void testDoubleReductionsMinGlobalMemory() {
        final int size = 1024;
        final int localSize = 256;
        double[] input = new double[size];
        double[] reduce = new double[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        double sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsDoublesKernelContext::doubleReductionMinGlobalMemory, context, input, reduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        // Final SUM
        double finalSum = 0;
        for (double v : reduce) {
            finalSum = TornadoMath.min(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }

    public static void doubleReductionMinLocalMemory(KernelContext context, double[] a, double[] b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        double[] localA = context.allocateDoubleLocalArray(256);
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
    public void testDoubleReductionsMinLocalMemory() {
        final int size = 1024;
        final int localSize = 256;
        double[] input = new double[size];
        double[] reduce = new double[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        double sequential = computeMinSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, localSize) //
                .task("t0", TestReductionsDoublesKernelContext::doubleReductionMinLocalMemory, context, input, reduce) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler) //
                .execute();

        // Final SUM
        double finalSum = 0;
        for (double v : reduce) {
            finalSum = TornadoMath.min(finalSum, v);
        }

        assertEquals(sequential, finalSum, 0);
    }
}