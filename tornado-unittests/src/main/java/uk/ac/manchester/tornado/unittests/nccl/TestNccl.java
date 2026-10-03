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
package uk.ac.manchester.tornado.unittests.nccl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.function.BiFunction;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.nccl.Nccl;
import uk.ac.manchester.tornado.nccl.NcclCommunicator;
import uk.ac.manchester.tornado.nccl.NcclPlanGroup;
import uk.ac.manchester.tornado.nccl.NcclRedOp;
import uk.ac.manchester.tornado.nccl.provider.NcclLibraryProvider;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;

/**
 * Unit tests for NCCL collectives as library tasks. Every CUDA device of the default backend is one
 * rank: each rank runs its own execution plan on its own device, the plans execute together through
 * {@link NcclPlanGroup}, and the collectives work on the TornadoVM device buffers directly. On a
 * single-GPU host the communicator has one rank. Skipped ([UNSUPPORTED]) unless the default device
 * is on the CUDA backend and NCCL is installed.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.nccl.TestNccl
 * </code>
 */
public class TestNccl extends TornadoTestBase {

    private static final int SIZE = 4096;
    private static final int COUNT = 1024;
    private static final int LOCAL_SIZE = 128;

    @Before
    public void ncclMustBeAvailable() {
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "NCCL library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        if (!NcclLibraryProvider.isAvailable()) {
            throw new TornadoVMCUDANotSupported("NCCL is not available on this host");
        }
    }

    public static void fillFloat(KernelContext context, FloatArray array, int rank) {
        int id = context.globalIdx;
        if (id < array.getSize()) {
            array.set(id, (rank + 1) * (id % 7 + 1));
        }
    }

    public static void fillInt(KernelContext context, IntArray array, int rank) {
        int id = context.globalIdx;
        if (id < array.getSize()) {
            array.set(id, rank * 1000 + id);
        }
    }

    public static void twice(KernelContext context, FloatArray input, FloatArray output) {
        int id = context.globalIdx;
        if (id < input.getSize()) {
            output.set(id, 2.0f * input.get(id));
        }
    }

    public static void addOne(KernelContext context, FloatArray array) {
        int id = context.globalIdx;
        if (id < array.getSize()) {
            array.set(id, array.get(id) + 1.0f);
        }
    }

    private TornadoDevice[] cudaDevices() {
        TornadoBackend backend = getTornadoRuntime().getBackend(getTornadoRuntime().getDefaultDevice().getBackendIndex());
        TornadoDevice[] devices = new TornadoDevice[backend.getNumDevices()];
        for (int i = 0; i < devices.length; i++) {
            devices[i] = backend.getDevice(i);
        }
        return devices;
    }

    private static WorkerGrid worker(int threads) {
        WorkerGrid worker = new WorkerGrid1D(threads);
        worker.setLocalWork(LOCAL_SIZE, 1, 1);
        return worker;
    }

    /**
     * Builds one plan per rank, on that rank's device, and runs all of them together {@code steps}
     * times. {@code graphOfRank} returns the task graph of a rank, named {@code "rank" + r}; every
     * kernel task in it is called {@code "k0"}, {@code "k1"}, ... and runs over {@code threads}.
     */
    private static void runRanks(TornadoDevice[] devices, int threads, int kernels, int steps, BiFunction<Integer, String, TaskGraph> graphOfRank, Runnable beforeEachStep) {
        TornadoExecutionPlan[] plans = new TornadoExecutionPlan[devices.length];
        try {
            for (int rank = 0; rank < devices.length; rank++) {
                String name = "rank" + rank;
                GridScheduler gridScheduler = new GridScheduler();
                for (int k = 0; k < kernels; k++) {
                    gridScheduler.addWorkerGrid(name + ".k" + k, worker(threads));
                }
                plans[rank] = new TornadoExecutionPlan(graphOfRank.apply(rank, name).snapshot());
                plans[rank].withDevice(devices[rank]).withGridScheduler(gridScheduler);
            }
            try (NcclPlanGroup ranks = new NcclPlanGroup(plans)) {
                for (int step = 0; step < steps; step++) {
                    beforeEachStep.run();
                    ranks.execute();
                }
            }
        } finally {
            for (TornadoExecutionPlan plan : plans) {
                if (plan != null) {
                    try {
                        plan.close();
                    } catch (Exception e) {
                        throw new TornadoRuntimeException(e);
                    }
                }
            }
        }
    }

    @Test
    public void testAllReduceInPlaceSum() {
        TornadoDevice[] devices = cudaDevices();
        int n = devices.length;
        FloatArray[] buffers = new FloatArray[n];
        try (NcclCommunicator communicator = NcclCommunicator.create(devices)) {
            runRanks(devices, SIZE, 1, 1, (rank, name) -> {
                buffers[rank] = new FloatArray(SIZE);
                return new TaskGraph(name) //
                        .task("k0", TestNccl::fillFloat, new KernelContext(), buffers[rank], rank) //
                        .libraryTask("sum", Nccl::allReduceInPlace, communicator, buffers[rank], NcclRedOp.SUM) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, buffers[rank]);
            }, () -> {
            });
        }
        float ranksSum = n * (n + 1) / 2.0f;
        for (int rank = 0; rank < n; rank++) {
            for (int i = 0; i < SIZE; i++) {
                assertEquals(ranksSum * (i % 7 + 1), buffers[rank].get(i), 0.0f);
            }
        }
    }

    @Test
    public void testAllReduceMaxInt() {
        TornadoDevice[] devices = cudaDevices();
        int n = devices.length;
        IntArray[] sends = new IntArray[n];
        IntArray[] recvs = new IntArray[n];
        try (NcclCommunicator communicator = NcclCommunicator.create(devices)) {
            runRanks(devices, SIZE, 1, 1, (rank, name) -> {
                sends[rank] = new IntArray(SIZE);
                recvs[rank] = new IntArray(SIZE);
                return new TaskGraph(name) //
                        .task("k0", TestNccl::fillInt, new KernelContext(), sends[rank], rank) //
                        .libraryTask("max", Nccl::allReduce, communicator, sends[rank], recvs[rank], NcclRedOp.MAX) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, recvs[rank]);
            }, () -> {
            });
        }
        for (int rank = 0; rank < n; rank++) {
            for (int i = 0; i < SIZE; i++) {
                assertEquals((n - 1) * 1000 + i, recvs[rank].get(i));
            }
        }
    }

    @Test
    public void testBroadcast() {
        TornadoDevice[] devices = cudaDevices();
        int n = devices.length;
        int root = n - 1;
        FloatArray[] buffers = new FloatArray[n];
        try (NcclCommunicator communicator = NcclCommunicator.create(devices)) {
            runRanks(devices, SIZE, 1, 1, (rank, name) -> {
                buffers[rank] = new FloatArray(SIZE);
                return new TaskGraph(name) //
                        .task("k0", TestNccl::fillFloat, new KernelContext(), buffers[rank], rank) //
                        .libraryTask("bcast", Nccl::broadcast, communicator, buffers[rank], root) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, buffers[rank]);
            }, () -> {
            });
        }
        for (int rank = 0; rank < n; rank++) {
            for (int i = 0; i < SIZE; i++) {
                assertEquals((root + 1) * (i % 7 + 1), buffers[rank].get(i), 0.0f);
            }
        }
    }

    @Test
    public void testReduceToRoot() {
        TornadoDevice[] devices = cudaDevices();
        int n = devices.length;
        IntArray[] sends = new IntArray[n];
        IntArray[] recvs = new IntArray[n];
        try (NcclCommunicator communicator = NcclCommunicator.create(devices)) {
            runRanks(devices, SIZE, 1, 1, (rank, name) -> {
                sends[rank] = new IntArray(SIZE);
                recvs[rank] = new IntArray(SIZE);
                return new TaskGraph(name) //
                        .task("k0", TestNccl::fillInt, new KernelContext(), sends[rank], rank) //
                        .libraryTask("reduce", Nccl::reduce, communicator, sends[rank], recvs[rank], NcclRedOp.SUM, 0) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, recvs[rank]);
            }, () -> {
            });
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals(1000 * n * (n - 1) / 2 + n * i, recvs[0].get(i));
        }
    }

    @Test
    public void testAllGather() {
        TornadoDevice[] devices = cudaDevices();
        int n = devices.length;
        IntArray[] sends = new IntArray[n];
        IntArray[] recvs = new IntArray[n];
        try (NcclCommunicator communicator = NcclCommunicator.create(devices)) {
            runRanks(devices, COUNT, 1, 1, (rank, name) -> {
                sends[rank] = new IntArray(COUNT);
                recvs[rank] = new IntArray(n * COUNT);
                return new TaskGraph(name) //
                        .task("k0", TestNccl::fillInt, new KernelContext(), sends[rank], rank) //
                        .libraryTask("gather", Nccl::allGather, communicator, sends[rank], recvs[rank]) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, recvs[rank]);
            }, () -> {
            });
        }
        for (int rank = 0; rank < n; rank++) {
            for (int from = 0; from < n; from++) {
                for (int i = 0; i < COUNT; i++) {
                    assertEquals(from * 1000 + i, recvs[rank].get(from * COUNT + i));
                }
            }
        }
    }

    @Test
    public void testReduceScatter() {
        TornadoDevice[] devices = cudaDevices();
        int n = devices.length;
        IntArray[] sends = new IntArray[n];
        IntArray[] recvs = new IntArray[n];
        try (NcclCommunicator communicator = NcclCommunicator.create(devices)) {
            runRanks(devices, n * COUNT, 1, 1, (rank, name) -> {
                sends[rank] = new IntArray(n * COUNT);
                recvs[rank] = new IntArray(COUNT);
                return new TaskGraph(name) //
                        .task("k0", TestNccl::fillInt, new KernelContext(), sends[rank], rank) //
                        .libraryTask("scatter", Nccl::reduceScatter, communicator, sends[rank], recvs[rank], NcclRedOp.SUM) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, recvs[rank]);
            }, () -> {
            });
        }
        for (int rank = 0; rank < n; rank++) {
            for (int i = 0; i < COUNT; i++) {
                assertEquals(1000 * n * (n - 1) / 2 + n * (rank * COUNT + i), recvs[rank].get(i));
            }
        }
    }

    /**
     * A kernel feeds the collective and another consumes its result, over several executions with
     * new host input each time: the collective is ordered with the kernels on the plan's stream.
     */
    @Test
    public void testKernelAverageKernelRepeated() {
        TornadoDevice[] devices = cudaDevices();
        int n = devices.length;
        final int steps = 3;
        FloatArray[] inputs = new FloatArray[n];
        FloatArray[] buffers = new FloatArray[n];
        int[] step = { -1 };
        try (NcclCommunicator communicator = NcclCommunicator.create(devices)) {
            runRanks(devices, SIZE, 2, steps, (rank, name) -> {
                inputs[rank] = new FloatArray(SIZE);
                buffers[rank] = new FloatArray(SIZE);
                return new TaskGraph(name) //
                        .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputs[rank]) //
                        .task("k0", TestNccl::twice, new KernelContext(), inputs[rank], buffers[rank]) //
                        .libraryTask("mean", Nccl::allReduceInPlace, communicator, buffers[rank], NcclRedOp.AVG) //
                        .task("k1", TestNccl::addOne, new KernelContext(), buffers[rank]) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, buffers[rank]);
            }, () -> {
                if (step[0] >= 0) {
                    checkAverage(buffers, n, step[0]);
                }
                step[0]++;
                for (int rank = 0; rank < n; rank++) {
                    for (int i = 0; i < SIZE; i++) {
                        inputs[rank].set(i, rank + step[0] + i % 3);
                    }
                }
            });
        }
        checkAverage(buffers, n, step[0]);
    }

    private static void checkAverage(FloatArray[] buffers, int n, int step) {
        for (int rank = 0; rank < n; rank++) {
            for (int i = 0; i < SIZE; i++) {
                float mean = 2.0f * ((n - 1) / 2.0f + step + i % 3);
                assertEquals(mean + 1.0f, buffers[rank].get(i), 1e-4f);
            }
        }
    }

    /**
     * Every rank sends to the next one and receives from the previous one in a single grouped task.
     * With two ranks this is an exchange in both directions; with one rank, a send to itself.
     */
    @Test
    public void testRingShiftSendRecv() {
        TornadoDevice[] devices = cudaDevices();
        int n = devices.length;
        IntArray[] sends = new IntArray[n];
        IntArray[] recvs = new IntArray[n];
        try (NcclCommunicator communicator = NcclCommunicator.create(devices)) {
            runRanks(devices, SIZE, 1, 2, (rank, name) -> {
                sends[rank] = new IntArray(SIZE);
                recvs[rank] = new IntArray(SIZE);
                return new TaskGraph(name) //
                        .task("k0", TestNccl::fillInt, new KernelContext(), sends[rank], rank) //
                        .libraryTask("shift", Nccl::sendRecv, communicator, sends[rank], (rank + 1) % n, recvs[rank], (rank - 1 + n) % n) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, recvs[rank]);
            }, () -> {
            });
        }
        for (int rank = 0; rank < n; rank++) {
            int previous = (rank - 1 + n) % n;
            for (int i = 0; i < SIZE; i++) {
                assertEquals(previous * 1000 + i, recvs[rank].get(i));
            }
        }
    }

    /**
     * A two-stage pipeline: a kernel on the first GPU produces a buffer and sends it, the second GPU
     * receives it and a kernel there consumes it, over several executions.
     */
    @Test
    public void testPipelineSendThenRecv() {
        TornadoDevice[] devices = cudaDevices();
        if (devices.length < 2) {
            throw new TornadoVMMultiDeviceNotSupported("This test needs at least 2 CUDA devices");
        }
        TornadoDevice[] stages = { devices[0], devices[1] };
        FloatArray produced = new FloatArray(SIZE);
        FloatArray received = new FloatArray(SIZE);
        try (NcclCommunicator communicator = NcclCommunicator.create(devices)) {
            runRanks(stages, SIZE, 1, 3, (rank, name) -> rank == 0 //
                    ? new TaskGraph(name) //
                            .task("k0", TestNccl::fillFloat, new KernelContext(), produced, 0) //
                            .libraryTask("send", Nccl::send, communicator, produced, 1) //
                    : new TaskGraph(name) //
                            .libraryTask("recv", Nccl::recv, communicator, received, 0) //
                            .task("k0", TestNccl::addOne, new KernelContext(), received) //
                            .transferToHost(DataTransferMode.EVERY_EXECUTION, received), () -> {
                    });
        }
        for (int i = 0; i < SIZE; i++) {
            assertEquals((i % 7 + 1) + 1.0f, received.get(i), 0.0f);
        }
    }

    @Test
    public void testPointToPointArgumentsChecked() {
        TornadoDevice[] devices = cudaDevices();
        try (NcclCommunicator communicator = NcclCommunicator.create(devices)) {
            FloatArray floats = new FloatArray(SIZE);
            assertThrows(TornadoRuntimeException.class, () -> Nccl.send(communicator, floats, devices.length));
            assertThrows(TornadoRuntimeException.class, () -> Nccl.recv(communicator, floats, -1));
            assertThrows(TornadoRuntimeException.class, () -> Nccl.sendRecv(communicator, floats, 0, new FloatArray(SIZE / 2), 0));
            assertThrows(TornadoRuntimeException.class, () -> Nccl.sendRecv(communicator, floats, 0, new IntArray(SIZE), 0));
        }
    }

    @Test
    public void testRanksMustBeDistinctDevices() {
        TornadoDevice device = cudaDevices()[0];
        assertThrows(TornadoRuntimeException.class, () -> NcclCommunicator.create(device, device));
    }

    @Test
    public void testTwoRanksOnTwoGpus() {
        TornadoDevice[] devices = cudaDevices();
        if (devices.length < 2) {
            throw new TornadoVMMultiDeviceNotSupported("This test needs at least 2 CUDA devices");
        }
        TornadoDevice[] pair = { devices[1], devices[0] };
        FloatArray[] buffers = new FloatArray[2];
        try (NcclCommunicator communicator = NcclCommunicator.create(pair)) {
            runRanks(pair, SIZE, 1, 2, (rank, name) -> {
                buffers[rank] = new FloatArray(SIZE);
                return new TaskGraph(name) //
                        .task("k0", TestNccl::fillFloat, new KernelContext(), buffers[rank], rank) //
                        .libraryTask("sum", Nccl::allReduceInPlace, communicator, buffers[rank], NcclRedOp.SUM) //
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, buffers[rank]);
            }, () -> {
            });
        }
        for (int rank = 0; rank < 2; rank++) {
            for (int i = 0; i < SIZE; i++) {
                assertEquals(3.0f * (i % 7 + 1), buffers[rank].get(i), 0.0f);
            }
        }
    }
}
