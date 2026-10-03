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

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

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
import uk.ac.manchester.tornado.nccl.provider.NcclNativeLib;

/**
 * Benchmark: a ring exchange between the GPUs of the CUDA backend, the communication of a halo
 * exchange or of pipeline parallelism. Each rank runs a kernel that produces a buffer, sends it to
 * the next rank while receiving the buffer of the previous rank, and runs a kernel on what it
 * received. Two ways to exchange:
 * <ul>
 * <li><b>host-staged</b>: what TornadoVM offers without NCCL. Each rank copies its buffer to the
 * host, the host hands every buffer on to the next rank, and each rank copies its new buffer
 * back.</li>
 * <li><b>NCCL</b>: a {@code sendRecv} library task between the two kernels; the data goes from GPU
 * to GPU.</li>
 * </ul>
 * Both variants run the same two kernels and are checked. Times are medians of the step, after
 * warm-up; the bandwidth is the bytes each rank sends per second, computed from a plan that holds
 * the exchange alone (the {@code sendrecv_perf} convention of nccl-tests).
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.BenchmarkNcclSendRecv [elements,elements,...] [iterations]
 * </code>
 */
public class BenchmarkNcclSendRecv {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int LOCAL_SIZE = 256;

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

    /** What rank {@code rank} holds after the step: the buffer of the previous rank, plus one. */
    private static void check(String variant, FloatArray array, int rank, int ranks) {
        float value = ((rank - 1 + ranks) % ranks) + 1.0f;
        for (int i = 0; i < array.getSize(); i++) {
            if (array.get(i) != value + i % 13 + 1.0f) {
                throw new TornadoRuntimeException("[ERROR] " + variant + ": rank " + rank + " element " + i + " is " + array.get(i) + ", expected " + (value + i % 13 + 1.0f));
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
            try {
                plan.close();
            } catch (TornadoExecutionPlanException e) {
                throw new TornadoRuntimeException(e);
            }
        }
    }

    /** Median nanoseconds of one step with NCCL moving the buffers; without kernels, the exchange alone. */
    private static double nccl(TornadoDevice[] devices, int size, int iterations, boolean withKernels) {
        int ranks = devices.length;
        FloatArray[] sends = new FloatArray[ranks];
        FloatArray[] recvs = new FloatArray[ranks];
        TornadoExecutionPlan[] plans = new TornadoExecutionPlan[ranks];
        long[] times = new long[iterations];
        try (NcclCommunicator communicator = NcclCommunicator.create(devices)) {
            for (int rank = 0; rank < ranks; rank++) {
                String name = "nccl" + rank;
                sends[rank] = new FloatArray(size);
                recvs[rank] = new FloatArray(size);
                TaskGraph graph = new TaskGraph(name);
                if (withKernels) {
                    graph.task("produce", BenchmarkNcclSendRecv::produce, new KernelContext(), sends[rank], rank + 1.0f);
                }
                graph.libraryTask("shift", Nccl::sendRecv, communicator, sends[rank], (rank + 1) % ranks, recvs[rank], (rank - 1 + ranks) % ranks);
                if (withKernels) {
                    graph.task("consume", BenchmarkNcclSendRecv::consume, new KernelContext(), recvs[rank]);
                }
                graph.transferToHost(DataTransferMode.UNDER_DEMAND, recvs[rank]);
                plans[rank] = new TornadoExecutionPlan(graph.snapshot());
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
                    results[rank].transferToHost(recvs[rank]);
                    check("NCCL", recvs[rank], rank, ranks);
                }
            }
        } finally {
            closeAll(plans);
        }
        return median(times);
    }

    /** Median nanoseconds of one step with every buffer handed on through the host. */
    private static double hostStaged(TornadoDevice[] devices, int size, int iterations) {
        int ranks = devices.length;
        FloatArray[] sends = new FloatArray[ranks];
        FloatArray[] recvs = new FloatArray[ranks];
        TornadoExecutionPlan[] producers = new TornadoExecutionPlan[ranks];
        TornadoExecutionPlan[] consumers = new TornadoExecutionPlan[ranks];
        long[] times = new long[iterations];
        try {
            for (int rank = 0; rank < ranks; rank++) {
                sends[rank] = new FloatArray(size);
                recvs[rank] = new FloatArray(size);
                String producer = "producer" + rank;
                TaskGraph produceGraph = new TaskGraph(producer) //
                        .task("produce", BenchmarkNcclSendRecv::produce, new KernelContext(), sends[rank], rank + 1.0f) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, sends[rank]);
                producers[rank] = new TornadoExecutionPlan(produceGraph.snapshot());
                producers[rank].withDevice(devices[rank]).withGridScheduler(grid(producer, size, "produce"));

                String consumer = "consumer" + rank;
                TaskGraph consumeGraph = new TaskGraph(consumer) //
                        .transferToDevice(DataTransferMode.EVERY_EXECUTION, recvs[rank]) //
                        .task("consume", BenchmarkNcclSendRecv::consume, new KernelContext(), recvs[rank]) //
                        .transferToHost(DataTransferMode.UNDER_DEMAND, recvs[rank]);
                consumers[rank] = new TornadoExecutionPlan(consumeGraph.snapshot());
                consumers[rank].withDevice(devices[rank]).withGridScheduler(grid(consumer, size, "consume"));
            }
            try (NcclPlanGroup produce = new NcclPlanGroup(producers); NcclPlanGroup consume = new NcclPlanGroup(consumers)) {
                TornadoExecutionResult[] results = null;
                for (int i = 0; i < WARMUP_ITERATIONS + iterations; i++) {
                    long start = System.nanoTime();
                    produce.execute();
                    for (int rank = 0; rank < ranks; rank++) {
                        MemorySegment from = sends[(rank - 1 + ranks) % ranks].getSegment();
                        recvs[rank].getSegment().copyFrom(from);
                    }
                    results = consume.execute();
                    if (i >= WARMUP_ITERATIONS) {
                        times[i - WARMUP_ITERATIONS] = System.nanoTime() - start;
                    }
                }
                for (int rank = 0; rank < ranks; rank++) {
                    results[rank].transferToHost(recvs[rank]);
                    check("host-staged", recvs[rank], rank, ranks);
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
        System.out.printf("NCCL %d.%d.%d, %d rank(s) in a ring: %s, %d iterations (median)%n", version / 10000, (version / 100) % 100, version % 100, numDevices, names, iterations);
        System.out.printf("%12s %10s | %16s %12s %9s | %16s %14s%n", "elements", "MiB/rank", "step host-staged", "step NCCL", "speedup", "exchange only", "GB/s per rank");
        for (int size : sizes) {
            double staged = hostStaged(devices, size, iterations);
            double step = nccl(devices, size, iterations, true);
            double exchange = nccl(devices, size, iterations, false);
            double bytes = (double) size * Float.BYTES;
            System.out.printf("%12d %10.2f | %13.1f us %9.1f us %8.1fx | %13.1f us %14.2f%n", size, bytes / (1 << 20), staged / 1e3, step / 1e3, staged / step, exchange / 1e3, bytes / exchange);
        }
    }
}
