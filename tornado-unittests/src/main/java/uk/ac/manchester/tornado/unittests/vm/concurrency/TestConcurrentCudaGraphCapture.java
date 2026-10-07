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
package uk.ac.manchester.tornado.unittests.vm.concurrency;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMMultiDeviceNotSupported;

/**
 * One thread per GPU, each with its own execution plan on its own device, capture their CUDA
 * graphs at the same time (as the ranks of a multi-GPU application do). A capture must only
 * restrict the thread that is capturing: the other threads keep allocating buffers and
 * instantiating their own graphs while it runs. Needs two or more CUDA devices.
 *
 * <p>
 * Every thread finishes executing before any closes its plan: releasing device memory synchronises
 * the device context, which is not allowed while a stream of that context is capturing, so closing a
 * plan during another thread's capture is a different matter from what this test checks.
 * </p>
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.vm.concurrency.TestConcurrentCudaGraphCapture
 * </code>
 */
public class TestConcurrentCudaGraphCapture extends TornadoTestBase {

    private static final int SIZE = 1 << 20;
    private static final int LOCAL_SIZE = 256;
    private static final int ROUNDS = 30;
    private static final int EXECUTIONS = 3;

    public static void scaleAndShift(KernelContext context, FloatArray input, FloatArray output, float shift) {
        int id = context.globalIdx;
        if (id < input.getSize()) {
            output.set(id, 2.0f * input.get(id) + shift);
        }
    }

    public static void addOne(KernelContext context, FloatArray array) {
        int id = context.globalIdx;
        if (id < array.getSize()) {
            array.set(id, array.get(id) + 1.0f);
        }
    }

    @Test
    public void testPlansCaptureOnSeveralThreadsAtOnce() throws Throwable {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
        TornadoBackend backend = getTornadoRuntime().getBackend(getTornadoRuntime().getDefaultDevice().getBackendIndex());
        final int threadsCount = backend.getNumDevices();
        if (threadsCount < 2) {
            throw new TornadoVMMultiDeviceNotSupported("This test needs at least 2 CUDA devices");
        }

        for (int round = 0; round < ROUNDS; round++) {
            CyclicBarrier start = new CyclicBarrier(threadsCount);
            CyclicBarrier finish = new CyclicBarrier(threadsCount);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread[] threads = new Thread[threadsCount];
            for (int t = 0; t < threadsCount; t++) {
                final int id = t;
                final int currentRound = round;
                threads[t] = new Thread(() -> {
                    try {
                        runPlan(id, backend.getDevice(id), currentRound, start, finish);
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                        // Release the other threads instead of leaving them waiting for this one.
                        start.reset();
                        finish.reset();
                    }
                }, "capture-" + t);
                threads[t].start();
            }
            for (Thread thread : threads) {
                thread.join();
            }
            if (failure.get() != null) {
                throw new AssertionError("round " + round + ": " + failure.get(), failure.get());
            }
        }
    }

    private static void runPlan(int id, TornadoDevice device, int round, CyclicBarrier start, CyclicBarrier finish) throws Exception {
        String name = "capture" + id + "r" + round;
        FloatArray input = new FloatArray(SIZE);
        FloatArray output = new FloatArray(SIZE);
        TaskGraph taskGraph = new TaskGraph(name) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestConcurrentCudaGraphCapture::scaleAndShift, new KernelContext(), input, output, (float) id) //
                .task("t1", TestConcurrentCudaGraphCapture::addOne, new KernelContext(), output) //
                .task("t2", TestConcurrentCudaGraphCapture::addOne, new KernelContext(), output) //
                .task("t3", TestConcurrentCudaGraphCapture::addOne, new KernelContext(), output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(LOCAL_SIZE, 1, 1);
        GridScheduler gridScheduler = new GridScheduler();
        for (String task : new String[] { "t0", "t1", "t2", "t3" }) {
            gridScheduler.addWorkerGrid(name + "." + task, worker);
        }
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withDevice(device).withGridScheduler(gridScheduler).withCUDAGraph();
            start.await();
            for (int execution = 0; execution < EXECUTIONS; execution++) {
                input.init(execution);
                plan.execute();
                for (int i = 0; i < SIZE; i += 997) {
                    assertEquals(2.0f * execution + id + 3.0f, output.get(i), 0.0f);
                }
            }
            finish.await();
        }
    }
}
