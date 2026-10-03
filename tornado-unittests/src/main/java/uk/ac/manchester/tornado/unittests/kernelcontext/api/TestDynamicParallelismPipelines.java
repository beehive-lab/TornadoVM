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
package uk.ac.manchester.tornado.unittests.kernelcontext.api;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.DeviceKernel;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.DeviceLaunchMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;
import uk.ac.manchester.tornado.cublas.provider.CuBlasLibraryProvider;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Larger workloads built on kernels launched from the device (CUDA Dynamic Parallelism): recursive
 * algorithms whose shape is decided on the device, device-driven iteration, and device launches inside
 * task graphs replayed as CUDA graphs, alongside JIT-compiled and library (cuBLAS) tasks.
 *
 * <p>How to run:
 *
 * <pre>
 * tornado-test --printKernel --printBytecodes -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestDynamicParallelismPipelines
 * </pre>
 */
public class TestDynamicParallelismPipelines extends TornadoTestBase {

    // ---------------------------------------------------------------- recursive quicksort

    private static final int SORT_CUTOFF = 32;
    private static final int SORT_MAX_DEPTH = 16;

    private static final DeviceKernel QUICKSORT = DeviceKernel.of(TestDynamicParallelismPipelines::quicksort);

    /**
     * One thread partitions {@code [lo, hi)} around its middle element and launches one child per
     * side, so the recursion tree follows the data. The left side goes to the block's stream and the
     * right side is fired and forgotten: the two never touch the same elements.
     */
    private static void quicksort(KernelContext context, IntArray data, int lo, int hi, int depth) {
        if (context.globalIdx == 0) {
            // Non-short-circuit '|' on purpose: with '||', both the CUDA and the OpenCL backends
            // currently let the else branch fall through into this one (a control-flow bug
            // independent of device launches), and the parent would sort the range its children
            // are partitioning.
            if (hi - lo <= SORT_CUTOFF | depth >= SORT_MAX_DEPTH) {
                for (int i = lo + 1; i < hi; i++) {
                    int value = data.get(i);
                    int j = i - 1;
                    while (j >= lo && data.get(j) > value) {
                        data.set(j + 1, data.get(j));
                        j--;
                    }
                    data.set(j + 1, value);
                }
            } else {
                int mid = (lo + hi) >>> 1;
                int pivot = data.get(mid);
                data.set(mid, data.get(hi - 1));
                data.set(hi - 1, pivot);
                int store = lo;
                for (int i = lo; i < hi - 1; i++) {
                    int value = data.get(i);
                    if (value < pivot) {
                        data.set(i, data.get(store));
                        data.set(store, value);
                        store++;
                    }
                }
                data.set(hi - 1, data.get(store));
                data.set(store, pivot);
                context.launch(QUICKSORT, 1, 1, data, lo, store, depth + 1);
                context.launch(QUICKSORT, DeviceLaunchMode.FIRE_AND_FORGET, 1, 1, data, store + 1, hi, depth + 1);
            }
        }
    }

    // ---------------------------------------------------------------- device-driven iteration

    private static final int SEGMENT = 8;
    private static final int MAX_ITERATIONS = 10;

    private static final DeviceKernel PROPAGATE = DeviceKernel.of(TestDynamicParallelismPipelines::propagate);
    private static final DeviceKernel CONVERGED = DeviceKernel.of(TestDynamicParallelismPipelines::converged);

    /**
     * One step of min-label propagation inside segments of {@link #SEGMENT} elements: every element
     * takes the smallest label among itself and its neighbours in the same segment. Any element that
     * changes flags the step. Thread 0 tail-launches the convergence check, which therefore sees the
     * whole step.
     */
    private static void propagate(KernelContext context, IntArray labels, IntArray next, IntArray changed, IntArray result, int n, int iteration) {
        int i = context.globalIdx;
        if (i < n) {
            int label = labels.get(i);
            int best = label;
            if (i > 0 && (i - 1) / SEGMENT == i / SEGMENT) {
                best = Math.min(best, labels.get(i - 1));
            }
            if (i < n - 1 && (i + 1) / SEGMENT == i / SEGMENT) {
                best = Math.min(best, labels.get(i + 1));
            }
            next.set(i, best);
            if (best != label) {
                changed.set(iteration, 1);
            }
        }
        if (i == 0) {
            context.launch(CONVERGED, DeviceLaunchMode.TAIL, 1, 1, labels, next, changed, result, n, iteration);
        }
    }

