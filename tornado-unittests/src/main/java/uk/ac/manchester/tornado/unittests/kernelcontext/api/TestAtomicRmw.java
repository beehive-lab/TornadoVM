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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Read-modify-write atomics on {@link KernelContext} over a local (shared) array: {@code atomicMax},
 * {@code atomicMin}, {@code atomicExchange} and {@code atomicCAS}, lowered on the CUDA backend to
 * {@code atomicMax} / {@code atomicMin} / {@code atomicExch} / {@code atomicCAS}.
 *
 * <p>Every test puts a whole block of threads on one shared element, so a non-atomic
 * read-modify-write loses updates and the assertion fails. Before these intrinsics existed
 * {@code atomicAdd} was the only atomic operation available.
 *
 * <p>How to run:
 *
 * <pre>
 * tornado-test --printKernel -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestAtomicRmw
 * </pre>
 */
public class TestAtomicRmw extends TornadoTestBase {

    private static final int THREADS = 256;
    private static final int BLOCKS = 4;
    private static final int SIZE = THREADS * BLOCKS;

    /** Per-block maximum of the input, computed with one contended shared accumulator. */
    private static void blockMaxKernel(KernelContext ctx, IntArray in, IntArray out) {
        int[] shared = ctx.allocateIntLocalArray(1);
        if (ctx.localIdx == 0) {
            shared[0] = Integer.MIN_VALUE;
        }
        ctx.localBarrier();
        ctx.atomicMax(shared, 0, in.get(ctx.globalIdx));
        ctx.localBarrier();
        if (ctx.localIdx == 0) {
            out.set(ctx.groupIdx, shared[0]);
        }
    }

    /** Per-block minimum, same shape. */
    private static void blockMinKernel(KernelContext ctx, IntArray in, IntArray out) {
        int[] shared = ctx.allocateIntLocalArray(1);
        if (ctx.localIdx == 0) {
            shared[0] = Integer.MAX_VALUE;
        }
        ctx.localBarrier();
        ctx.atomicMin(shared, 0, in.get(ctx.globalIdx));
        ctx.localBarrier();
        if (ctx.localIdx == 0) {
            out.set(ctx.groupIdx, shared[0]);
        }
    }

    /**
     * Exactly one thread per block must win the claim. Every thread compare-and-swaps the sentinel with
     * its own lane id; the winner is the one that observes the sentinel, and it records how many threads
     * claimed to win.
     */
    private static void claimOnceKernel(KernelContext ctx, IntArray out, IntArray seen) {
        int[] shared = ctx.allocateIntLocalArray(1);
        if (ctx.localIdx == 0) {
            shared[0] = -1; // slot to claim
        }
        ctx.localBarrier();
        // Every thread records what it observed, so the host can count the winners.
        seen.set(ctx.globalIdx, ctx.atomicCAS(shared, 0, -1, ctx.localIdx));
        ctx.localBarrier();
        if (ctx.localIdx == 0) {
            out.set(ctx.groupIdx, shared[0]);
        }
    }

    /**
     * A lock-free maximum built from a compare-and-swap retry loop - the pattern that is impossible with
     * only {@code atomicAdd}. Must agree with the single-instruction {@code atomicMax}.
     */
    private static void casLoopMaxKernel(KernelContext ctx, IntArray in, IntArray out) {
        int[] shared = ctx.allocateIntLocalArray(1);
        if (ctx.localIdx == 0) {
            shared[0] = Integer.MIN_VALUE;
        }
        ctx.localBarrier();
        int candidate = in.get(ctx.globalIdx);
        int observed = shared[0];
        while (candidate > observed) {
            int previous = ctx.atomicCAS(shared, 0, observed, candidate);
            if (previous == observed) {
                break;
            }
            observed = previous;
        }
        ctx.localBarrier();
        if (ctx.localIdx == 0) {
            out.set(ctx.groupIdx, shared[0]);
        }
    }

    /** The last exchange wins, and every thread must observe some previously written lane id. */
    private static void exchangeKernel(KernelContext ctx, IntArray out, IntArray seen) {
        int[] shared = ctx.allocateIntLocalArray(1);
        if (ctx.localIdx == 0) {
            shared[0] = -1;
        }
        ctx.localBarrier();
        int previous = ctx.atomicExchange(shared, 0, ctx.localIdx);
        seen.set(ctx.globalIdx, previous);
        ctx.localBarrier();
        if (ctx.localIdx == 0) {
            out.set(ctx.groupIdx, shared[0]);
        }
    }

