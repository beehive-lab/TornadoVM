/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.nccl.tests;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.nccl.Nccl;
import uk.ac.manchester.tornado.nccl.NcclCommunicator;
import uk.ac.manchester.tornado.nccl.NcclGroup;
import uk.ac.manchester.tornado.nccl.NcclPlanGroup;
import uk.ac.manchester.tornado.nccl.NcclRedOp;
import uk.ac.manchester.tornado.nccl.provider.NcclNativeLib;

/**
 * Benchmark: all-reducing several buffers per step (the gradients of several layers, say), either
 * as one library task per buffer or as one {@link NcclGroup} holding all of them. Every GPU of the
 * CUDA backend is a rank; the plans hold only the collectives, so the difference is what grouping
 * saves in launches. Times are medians of the step after warm-up.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.BenchmarkNcclGroup [buffers] [elements,elements,...] [iterations]
 * </code>
 */
public class BenchmarkNcclGroup {

    private static final int WARMUP_ITERATIONS = 5;

    private static double median(long[] times) {
        long[] sorted = times.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    /** Median nanoseconds of a step that all-reduces {@code buffers} buffers of {@code size} floats. */
    private static double step(TornadoDevice[] devices, int buffers, int size, int iterations, boolean grouped) {
        int ranks = devices.length;
        TornadoExecutionPlan[] plans = new TornadoExecutionPlan[ranks];
        long[] times = new long[iterations];
        try (NcclCommunicator communicator = NcclCommunicator.create(devices)) {
            try {
                for (int rank = 0; rank < ranks; rank++) {
                    TaskGraph graph = new TaskGraph((grouped ? "grouped" : "separate") + rank);
                    FloatArray[] arrays = new FloatArray[buffers];
                    NcclGroup group = NcclGroup.on(communicator);
                    for (int b = 0; b < buffers; b++) {
                        arrays[b] = new FloatArray(size);
                        arrays[b].init(rank + 1.0f);
                        graph.transferToDevice(DataTransferMode.FIRST_EXECUTION, arrays[b]);
                        if (grouped) {
                            group.allReduceInPlace(arrays[b], NcclRedOp.SUM);
                        } else {
                            graph.libraryTask("sum" + b, Nccl::allReduceInPlace, communicator, arrays[b], NcclRedOp.SUM);
                        }
                    }
                    if (grouped) {
                        graph.libraryTask("sums", Nccl::group, group);
                    }
                    graph.transferToHost(DataTransferMode.UNDER_DEMAND, (Object[]) arrays);
                    plans[rank] = new TornadoExecutionPlan(graph.snapshot());
                    plans[rank].withDevice(devices[rank]);
                }
                try (NcclPlanGroup group = new NcclPlanGroup(plans)) {
                    for (int i = 0; i < WARMUP_ITERATIONS + iterations; i++) {
                        long start = System.nanoTime();
                        group.execute();
                        if (i >= WARMUP_ITERATIONS) {
                            times[i - WARMUP_ITERATIONS] = System.nanoTime() - start;
                        }
                    }
                }
            } finally {
                for (TornadoExecutionPlan plan : plans) {
                    if (plan != null) {
                        try {
                            plan.close();
                        } catch (TornadoExecutionPlanException e) {
                            throw new TornadoRuntimeException(e);
                        }
                    }
                }
            }
        }
        return median(times);
    }

    public static void main(String[] args) {
        int buffers = args.length > 0 ? Integer.parseInt(args[0]) : 8;
        int[] sizes = Arrays.stream((args.length > 1 ? args[1] : "1024,65536,1048576").split(",")).mapToInt(Integer::parseInt).toArray();
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 200;

        int numDevices = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getNumDevices();
        TornadoDevice[] devices = new TornadoDevice[numDevices];
        for (int i = 0; i < numDevices; i++) {
            devices[i] = TornadoExecutionPlan.getDevice(0, i);
        }
        int version = NcclNativeLib.version();
        System.out.printf("NCCL %d.%d.%d, %d rank(s), %d all-reduces per step, %d iterations (median)%n", version / 10000, (version / 100) % 100, version % 100, numDevices, buffers, iterations);
        System.out.printf("%12s %12s | %16s %16s %9s%n", "elements", "MiB/buffer", "separate tasks", "one group", "speedup");
        step(devices, buffers, sizes[0], iterations, true);
        for (int size : sizes) {
            double separate = step(devices, buffers, size, iterations, false);
            double grouped = step(devices, buffers, size, iterations, true);
            System.out.printf("%12d %12.3f | %13.1f us %13.1f us %8.2fx%n", size, size * 4.0 / (1 << 20), separate / 1e3, grouped / 1e3, separate / grouped);
        }
    }
}