    /** Decides on the device whether another step is needed, and launches it with the buffers swapped. */
    private static void converged(KernelContext context, IntArray labels, IntArray next, IntArray changed, IntArray result, int n, int iteration) {
        if (context.globalIdx == 0) {
            if (changed.get(iteration) == 1 && iteration + 1 < MAX_ITERATIONS) {
                context.launch(PROPAGATE, n, 128, next, labels, changed, result, n, iteration + 1);
            } else {
                result.set(0, iteration + 1);
            }
        }
    }

    // ---------------------------------------------------------------- pipeline stages

    private static final DeviceKernel SCALE_BLOCK = DeviceKernel.of(TestDynamicParallelismPipelines::scaleBlock);
    private static final DeviceKernel SCATTER = DeviceKernel.of(TestDynamicParallelismPipelines::scatterDoubled);

    private static void initialise(KernelContext context, IntArray a, int seed) {
        int i = context.globalIdx;
        a.set(i, i + seed);
    }

    /** Each parent block launches a child over its own 256 elements. */
    private static void perBlockLaunch(KernelContext context, IntArray a, IntArray b) {
        if (context.localIdx == 0) {
            context.launch(SCALE_BLOCK, 256, 64, a, b, context.groupIdx * 256);
        }
    }

    private static void scaleBlock(KernelContext context, IntArray a, IntArray b, int offset) {
        int i = offset + context.globalIdx;
        b.set(i, a.get(i) * 3);
    }

    /** Consumes what the children wrote: runs after the parent task, so after all its children. */
    private static void consume(KernelContext context, IntArray b, IntArray c) {
        int i = context.globalIdx;
        c.set(i, b.get(i) + 1);
    }

    private static void clear(KernelContext context, FloatArray out) {
        out.set(context.globalIdx, 0.0f);
    }

    /**
     * Stream compaction with a device-sized grid: one thread gathers the indices of the positive
     * entries of {@code y}, then launches exactly one child thread per entry.
     */
    private static void compactPositive(KernelContext context, FloatArray y, IntArray indices, FloatArray out, int n) {
        if (context.globalIdx == 0) {
            int count = 0;
            for (int i = 0; i < n; i++) {
                if (y.get(i) > 0.0f) {
                    indices.set(count, i);
                    count++;
                }
            }
            if (count > 0) {
                context.launch(SCATTER, count, 64, y, indices, out, count);
            }
        }
    }

    private static void scatterDoubled(KernelContext context, FloatArray y, IntArray indices, FloatArray out, int count) {
        int k = context.globalIdx;
        if (k < count) {
            int i = indices.get(k);
            out.set(i, y.get(i) * 2.0f);
        }
    }

    // ---------------------------------------------------------------- helpers

