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
package uk.ac.manchester.tornado.examples.compute_tornadovmcontext;

import uk.ac.manchester.tornado.api.GridTask;
import uk.ac.manchester.tornado.api.TornadoVMContext;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;

import uk.ac.manchester.tornado.api.TaskSchedule;

import java.util.stream.IntStream;

/**
 * Example of how to express a SCAN operation in TornadoVM using the new API
 *
 */
public class TestScanTornadoVMContext {

    public static int sumScratch(TornadoVMContext context, int[] scratch, int lid, int lsz) {
        context.localBarrier(); // make sure all of scratch is populated
        for (int step = 2; step <= lsz; step <<= 1) {
            if (((lid + 1) % step) == 0) {
                scratch[lid] += scratch[lid - (step >> 1)];
            }
            context.localBarrier();
        }
        int sum = 0;
        if ((lid + 1) == lsz) {
            sum = scratch[lid];
            scratch[lid] = 0;
        }

        context.localBarrier();
        for (int step = lsz; step > 1; step >>= 1) {
            if (((lid + 1) % step) == 0) {
                int prev = scratch[lid - (step >> 1)];
                scratch[lid - (step >> 1)] = scratch[lid];
                scratch[lid] += prev;
            }
            context.localBarrier();
        }
        return sum;
    }

    // 4 2 3 2 6 1 2 3
    // \ | \ | \ | \ |
    // \ | \ | \ | \ |
    // \ | \ | \ | \ |
    // \| \| \| \|
    // + + + +
    // 4 6 3 5 6 7 2 5
    // \ | \ |
    // \ | \ |
    // \ | \ |
    // \ | \ |
    // \ | \ |
    // \ | \ |
    // \ | \ |
    // \ | \ |
    // \| \|
    // + +
    // 4 6 3 11 6 7 2 12
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ | This last pass can be ommitted!
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \|
    // +
    // 4 6 3 11 6 7 2 23
    // |
    // overwrite with 0
    // |
    // V
    // 4 6 3 11 6 7 2 0
    // \ /|
    // \ / |
    // \ / |
    // \ / |
    // \ / |
    // \ / |
    // \ / |
    // \ / |
    // \ / |
    // / |
    // / \ |
    // / \ |
    // / \ |
    // / \ |
    // / \ |
    // / \ |
    // / \ |
    // / \ |
    // / \|
    // V +
    // 4 6 3 0 6 7 2 11
    // \ /| \ / |
    // \ / | \ / |
    // \ / | \ / |
    // \/ | \/ |
    // /\ | /\ |
    // / \ | / \ |
    // / \ | / \ |
    // / \| / \ |
    // V + V +
    // 4 0 3 6 6 11 2 18
    // \ /| \ /| \ /| \ /|
    // \/ | \/ | \/ | \/ |
    // /\ | /\ | /\ | /\ |
    // / \| / \| / \| / \|
    // V + V + V + V +
    // 0 4 6 9 11 17 18 20

    public static void prefixKernel(TornadoVMContext context, int[] data) {
        int lid = context.localIdx;
        int lsz = context.getLocalGroupSize(0);
        int gid = context.threadIdx;
        int[] scratch = context.allocateIntLocalArray(64);
        scratch[lid] = data[gid]; // copy into local scratch for the reduction
        int sum = sumScratch(context, scratch, lid, lsz);
        if ((lid + 1) == lsz) {
            data[gid] = sum;
        } else {
            data[gid] = scratch[lid + 1];
        }
    }

    public static void globalKernel(TornadoVMContext context, int[] data) {
        int lid = context.localIdx;
        int lsz = context.getLocalGroupSize(0);
        int gid = (lsz * (context.threadIdx + 1)) - 1; // lsz-1, lsz*2-1, lsz*3-1, lsz*4-1
        int[] scratch = context.allocateIntLocalArray(64);
        scratch[lid] = data[gid]; // copy into local scratch for the reduction
        int sum = sumScratch(context, scratch, lid, lsz);
        if ((lid + 1) == lsz) {
            data[gid] = sum;
        } else {
            data[gid] = scratch[lid + 1];
        }
    }

    public static void sumKernel(TornadoVMContext context, int[] data) {
        int lid = context.localIdx;
        int lsz = context.getLocalGroupSize(0);
        int gid = context.threadIdx;
        int grpid = context.groupIdx;
        int[] scratch = context.allocateIntLocalArray(64);
        scratch[lid] = data[gid]; // copy into local scratch
        context.localBarrier();
        if ((lid + 1) != lsz && grpid > 0) {
            // don't do this for last in group
            scratch[lid] += data[(grpid * lsz) - 1];
        }
        context.localBarrier();
        data[gid] = scratch[lid];
    }

    static void javaPrefixSum(int arr[], int n, int prefixSum[]) {
        prefixSum[0] = arr[0];

        // Adding present element
        // with previous element
        for (int i = 1; i < n; ++i)
            prefixSum[i] = prefixSum[i - 1] + arr[i];
    }

    public static void main(String[] args) {
        final int size = 4096;
        final int localSize = 64;
        int[] input = new int[size];
        int[] javaOutput = new int[size];
        String tornadoSDK = System.getenv("TORNADO_SDK");

        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        javaPrefixSum(input, size, javaOutput);

        // Prefix kernel
        WorkerGrid worker1 = new WorkerGrid1D(size);
        GridTask gridTask1 = new GridTask();
        gridTask1.set("s1.t0", worker1);
        TornadoVMContext context1 = new TornadoVMContext(worker1);

        TaskSchedule s1 = new TaskSchedule("s1").streamIn(input).task("t0", TestScanTornadoVMContext::prefixKernel, context1, input).streamOut(input);
        // Change the Grid
        worker1.setGlobalWork(size, 1, 1);
        worker1.setLocalWork(localSize, 1, 1);
        s1.execute(gridTask1);

        // Global kernel
        WorkerGrid worker2 = new WorkerGrid1D(size);
        GridTask gridTask2 = new GridTask();
        gridTask2.set("s2.t0", worker2);
        TornadoVMContext context2 = new TornadoVMContext(worker2);

        TaskSchedule s2 = new TaskSchedule("s2").streamIn(input).task("t0", TestScanTornadoVMContext::globalKernel, context2, input).streamOut(input);
        // Change the Grid
        worker2.setGlobalWork(size, 1, 1);
        worker2.setLocalWork(localSize, 1, 1);
        s2.execute(gridTask2);

        // Sum kernel
        WorkerGrid worker3 = new WorkerGrid1D(size);
        GridTask gridTask3 = new GridTask();
        gridTask3.set("s3.t0", worker3);
        TornadoVMContext context = new TornadoVMContext(worker3);

        TaskSchedule s3 = new TaskSchedule("s3").streamIn(input).task("t0", TestScanTornadoVMContext::sumKernel, context, input).streamOut(input);
        // Change the Grid
        worker3.setGlobalWork(size, 1, 1);
        worker3.setLocalWork(localSize, 1, 1);
        s3.execute(gridTask3);

        for (int v = 0; v < input.length; v++) {
            if (v < 10) {
                System.out.println(input[v] + " - " + javaOutput[v]);
            }
        }
    }
}
