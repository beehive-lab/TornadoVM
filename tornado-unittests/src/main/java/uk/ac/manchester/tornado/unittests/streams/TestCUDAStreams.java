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
package uk.ac.manchester.tornado.unittests.streams;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Functional correctness tests for the PTX backend's CUDA-stream support. This is the single
 * correctness suite for the feature; wall-clock benefit measurements live in
 * {@link TestStreamsPerformance}.
 *
 * <p>Intra-plan concurrency is enabled <b>per execution plan</b> with
 * {@link TornadoExecutionPlan#withIntraPlanConcurrency()}: DAG-independent operations are routed to
 * separate H2D / COMPUTE-pool / D2H role streams and ordered by device events. These tests assert that
 * results are identical to single-stream execution under every dependency shape.
 *
 * <h3>Coverage</h3>
 * <ol>
 *   <li>{@link #testSequentialBaseline()} - single-stream control (concurrency off).</li>
 *   <li>{@link #testTaskDependency()} - linear dependency chain: ordering preserved across streams.</li>
 *   <li>{@link #testMixedDependencyGraph()} - diamond: two independent producers feed one consumer
 *       (the consumer must wait on producer events that may live on <em>different</em> compute streams).</li>
 *   <li>{@link #testManyIndependentUnits()} - 8 independent H2D->compute->D2H units, exercising
 *       round-robin <em>wrap-around</em> across the (default 4) COMPUTE-stream pool.</li>
 *   <li>{@link #testMultipleIterations()} - repeated execution: event-registry reset and stream reuse.</li>
 *   <li>{@link #testSingleStreamGraphReplay()} - single-stream CUDA-graph capture/replay.</li>
 *   <li>{@link #testMultiStreamGraphReplay()} - multi-stream (fork/join) CUDA-graph capture/replay.</li>
 *   <li>{@link #testDecodeLoopConcurrent()} - a GPULlama3-shaped decode loop: {@code FIRST_EXECUTION}
 *       resident weights + a serial per-token forward chain.</li>
 * </ol>
 *
 * How to run:
 * <pre>
 *   tornado-test -V uk.ac.manchester.tornado.unittests.streams.TestCUDAStreams
 * </pre>
 */
public class TestCUDAStreams extends TornadoTestBase {

    private static final int N = 52428800; // 200 MB each array
    private static final int COMPUTE_ITERATIONS = 2048;
    private static final int REPEAT_ITERATIONS = 100;
    private static final int MANY_UNITS = 8;      // > COMPUTE pool size (4) => round-robin wrap-around
    // Many-units uses a SMALL grid + heavy per-thread loop: a single kernel does not saturate the SMs,
    // so kernels on different compute streams genuinely co-reside and run concurrently on the GPU.
    private static final int MANY_UNIT_SIZE = 32 * 1024; // 128 KB/array => only a few thread blocks
    private static final int MANY_UNIT_ITERATIONS = 1 << 16;
    // The plan is executed several times: the first (cold) launch is serial (JIT + first-launch latency
    // + GPU clock ramp); genuine compute-stream overlap manifests in steady state across executions.
    private static final int MANY_UNIT_EXECUTIONS = 8;

    private static final int GRAPH_N = 8192;       // CUDA-graph capture/replay arrays
    private static final int GRAPH_REPLAYS = 8;

    private static final int LLAMA_DIM = 2048;     // GPULlama3-shaped decode loop
    private static final int LLAMA_LAYERS = 6;
    private static final int LLAMA_TOKENS = 16;
    private static final float LLAMA_DELTA = 0.05f;

    private static final float DELTA = 1e-3f;
    private static final float ALPHA = 0.5f;

    // -------------------------------------------------------------------------
    // Kernels and CPU references
    // -------------------------------------------------------------------------

    /** axpy followed by a long fused-multiply-add loop, so each kernel is non-trivial. */
    public static void computeKernel(FloatArray x, FloatArray y, FloatArray result, float alpha) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            float xi = x.get(i);
            float yi = y.get(i);
            float val = alpha * xi + yi;
            for (int j = 0; j < COMPUTE_ITERATIONS; j++) {
                val = val * alpha + yi;
            }
            result.set(i, val);
        }
    }

    private static float expectedValue(float x, float y, float alpha) {
        float val = alpha * x + y;
        for (int j = 0; j < COMPUTE_ITERATIONS; j++) {
            val = val * alpha + y;
        }
        return val;
    }

    /** Small-grid, heavy-loop variant used by {@link #testManyIndependentUnits()} so kernels overlap. */
    public static void computeSmall(FloatArray x, FloatArray y, FloatArray result, float alpha) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            float xi = x.get(i);
            float yi = y.get(i);
            float val = alpha * xi + yi;
            for (int j = 0; j < MANY_UNIT_ITERATIONS; j++) {
                val = val * alpha + yi;
            }
            result.set(i, val);
        }
    }

    private static float expectedSmall(float x, float y, float alpha) {
        float val = alpha * x + y;
        for (int j = 0; j < MANY_UNIT_ITERATIONS; j++) {
            val = val * alpha + y;
        }
        return val;
    }

    /** Plain axpy, used by the CUDA-graph tests (cheap, capture-friendly). */
    public static void axpy(FloatArray x, FloatArray y, FloatArray result, float alpha) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            result.set(i, alpha * x.get(i) + y.get(i));
        }
    }

    public static void scale(FloatArray in, FloatArray out, float alpha) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, alpha * in.get(i));
        }
    }

    private static float expectedAxpy(float x, float y, float alpha) {
        return alpha * x + y;
    }

    /**
     * GEMV with ReLU: {@code out = relu(W * x)}, {@code W} a row-major {@code dim x dim} matrix.
     * Each output element is reduced by one thread (deterministic; matches the CPU reference).
     */
    public static void matvecReLU(FloatArray w, FloatArray x, FloatArray out, int dim) {
        for (@Parallel int i = 0; i < dim; i++) {
            float acc = 0.0f;
            for (int j = 0; j < dim; j++) {
                acc += w.get(i * dim + j) * x.get(j);
            }
            out.set(i, Math.max(0.0f, acc));
        }
    }

    // -------------------------------------------------------------------------
    // Stream correctness tests
    // -------------------------------------------------------------------------

    /** 1. Baseline sequential execution without intra-plan concurrency (single stream). */
    @Test
    public void testSequentialBaseline() throws TornadoExecutionPlanException {
        FloatArray x = new FloatArray(N);
        FloatArray y = new FloatArray(N);
        FloatArray result = new FloatArray(N);
        x.init(1.0f);
        y.init(2.0f);

        TaskGraph tg = new TaskGraph("baseline")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y)
                .task("t0", TestCUDAStreams::computeKernel, x, y, result, ALPHA)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.execute();
        }

        float expected = expectedValue(1.0f, 2.0f, ALPHA);
        for (int i = 0; i < N; i++) {
            assertEquals(expected, result.get(i), DELTA);
        }
    }

    /** 2. Linear dependency chain: t1 consumes t0's output - ordering must be preserved. */
    @Test
    public void testTaskDependency() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray a = new FloatArray(N);
        FloatArray b = new FloatArray(N);
        FloatArray r1 = new FloatArray(N);
        FloatArray r2 = new FloatArray(N);
        a.init(1.0f);
        b.init(2.0f);

        TaskGraph tg = new TaskGraph("streamsDependency")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("t0", TestCUDAStreams::computeKernel, a, b, r1, ALPHA)
                .task("t1", TestCUDAStreams::computeKernel, r1, b, r2, ALPHA)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, r2);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withIntraPlanConcurrency();
            plan.execute();
        }

        float expected1 = expectedValue(1.0f, 2.0f, ALPHA);
        float expected2 = expectedValue(expected1, 2.0f, ALPHA);
        for (int i = 0; i < N; i++) {
            assertEquals(expected2, r2.get(i), DELTA);
        }
    }

    /**
     * 3. Mixed (diamond) dependency: two independent producers (t0, t1) feed one consumer (t2).
     * t0 and t1 may be round-robined onto different compute streams, so t2 must wait on both
     * producers' events cross-stream - the core correctness case for the COMPUTE stream pool.
     */
    @Test
    public void testMixedDependencyGraph() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray x = new FloatArray(N);
        FloatArray y = new FloatArray(N);
        FloatArray r0 = new FloatArray(N);
        FloatArray r1 = new FloatArray(N);
        FloatArray r2 = new FloatArray(N);
        x.init(1.0f);
        y.init(2.0f);

        TaskGraph tg = new TaskGraph("mixedGraph")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y)
                .task("t0", TestCUDAStreams::computeKernel, x, y, r0, ALPHA)
                .task("t1", TestCUDAStreams::computeKernel, x, y, r1, ALPHA)
                .task("t2", TestCUDAStreams::computeKernel, r0, r1, r2, ALPHA)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, r2);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withIntraPlanConcurrency();
            plan.execute();
        }

        float v = expectedValue(1.0f, 2.0f, ALPHA);
        float expected = expectedValue(v, v, ALPHA);
        for (int i = 0; i < N; i++) {
            assertEquals(expected, r2.get(i), DELTA);
        }
    }

    /**
     * 4. Many independent units: {@value #MANY_UNITS} independent H2D->compute->D2H pipelines. Because
     * {@value #MANY_UNITS} exceeds the default COMPUTE-pool size (4), this exercises round-robin
     * wrap-around - kernels reuse compute streams and must still resolve their dependencies correctly.
     *
     * <p>Unlike the other tests, the kernels here use a small grid ({@value #MANY_UNIT_SIZE} elements)
     * with a heavy per-thread loop, so a single kernel does <em>not</em> saturate the SMs, and the plan is
     * executed {@value #MANY_UNIT_EXECUTIONS} times so steady-state pipelining kicks in. This is the one
     * correctness test where kernels on different compute streams genuinely run <em>concurrently</em> on the
     * GPU - profiling shows up to 4 kernels in flight at once (overlap-factor ~ 2.2) - proving the pool
     * actually executes in parallel, not merely that the streams are created. Only correctness is asserted;
     * the overlap itself is a non-deterministic performance property and is therefore not gated (see
     * {@link TestStreamsPerformance}).
     */
    @Test
    public void testManyIndependentUnitsSingleStream() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray[] x = new FloatArray[MANY_UNITS];
        FloatArray[] y = new FloatArray[MANY_UNITS];
        FloatArray[] r = new FloatArray[MANY_UNITS];

        TaskGraph tg = new TaskGraph("noDepsSeq");
        for (int u = 0; u < MANY_UNITS; u++) {
            x[u] = new FloatArray(MANY_UNIT_SIZE);
            y[u] = new FloatArray(MANY_UNIT_SIZE);
            r[u] = new FloatArray(MANY_UNIT_SIZE);
            x[u].init(u + 1.0f);
            y[u].init(u + 2.0f);
            tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, x[u], y[u])
                    .task("u" + u, TestCUDAStreams::computeSmall, x[u], y[u], r[u], ALPHA)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, r[u]);
        }

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            // No intra-plan concurrency -> Single stream
            for (int it = 0; it < MANY_UNIT_EXECUTIONS; it++) {
                plan.execute();
            }
        }

        for (int u = 0; u < MANY_UNITS; u++) {
            float expected = expectedSmall(u + 1.0f, u + 2.0f, ALPHA);
            for (int i = 0; i < MANY_UNIT_SIZE; i++) {
                assertEquals(expected, r[u].get(i), DELTA);
            }
        }
    }

    @Test
    public void testManyIndependentUnitsMultiStream() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray[] x = new FloatArray[MANY_UNITS];
        FloatArray[] y = new FloatArray[MANY_UNITS];
        FloatArray[] r = new FloatArray[MANY_UNITS];

        TaskGraph tg = new TaskGraph("noDepsPar");
        for (int u = 0; u < MANY_UNITS; u++) {
            x[u] = new FloatArray(MANY_UNIT_SIZE);
            y[u] = new FloatArray(MANY_UNIT_SIZE);
            r[u] = new FloatArray(MANY_UNIT_SIZE);
            x[u].init(u + 1.0f);
            y[u].init(u + 2.0f);
            tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, x[u], y[u])
                    .task("u" + u, TestCUDAStreams::computeSmall, x[u], y[u], r[u], ALPHA)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, r[u]);
        }

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            // With intra-plan concurrency -> Multi stream
            plan.withIntraPlanConcurrency();
            for (int it = 0; it < MANY_UNIT_EXECUTIONS; it++) {
                plan.execute();
            }
        }

        for (int u = 0; u < MANY_UNITS; u++) {
            float expected = expectedSmall(u + 1.0f, u + 2.0f, ALPHA);
            for (int i = 0; i < MANY_UNIT_SIZE; i++) {
                assertEquals(expected, r[u].get(i), DELTA);
            }
        }
    }

    /** 5. Repeated execution: validates event-registry reset and safe stream reuse across iterations. */
    @Test
    public void testMultipleIterations() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray x = new FloatArray(N);
        FloatArray y = new FloatArray(N);
        FloatArray result = new FloatArray(N);
        x.init(1.0f);
        y.init(2.0f);

        TaskGraph tg = new TaskGraph("streamsMultiIter")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y)
                .task("t0", TestCUDAStreams::computeKernel, x, y, result, ALPHA)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withIntraPlanConcurrency();
            for (int i = 0; i < REPEAT_ITERATIONS; i++) {
                plan.execute();
            }
        }

        float expected = expectedValue(1.0f, 2.0f, ALPHA);
        for (int i = 0; i < N; i++) {
            assertEquals(expected, result.get(i), DELTA);
        }
    }

    // -------------------------------------------------------------------------
    // CUDA-graph capture/replay tests
    // -------------------------------------------------------------------------

    /** 6. Single-stream CUDA graph: capture once, replay many times, results stable. */
    @Test
    public void testSingleStreamGraphReplay() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray x = new FloatArray(GRAPH_N);
        FloatArray y = new FloatArray(GRAPH_N);
        FloatArray result = new FloatArray(GRAPH_N);
        x.init(1.0f);
        y.init(2.0f);

        TaskGraph tg = new TaskGraph("graphSingle")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y)
                .task("t0", TestCUDAStreams::axpy, x, y, result, ALPHA)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withCUDAGraph();
            for (int i = 0; i < GRAPH_REPLAYS; i++) {
                plan.execute();
            }
        }

        float expected = expectedAxpy(1.0f, 2.0f, ALPHA);
        for (int i = 0; i < GRAPH_N; i++) {
            assertEquals(expected, result.get(i), DELTA);
        }
    }

    /**
     * 7. Multi-stream (fork/join) CUDA-graph capture combined with intra-plan concurrency: a two-task
     * H2D->COMPUTE->COMPUTE->D2H pipeline captured across role streams and replayed many times. The
     * inputs are MUTATED before every replay and the outputs validated after each one: graph H2D copy
     * nodes must re-read their host source pointers on every launch, so each replay has to observe the
     * new input values (a stale capture would keep returning the first execution's results).
     */
    @Test
    public void testMultiStreamGraphReplay() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray x = new FloatArray(GRAPH_N);
        FloatArray y = new FloatArray(GRAPH_N);
        FloatArray tmp = new FloatArray(GRAPH_N);
        FloatArray result = new FloatArray(GRAPH_N);

        TaskGraph tg = new TaskGraph("graphMulti")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y)
                .task("t0", TestCUDAStreams::axpy, x, y, tmp, ALPHA)
                .task("t1", TestCUDAStreams::scale, tmp, result, ALPHA)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withIntraPlanConcurrency().withCUDAGraph();
            for (int i = 0; i < GRAPH_REPLAYS; i++) {
                float xValue = 1.0f + i;
                float yValue = 2.0f + i;
                x.init(xValue);
                y.init(yValue);
                plan.execute();
                float expected = ALPHA * expectedAxpy(xValue, yValue, ALPHA);
                for (int j = 0; j < GRAPH_N; j++) {
                    assertEquals(expected, result.get(j), DELTA);
                }
            }
        }
    }

    /**
     * 9. The three features combined: intra-plan concurrency + CUDA-graph capture/replay + the
     * dynamic profiler. Guards a regression where per-operation profiler bookkeeping waited on
     * events recorded into the capturing stream (illegal: CUDA_ERROR_CAPTURED_EVENT), which
     * invalidated the capture and failed every subsequent operation. Device-side per-op times are
     * legitimately not collected for graph-captured operations; this test asserts correctness.
     */
    @Test
    public void testMultiStreamGraphReplayWithProfiler() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        FloatArray x = new FloatArray(GRAPH_N);
        FloatArray y = new FloatArray(GRAPH_N);
        FloatArray tmp = new FloatArray(GRAPH_N);
        FloatArray result = new FloatArray(GRAPH_N);

        TaskGraph tg = new TaskGraph("graphMultiProfiler")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y)
                .task("t0", TestCUDAStreams::axpy, x, y, tmp, ALPHA)
                .task("t1", TestCUDAStreams::scale, tmp, result, ALPHA)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withIntraPlanConcurrency().withCUDAGraph().withProfiler(ProfilerMode.SILENT);
            for (int i = 0; i < GRAPH_REPLAYS; i++) {
                float xValue = 1.0f + i;
                float yValue = 2.0f + i;
                x.init(xValue);
                y.init(yValue);
                plan.execute();
                float expected = ALPHA * expectedAxpy(xValue, yValue, ALPHA);
                for (int j = 0; j < GRAPH_N; j++) {
                    assertEquals(expected, result.get(j), DELTA);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // GPULlama3-shaped decode loop (FIRST_EXECUTION resident weights + serial chain)
    // -------------------------------------------------------------------------

    /** 8. Decode loop with intra-plan concurrency (H2D / COMPUTE / D2H role streams). */
    @Test
    public void testDecodeLoopConcurrent() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);

        // Resident read-only weights (uploaded once, FIRST_EXECUTION) - row-stochastic (W[i][j] = 1/DIM).
        FloatArray[] weights = new FloatArray[LLAMA_LAYERS];
        FloatArray[] hidden = new FloatArray[LLAMA_LAYERS];
        for (int l = 0; l < LLAMA_LAYERS; l++) {
            weights[l] = new FloatArray(LLAMA_DIM * LLAMA_DIM);
            weights[l].init(1.0f / LLAMA_DIM);
            hidden[l] = new FloatArray(LLAMA_DIM);
        }
        FloatArray x = new FloatArray(LLAMA_DIM); // per-token activation (EVERY_EXECUTION)

        TaskGraph tg = new TaskGraph("llamaDecode");
        tg.transferToDevice(DataTransferMode.FIRST_EXECUTION, (Object[]) weights);
        tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, x);
        FloatArray prev = x;
        for (int l = 0; l < LLAMA_LAYERS; l++) {
            tg.task("layer" + l, TestCUDAStreams::matvecReLU, weights[l], prev, hidden[l], LLAMA_DIM);
            prev = hidden[l];
        }
        FloatArray logits = hidden[LLAMA_LAYERS - 1];
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, logits);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withIntraPlanConcurrency();
            for (int token = 0; token < LLAMA_TOKENS; token++) {
                float tokenValue = token + 1;
                x.init(tokenValue);
                plan.execute();
                // Row-stochastic weights + uniform input => every layer (and ReLU) preserves the value.
                for (int i = 0; i < LLAMA_DIM; i++) {
                    assertEquals(tokenValue, logits.get(i), LLAMA_DELTA);
                }
            }
        }
    }

    /**
     * Staged transfers: a large FIRST_EXECUTION read-only input routed through the
     * pinned host staging ring. A/B via {@code -Dtornado.staged.transfers=true}: with the flag
     * off this exercises the direct (whole-segment-registered) path, with it on the chunked
     * staging-ring path - including slot wrap-around (input >> ring capacity), the remainder chunk,
     * and the kernel's dependency on the LAST chunk's event. Executes twice so the second run
     * verifies the FIRST_EXECUTION buffer stays resident and correct.
     */
    @Test
    public void testStagedFirstExecutionTransfer() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        final int size = 48 * 1024 * 1024; // 192 MB: 12 chunks of 16MB over a 4-slot ring
        FloatArray weights = new FloatArray(size);
        FloatArray out = new FloatArray(size);
        // Per-index (non-uniform) values catch short, reordered or misplaced chunk copies.
        for (int i = 0; i < size; i++) {
            weights.set(i, i % 1013);
        }

        TaskGraph tg = new TaskGraph("staged")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, weights)
                .task("t0", TestCUDAStreams::scale, weights, out, 2.0f)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        ImmutableTaskGraph itg = tg.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(itg)) {
            plan.withIntraPlanConcurrency();
            for (int iteration = 0; iteration < 2; iteration++) {
                out.init(-1.0f);
                plan.execute();
                for (int i = 0; i < size; i++) {
                    assertEquals(2.0f * (i % 1013), out.get(i), DELTA);
                }
            }
        }
    }
}
