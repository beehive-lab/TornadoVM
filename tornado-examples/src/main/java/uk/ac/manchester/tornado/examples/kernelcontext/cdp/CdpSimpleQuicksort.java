/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.examples.kernelcontext.cdp;

import java.util.Random;

import uk.ac.manchester.tornado.api.DeviceKernel;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.DeviceLaunchMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Port of {@code cdpSimpleQuicksort} from NVIDIA's cuda-samples ({@code cpp/3_CUDA_Features/cdpSimpleQuicksort}) to CUDA Dynamic
 * Parallelism in TornadoVM ({@link KernelContext#launch}). Quicksort in which a single thread partitions a range and launches one grid per side, each into its own stream. Small or deep ranges are finished with a selection sort.
 *
 * <p>
 * The same kernels are tested in {@code TestCudaSamplesDynamicParallelism}. How to run (CUDA backend):
 * </p>
 *
 * <pre>
 * tornado --threadInfo -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.cdp.CdpSimpleQuicksort
 * </pre>
 */
public class CdpSimpleQuicksort {

    private static GridScheduler grid(String task, int global, int local) {
        WorkerGrid worker = new WorkerGrid1D(global);
        worker.setLocalWork(local, 1, 1);
        return new GridScheduler(task, worker);
    }

    private static final int QS_MAX_DEPTH = 16;
    private static final int QS_INSERTION_SORT = 32;

    private static final DeviceKernel SIMPLE_QUICKSORT = DeviceKernel.of(CdpSimpleQuicksort::cdpSimpleQuicksort);

    /** selection_sort: used when the recursion is too deep or the range is small. */
    private static void selectionSort(IntArray data, int left, int right) {
        for (int i = left; i <= right; ++i) {
            int minVal = data.get(i);
            int minIdx = i;
            for (int j = i + 1; j <= right; ++j) {
                int valJ = data.get(j);
                if (valJ < minVal) {
                    minIdx = j;
                    minVal = valJ;
                }
            }
            if (i != minIdx) {
                data.set(minIdx, data.get(i));
                data.set(i, minVal);
            }
        }
    }

    /** cdp_simple_quicksort: one thread partitions [left, right] and launches one grid per side. */
    private static void cdpSimpleQuicksort(KernelContext context, IntArray data, int left, int right, int depth) {
        // '|' rather than '||': see beehive-lab/TornadoVM#1137.
        if (depth >= QS_MAX_DEPTH | right - left <= QS_INSERTION_SORT) {
            selectionSort(data, left, right);
            return;
        }
        int lptr = left;
        int rptr = right;
        int pivot = data.get((left + right) / 2);
        while (lptr <= rptr) {
            int lval = data.get(lptr);
            int rval = data.get(rptr);
            while (lval < pivot) {
                lptr++;
                lval = data.get(lptr);
            }
            while (rval > pivot) {
                rptr--;
                rval = data.get(rptr);
            }
            if (lptr <= rptr) {
                data.set(lptr, rval);
                data.set(rptr, lval);
                lptr++;
                rptr--;
            }
        }
        if (left < rptr) {
            context.launch(SIMPLE_QUICKSORT, DeviceLaunchMode.FIRE_AND_FORGET, 1, 1, data, left, rptr, depth + 1);
        }
        if (lptr < right) {
            context.launch(SIMPLE_QUICKSORT, DeviceLaunchMode.FIRE_AND_FORGET, 1, 1, data, lptr, right, depth + 1);
        }
    }

    public static void main(String[] args) throws TornadoExecutionPlanException {
        final int numItems = args.length > 0 ? Integer.parseInt(args[0]) : 128;
        Random random = new Random(2047);
        IntArray data = new IntArray(numItems);
        for (int i = 0; i < numItems; i++) {
            data.set(i, random.nextInt(numItems));
        }
        System.out.println("Running on GPU with " + numItems + " items");
        TaskGraph taskGraph = new TaskGraph("qsort") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task("cdp", CdpSimpleQuicksort::cdpSimpleQuicksort, new KernelContext(), data, 0, numItems - 1, 0) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("qsort.cdp", 1, 1)).execute();
        }
        for (int i = 1; i < numItems; i++) {
            if (data.get(i - 1) > data.get(i)) {
                System.out.println("Invalid item[" + (i - 1) + "]: " + data.get(i - 1) + " greater than " + data.get(i));
                return;
            }
        }
        System.out.println("OK");
    }
}
