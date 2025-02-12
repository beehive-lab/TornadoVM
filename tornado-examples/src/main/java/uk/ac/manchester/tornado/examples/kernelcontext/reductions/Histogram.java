/*
 * Copyright (c) 2024 APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.kernelcontext.reductions;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import java.util.Random;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * $ tornado --threadInfo -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.reductions.Histogram
 * </code>
 */
public class Histogram {

    private static final int NUM_BINS = 4;
    private static final int BLOCK_SIZE = 256;
    private static int size = 256;

    /*public static void histogramOptimized(KernelContext context, IntArray inputData, IntArray histData) {
        int[] localHist= context.allocateIntLocalArray(NUM_BINS);
    //        __shared__ int localHist[NUM_BINS];

    //        int tid = threadIdx.x + blockIdx.x * blockDim.x;
        int tid = context.globalIdx;


        if (context.localIdx < NUM_BINS) {
            localHist[context.localIdx] = 0;
        }
        context.localBarrier();

        if (tid < size) {
            atomicAdd(&localHist[inputData.get(tid)], 1);
        }
        context.localBarrier();

        if (context.localIdx < NUM_BINS) {
            atomicAdd(&histData[context.localIdx], localHist[context.localIdx]);
        }
    }*/

    /**
     * This method implements the following CUDA kernel with the TornadoVM Kernel API.
     *
     * __global__ void histogramKernel(int *data, int *hist, int dataSize) {
     * int tid = threadIdx.x + blockIdx.x * blockDim.x;
     *
     * if (tid < dataSize) {
     * atomicAdd(&hist[data[tid]], 1); // Atomic increment to avoid race conditions
     * }
     * }
     *
     * @param context
     * @param input
     * @param output
     */
    public static void histogramKernel(KernelContext context, IntArray input, IntArray output) {
        int tid = context.globalIdx;

        if (tid < input.getSize()) {
            int index = input.get(tid);
            context.atomicAdd(output, index, 1);
            output.set(index, output.get(index));
        }

        //        for (@Parallel int tid = 0; tid < input.getSize(); tid++) {
        //            int index = input.get(tid);
        //            context.atomicAdd(output, index, 1);
        //            output.set(index, output.get(index));
        //        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        //        int[] data = new int[size];
        Random rand = new Random();
        //        IntArray inputData = IntArray.fromArray(data);
        IntArray inputData = new IntArray(size);
        IntArray histData = new IntArray(size);

        // Initialize random data
        for (int i = 0; i < size; i++) {
            inputData.set(i, rand.nextInt(NUM_BINS));
            //            inputData.set(i, i);
        }
        inputData.init(2);
        histData.init(0);

        KernelContext context = new KernelContext();

        WorkerGrid workerGrid = new WorkerGrid1D(size);
        workerGrid.setGlobalWork(size, 1, 1);
        workerGrid.setLocalWork(BLOCK_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputData) //
                .task("t0", Histogram::histogramKernel, context, inputData, histData) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, histData); //

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.withGridScheduler(gridScheduler).execute();
            //            executionPlan.withDefaultScheduler().execute();
        }

        for (int i = 0; i < NUM_BINS + 1; i++) {
            System.out.println("histData[" + i + "]: " + histData.get(i));
        }
    }
}
