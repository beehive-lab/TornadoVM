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
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.nccl.Nccl;
import uk.ac.manchester.tornado.nccl.NcclCommunicator;
import uk.ac.manchester.tornado.nccl.NcclPlanGroup;
import uk.ac.manchester.tornado.nccl.NcclRedOp;
import uk.ac.manchester.tornado.nccl.provider.NcclNativeLib;

/**
 * Benchmark: one step of a data-parallel pipeline on every GPU of the CUDA backend. Each rank runs
 * a kernel that produces its contribution, the contributions are summed across ranks, and a second
 * kernel consumes the sum. Two ways to sum:
 * <ul>
 * <li><b>host-staged</b>: what TornadoVM offers without NCCL. Each rank copies its buffer to the
 * host, the host adds them up (in parallel), and each rank copies the sum back.</li>
 * <li><b>NCCL</b>: an {@code allReduceInPlace} library task between the two kernels; the data never
 * leaves the GPUs.</li>
 * </ul>
 * Both variants run the same two kernels and are checked against the expected sum. Times are
 * medians of the step, after warm-up; bus bandwidth follows the nccl-tests convention for
 * all-reduce, {@code 2 (n - 1) / n} times the algorithm bandwidth.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado [--jvm="-Dtornado.nccl.benchmark.cudaGraph=true"] -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.BenchmarkNcclAllReduce [elements,elements,...] [iterations]
 * </code>
 */
public class BenchmarkNcclAllReduce {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int LOCAL_SIZE = 256;

    /** With {@code -Dtornado.nccl.benchmark.cudaGraph=true}, every plan is captured into a CUDA graph and replayed. */
    private static final boolean CUDA_GRAPH = Boolean.getBoolean("tornado.nccl.benchmark.cudaGraph");

    private static TornadoExecutionPlan graphIfRequested(TornadoExecutionPlan plan) {
        if (CUDA_GRAPH) {
            plan.withCUDAGraph();
        }
        return plan;
    }

    public static void produce(KernelContext context, FloatArray array, float value) {
        int id = context.globalIdx;
        if (id < array.getSize()) {
            array.set(id, value + id % 13);
        }
    }

    public static void consume(KernelContext context, FloatArray array) {
        int id = context.globalIdx;
        if (id < array.getSize()) {
            array.set(id, array.get(id) + 1.0f);
        }
    }

    private static GridScheduler grid(String graph, int size, String... tasks) {
        GridScheduler gridScheduler = new GridScheduler();
        for (String task : tasks) {
            WorkerGrid worker = new WorkerGrid1D(size);
            worker.setLocalWork(LOCAL_SIZE, 1, 1);
            gridScheduler.addWorkerGrid(graph + "." + task, worker);
        }
        return gridScheduler;
    }

    private static float expected(int ranks, int i) {
        return ranks * (ranks + 1) / 2.0f + ranks * (i % 13) + 1.0f;
    }

    private static void check(String variant, FloatArray array, int ranks) {
        for (int i = 0; i < array.getSize(); i++) {
            if (array.get(i) != expected(ranks, i)) {
                throw new TornadoRuntimeException("[ERROR] " + variant + ": element " + i + " is " + array.get(i) + ", expected " + expected(ranks, i));
            }
        }
    }

