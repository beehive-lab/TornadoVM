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
package uk.ac.manchester.tornado.examples.kernelcontext.reductions;

import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;

public class ReductionsLocalMemory {

    // Reduction in Local memory using KernelContext
    public static void reductionLocal(float[] a, float[] b, int localSize, KernelContext context) {
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

    public static float computeSequential(float[] input) {
        float acc = 0;
        for (float v : input) {
            acc += v;
        }
        return acc;
    }

    public static void rAdd(final float[] array, int size) {
        float acc = array[0];
        for (int i = 1; i < array.length; i++) {
            acc += array[i];
        }
        array[0] = acc;
    }

    public static void main(String[] args) {
        final int size = 1024;
        final int localSize = 256;
        float[] input = new float[size];
        float[] reduce = new float[size / localSize];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        float sequential = computeSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler();
        gridScheduler.setWorkerGrid("s0.t0", worker);
        KernelContext context = new KernelContext();

        TaskSchedule s0 = new TaskSchedule("s0").streamIn(input, localSize).task("t0", ReductionsLocalMemory::reductionLocal, input, reduce, localSize, context)
                .task("t1", ReductionsLocalMemory::rAdd, reduce, (size / localSize)).streamOut(reduce);
        // Change the Grid
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridScheduler);

        // Final SUM
        float finalSum = reduce[0];

        System.out.println("Final SUM = " + finalSum + " vs seq= " + sequential);
        if ((sequential - finalSum) == 0) {
            System.out.println("Result is correct");
        } else {
            System.out.println("Result is wrong");
        }
    }
}