    private void assumeDynamicParallelism() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
    }

    private static WorkerGrid worker(int size, int local) {
        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(local, 1, 1);
        return worker;
    }

    // ---------------------------------------------------------------- tests

    @Test
    public void testRecursiveQuicksort() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int n = 1 << 13;
        Random random = new Random(11);
        IntArray data = new IntArray(n);
        int[] expected = new int[n];
        for (int i = 0; i < n; i++) {
            int value = random.nextInt(100_000) - 50_000;
            data.set(i, value);
            expected[i] = value;
        }
        Arrays.sort(expected);

        TaskGraph taskGraph = new TaskGraph("sort") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, data) //
                .task("qs", TestDynamicParallelismPipelines::quicksort, new KernelContext(), data, 0, n, 0) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(new GridScheduler("sort.qs", worker(1, 1))).execute();
        }
        for (int i = 0; i < n; i++) {
            assertEquals("index " + i, expected[i], data.get(i));
        }
    }

    @Test
    public void testDeviceDrivenConvergenceLoop() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int n = 1024;
        Random random = new Random(3);
        IntArray labels = new IntArray(n);
        IntArray next = new IntArray(n);
        IntArray changed = new IntArray(MAX_ITERATIONS);
        IntArray result = new IntArray(1);
        int[] host = new int[n];
        for (int i = 0; i < n; i++) {
            host[i] = random.nextInt(1000);
            labels.set(i, host[i]);
        }

        // The same algorithm on the host, counting steps until one changes nothing.
        int expectedIterations = 0;
        boolean anyChange = true;
        while (anyChange && expectedIterations < MAX_ITERATIONS) {
            anyChange = false;
            int[] stepped = new int[n];
            for (int i = 0; i < n; i++) {
                int best = host[i];
                if (i > 0 && (i - 1) / SEGMENT == i / SEGMENT) {
                    best = Math.min(best, host[i - 1]);
                }
                if (i < n - 1 && (i + 1) / SEGMENT == i / SEGMENT) {
                    best = Math.min(best, host[i + 1]);
                }
                stepped[i] = best;
                anyChange |= best != host[i];
            }
            host = stepped;
            expectedIterations++;
        }

        TaskGraph taskGraph = new TaskGraph("labels") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, labels, next, changed) //
                .task("propagate", TestDynamicParallelismPipelines::propagate, new KernelContext(), labels, next, changed, result, n, 0) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, labels, next, result);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(new GridScheduler("labels.propagate", worker(n, 128))).execute();
        }

        assertEquals(expectedIterations, result.get(0));
        // After the last step nothing changed, so both buffers hold the converged labels.
        for (int i = 0; i < n; i++) {
            assertEquals(host[i], labels.get(i));
            assertEquals(host[i], next.get(i));
        }
    }

    /** JIT kernel -> kernel with device launches -> JIT kernel, captured once and replayed as a CUDA graph. */
    @Test
    public void testJitChainUnderCUDAGraph() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int blocks = 16;
        final int n = blocks * 256;
        IntArray a = new IntArray(n);
        IntArray b = new IntArray(n);
        IntArray c = new IntArray(n);
        int[] seed = { 0 };

        TaskGraph taskGraph = new TaskGraph("chain") //
                .task("init", TestDynamicParallelismPipelines::initialise, new KernelContext(), a, 5) //
                .task("launch", TestDynamicParallelismPipelines::perBlockLaunch, new KernelContext(), a, b) //
                .task("consume", TestDynamicParallelismPipelines::consume, new KernelContext(), b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        GridScheduler grid = new GridScheduler();
        grid.addWorkerGrid("chain.init", worker(n, 256));
        grid.addWorkerGrid("chain.launch", worker(blocks * 32, 32));
        grid.addWorkerGrid("chain.consume", worker(n, 256));

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid).withCUDAGraph();
            for (int iteration = 0; iteration < 5; iteration++) {
                c.init(-1);
                plan.execute();
                for (int i = 0; i < n; i++) {
                    assertEquals("iteration " + iteration, (i + 5) * 3 + 1, c.get(i));
                }
            }
        }
    }

    /**
     * cuBLAS sgemv -> device-sized stream compaction (kernel launches from the device) -> cuBLAS sasum,
     * captured and replayed as a CUDA graph with new input on every iteration.
     */
    @Test
    public void testLibraryChainUnderCUDAGraph() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        if (!CuBlasLibraryProvider.isAvailable()) {
            throw new TornadoVMCUDANotSupported("cuBLAS is not available on this host");
        }
        final int n = 256;
        Random random = new Random(5);
        FloatArray matrix = new FloatArray(n * n);
        FloatArray x = new FloatArray(n);
        FloatArray y = new FloatArray(n);
        IntArray indices = new IntArray(n);
        FloatArray out = new FloatArray(n);
        FloatArray sum = new FloatArray(1);
        for (int i = 0; i < n * n; i++) {
            matrix.set(i, random.nextFloat() - 0.5f);
        }

        TaskGraph taskGraph = new TaskGraph("lib") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, matrix) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x) //
                .libraryTask("sgemv", CuBlas::cublasSgemv, //
                        CuBlasOperation.CUBLAS_OP_T.operation(), n, n, 1.0f, matrix, n, x, 1, 0.0f, y, 1) //
                .task("clear", TestDynamicParallelismPipelines::clear, new KernelContext(), out) //
                .task("compact", TestDynamicParallelismPipelines::compactPositive, new KernelContext(), y, indices, out, n) //
                .libraryTask("sasum", CuBlas::cublasSasum, n, out, 1, sum) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y, out, sum);
        GridScheduler grid = new GridScheduler();
        grid.addWorkerGrid("lib.clear", worker(n, 64));
        grid.addWorkerGrid("lib.compact", worker(32, 32));

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid).withCUDAGraph();
            for (int iteration = 0; iteration < 4; iteration++) {
                for (int i = 0; i < n; i++) {
                    x.set(i, random.nextFloat() - 0.5f);
                }
                plan.execute();

                double expected = 0;
                for (int col = 0; col < n; col++) {
                    double dot = 0;
                    for (int row = 0; row < n; row++) {
                        dot += matrix.get(col * n + row) * x.get(row);
                    }
                    assertEquals("y[" + col + "] at iteration " + iteration, dot, y.get(col), 1e-3);
                    float scattered = out.get(col);
                    assertEquals("out[" + col + "] at iteration " + iteration, y.get(col) > 0 ? 2 * y.get(col) : 0.0f, scattered, 1e-4f);
                    if (y.get(col) > 0) {
                        expected += 2 * y.get(col);
                    }
                }
                assertEquals("sum at iteration " + iteration, expected, sum.get(0), 1e-2);
            }
        }
    }
}