    private void skipUnsupportedBackends() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        assertNotBackend(TornadoVMBackendType.METAL);
        assertNotBackend(TornadoVMBackendType.PTX);
    }

    private static IntArray input() {
        IntArray in = new IntArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            // Non-monotonic, so the per-block extremes are not simply the first or last element.
            in.set(i, (i * 7919) % 10007);
        }
        return in;
    }

    private static void run(String name, IntArray in, IntArray out, IntArray extra) throws TornadoExecutionPlanException {
        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(THREADS, 1, 1);
        GridScheduler grid = new GridScheduler("atomics." + name, worker);
        TaskGraph taskGraph = new TaskGraph("atomics");
        if (in != null) {
            taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, in);
        }
        switch (name) {
            case "max" -> taskGraph.task(name, TestAtomicRmw::blockMaxKernel, context, in, out);
            case "min" -> taskGraph.task(name, TestAtomicRmw::blockMinKernel, context, in, out);
            case "claim" -> taskGraph.task(name, TestAtomicRmw::claimOnceKernel, context, out, extra);
            case "casLoop" -> taskGraph.task(name, TestAtomicRmw::casLoopMaxKernel, context, in, out);
            case "exchange" -> taskGraph.task(name, TestAtomicRmw::exchangeKernel, context, out, extra);
            default -> throw new IllegalArgumentException(name);
        }
        if (extra != null) {
            taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, out, extra);
        } else {
            taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        }
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }
    }

    @Test
    public void testAtomicMax() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();
        IntArray in = input();
        IntArray out = new IntArray(BLOCKS);
        run("max", in, out, null);
        for (int block = 0; block < BLOCKS; block++) {
            int expected = Integer.MIN_VALUE;
            for (int i = block * THREADS; i < (block + 1) * THREADS; i++) {
                expected = Math.max(expected, in.get(i));
            }
            assertEquals("block " + block, expected, out.get(block));
        }
    }

    @Test
    public void testAtomicMin() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();
        IntArray in = input();
        IntArray out = new IntArray(BLOCKS);
        run("min", in, out, null);
        for (int block = 0; block < BLOCKS; block++) {
            int expected = Integer.MAX_VALUE;
            for (int i = block * THREADS; i < (block + 1) * THREADS; i++) {
                expected = Math.min(expected, in.get(i));
            }
            assertEquals("block " + block, expected, out.get(block));
        }
    }

    @Test
    public void testAtomicCASClaimsSlotExactlyOnce() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();
        IntArray out = new IntArray(BLOCKS);
        IntArray seen = new IntArray(SIZE);
        seen.init(-2);
        run("claim", null, out, seen);
        for (int block = 0; block < BLOCKS; block++) {
            int winners = 0;
            for (int i = block * THREADS; i < (block + 1) * THREADS; i++) {
                if (seen.get(i) == -1) {
                    winners++;
                }
            }
            assertEquals("exactly one thread of block " + block + " may win the claim", 1, winners);
            assertTrue("the claimed slot must hold a lane id, but was " + out.get(block), //
                    out.get(block) >= 0 && out.get(block) < THREADS);
        }
    }

    @Test
    public void testAtomicCASRetryLoopMatchesAtomicMax() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();
        IntArray in = input();
        IntArray viaLoop = new IntArray(BLOCKS);
        IntArray viaMax = new IntArray(BLOCKS);
        run("casLoop", in, viaLoop, null);
        run("max", in, viaMax, null);
        for (int block = 0; block < BLOCKS; block++) {
            assertEquals("block " + block, viaMax.get(block), viaLoop.get(block));
        }
    }

    @Test
    public void testAtomicExchange() throws TornadoExecutionPlanException {
        skipUnsupportedBackends();
        IntArray out = new IntArray(BLOCKS);
        IntArray seen = new IntArray(SIZE);
        seen.init(-2);
        run("exchange", null, out, seen);
        for (int block = 0; block < BLOCKS; block++) {
            int winner = out.get(block);
            assertTrue("the surviving value must be a lane id of block " + block + " but was " + winner, //
                    winner >= 0 && winner < THREADS);
        }
        // Exactly one thread per block observes the sentinel; every other observes another lane's id.
        for (int block = 0; block < BLOCKS; block++) {
            int sentinelObservations = 0;
            for (int i = block * THREADS; i < (block + 1) * THREADS; i++) {
                int observed = seen.get(i);
                assertTrue("unexpected observed value " + observed, observed == -1 || (observed >= 0 && observed < THREADS));
                if (observed == -1) {
                    sentinelObservations++;
                }
            }
            assertEquals("exactly one thread of block " + block + " may see the sentinel", 1, sentinelObservations);
        }
    }
}