    private static double median(long[] times) {
        long[] sorted = times.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static void closeAll(TornadoExecutionPlan... plans) {
        for (TornadoExecutionPlan plan : plans) {
            if (plan == null) {
                continue;
            }
            try {
                plan.close();
            } catch (TornadoExecutionPlanException e) {
                throw new TornadoRuntimeException(e);
            }
        }
    }

    /**
     * Median nanoseconds of one step with NCCL doing the sum on the GPUs. Without kernels the plan
     * holds the all-reduce alone, which is what the bandwidth columns are computed from.
     */
    private static double nccl(TornadoDevice[] devices, int size, int iterations, boolean withKernels) {
        int ranks = devices.length;
        FloatArray[] buffers = new FloatArray[ranks];
        TornadoExecutionPlan[] plans = new TornadoExecutionPlan[ranks];
        long[] times = new long[iterations];
        // The plans are closed before the communicator: a captured CUDA graph refers to it, and
        // destroying a communicator that an instantiated graph still uses blocks.
        try (NcclCommunicator communicator = NcclCommunicator.create(devices)) {
            try {
                for (int rank = 0; rank < ranks; rank++) {
                    String name = "nccl" + rank;
                    buffers[rank] = new FloatArray(size);
                    TaskGraph graph = new TaskGraph(name);
                    if (withKernels) {
                        graph.task("produce", BenchmarkNcclAllReduce::produce, new KernelContext(), buffers[rank], rank + 1.0f);
                    }
                    graph.libraryTask("sum", Nccl::allReduceInPlace, communicator, buffers[rank], NcclRedOp.SUM);
                    if (withKernels) {
                        graph.task("consume", BenchmarkNcclAllReduce::consume, new KernelContext(), buffers[rank]);
                    }
                    graph.transferToHost(DataTransferMode.UNDER_DEMAND, buffers[rank]);
                    plans[rank] = graphIfRequested(new TornadoExecutionPlan(graph.snapshot()));
                    plans[rank].withDevice(devices[rank]);
                    if (withKernels) {
                        plans[rank].withGridScheduler(grid(name, size, "produce", "consume"));
                    }
                }
                try (NcclPlanGroup group = new NcclPlanGroup(plans)) {
                    TornadoExecutionResult[] results = null;
                    for (int i = 0; i < WARMUP_ITERATIONS + iterations; i++) {
                        long start = System.nanoTime();
                        results = group.execute();
                        if (i >= WARMUP_ITERATIONS) {
                            times[i - WARMUP_ITERATIONS] = System.nanoTime() - start;
                        }
                    }
                    for (int rank = 0; rank < ranks && withKernels; rank++) {
                        results[rank].transferToHost(buffers[rank]);
                        check("NCCL", buffers[rank], ranks);
                    }
                }
            } finally {
                closeAll(plans);
            }
        }
        return median(times);
    }

    /** Median nanoseconds of one step with the sum done on the host between two copies. */
    private static double hostStaged(TornadoDevice[] devices, int size, int iterations) {
        int ranks = devices.length;
        FloatArray[] contributions = new FloatArray[ranks];
        FloatArray[] sums = new FloatArray[ranks];
        TornadoExecutionPlan[] producers = new TornadoExecutionPlan[ranks];
        TornadoExecutionPlan[] consumers = new TornadoExecutionPlan[ranks];
        long[] times = new long[iterations];
        try {
            for (int rank = 0; rank < ranks; rank++) {
                contributions[rank] = new FloatArray(size);
                sums[rank] = new FloatArray(size);
                String producer = "producer" + rank;
                TaskGraph produceGraph = new TaskGraph(producer) //
                        .task("produce", BenchmarkNcclAllReduce::produce, new KernelContext(), contributions[rank], rank + 1.0f) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, contributions[rank]);
                producers[rank] = graphIfRequested(new TornadoExecutionPlan(produceGraph.snapshot()));
                producers[rank].withDevice(devices[rank]).withGridScheduler(grid(producer, size, "produce"));

                String consumer = "consumer" + rank;
                TaskGraph consumeGraph = new TaskGraph(consumer) //
                        .transferToDevice(DataTransferMode.EVERY_EXECUTION, sums[rank]) //
                        .task("consume", BenchmarkNcclAllReduce::consume, new KernelContext(), sums[rank]) //
                        .transferToHost(DataTransferMode.UNDER_DEMAND, sums[rank]);
                consumers[rank] = graphIfRequested(new TornadoExecutionPlan(consumeGraph.snapshot()));
                consumers[rank].withDevice(devices[rank]).withGridScheduler(grid(consumer, size, "consume"));
            }
            try (NcclPlanGroup produce = new NcclPlanGroup(producers); NcclPlanGroup consume = new NcclPlanGroup(consumers)) {
                TornadoExecutionResult[] results = null;
                for (int i = 0; i < WARMUP_ITERATIONS + iterations; i++) {
                    long start = System.nanoTime();
                    produce.execute();
                    IntStream.range(0, size).parallel().forEach(e -> {
                        float sum = 0.0f;
                        for (FloatArray contribution : contributions) {
                            sum += contribution.get(e);
                        }
                        for (FloatArray rankSum : sums) {
                            rankSum.set(e, sum);
                        }
                    });
                    results = consume.execute();
                    if (i >= WARMUP_ITERATIONS) {
                        times[i - WARMUP_ITERATIONS] = System.nanoTime() - start;
                    }
                }
                for (int rank = 0; rank < ranks; rank++) {
                    results[rank].transferToHost(sums[rank]);
                    check("host-staged", sums[rank], ranks);
                }
            }
        } finally {
            closeAll(producers);
            closeAll(consumers);
        }
        return median(times);
    }

    public static void main(String[] args) {
        int[] sizes = Arrays.stream((args.length > 0 ? args[0] : "1024,65536,1048576,16777216,67108864").split(",")).mapToInt(Integer::parseInt).toArray();
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 50;

        int numDevices = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getNumDevices();
        TornadoDevice[] devices = new TornadoDevice[numDevices];
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < numDevices; i++) {
            devices[i] = TornadoExecutionPlan.getDevice(0, i);
            names.append(i == 0 ? "" : ", ").append(devices[i].getPhysicalDevice().getDeviceName());
        }
        int version = NcclNativeLib.version();
        String mode = CUDA_GRAPH ? ", CUDA graphs" : "";
        System.out.printf("NCCL %d.%d.%d, %d rank(s): %s, %d iterations (median)%s%n", version / 10000, (version / 100) % 100, version % 100, numDevices, names, iterations, mode);
        System.out.printf("%12s %10s | %16s %12s %9s | %16s %12s %12s%n", "elements", "MiB/rank", "step host-staged", "step NCCL", "speedup", "all-reduce only", "algbw GB/s", "busbw GB/s");
        for (int size : sizes) {
            double staged = hostStaged(devices, size, iterations);
            double step = nccl(devices, size, iterations, true);
            double collective = nccl(devices, size, iterations, false);
            double bytes = (double) size * Float.BYTES;
            double algbw = bytes / collective;
            double busbw = algbw * 2.0 * (numDevices - 1) / numDevices;
            System.out.printf("%12d %10.2f | %13.1f us %9.1f us %8.1fx | %13.1f us %12.2f %12.2f%n", size, bytes / (1 << 20), staged / 1e3, step / 1e3, staged / step, collective / 1e3, algbw, busbw);
        }
    }
}
