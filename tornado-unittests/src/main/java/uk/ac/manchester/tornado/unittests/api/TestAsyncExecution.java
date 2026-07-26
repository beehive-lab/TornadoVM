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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@link TornadoExecutionPlan#executeAsync()}: issue a plan and get a future instead of a blocked
 * thread. On the CUDA backend the device signals completion through a host callback; other backends
 * fall back to waiting on a runtime thread, which these tests also accept - what is asserted is the
 * contract (results correct after joining, caller released before the GPU is done), not the mechanism.
 *
 * <p>How to run:
 *
 * <pre>
 * tornado-test --ea -V uk.ac.manchester.tornado.unittests.api.TestAsyncExecution
 * </pre>
 */
public class TestAsyncExecution extends TornadoTestBase {

    private static final int SIZE = 1024 * 1024;
    /** Long enough per element that the plan runs for tens of milliseconds, so the timings are unambiguous. */
    private static final int ITERATIONS = 1 << 18;
    private static final float DELTA = 1e-2f;

    public static void spin(FloatArray in, FloatArray out, float alpha) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            float value = in.get(i);
            for (int j = 0; j < ITERATIONS; j++) {
                value = value * alpha + 1.0f;
            }
            out.set(i, value);
        }
    }

    private static float expected(float in, float alpha) {
        float value = in;
        for (int j = 0; j < ITERATIONS; j++) {
            value = value * alpha + 1.0f;
        }
        return value;
    }

    private static ImmutableTaskGraph buildGraph(FloatArray in, FloatArray out) {
        return new TaskGraph("async") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("spin", TestAsyncExecution::spin, in, out, 0.5f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out) //
                .snapshot();
    }

    /** The future completes and carries the same result object shape {@code execute()} returns. */
    @Test
    public void testAsyncCompletesWithCorrectResult() throws Exception {
        FloatArray in = new FloatArray(SIZE);
        FloatArray out = new FloatArray(SIZE);
        in.init(2.0f);
        out.init(-1.0f);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(buildGraph(in, out))) {
            CompletableFuture<TornadoExecutionResult> pending = plan.executeAsync();
            TornadoExecutionResult result = pending.get(60, TimeUnit.SECONDS);
            assertTrue("the future must carry a result", result != null);
            float want = expected(2.0f, 0.5f);
            for (int i = 0; i < SIZE; i += 1024) {
                assertEquals(want, out.get(i), DELTA * Math.max(1.0f, Math.abs(want)));
            }
        }
    }

    /**
     * The point of the API: the call returns well before the device is finished, so the caller's thread
     * is free. Asserted as a ratio rather than an absolute time - the submit must take a small fraction
     * of the blocking execution it replaces.
     */
    @Test
    public void testAsyncReturnsBeforeCompletion() throws Exception {
        FloatArray in = new FloatArray(SIZE);
        FloatArray out = new FloatArray(SIZE);
        in.init(2.0f);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(buildGraph(in, out))) {
            // Warm up: the first execution pays JIT and allocation, which would dwarf everything else.
            plan.execute();

            long blockingStart = System.nanoTime();
            plan.execute();
            long blockingNanos = System.nanoTime() - blockingStart;

            long submitStart = System.nanoTime();
            CompletableFuture<TornadoExecutionResult> pending = plan.executeAsync();
            long submitNanos = System.nanoTime() - submitStart;

            pending.get(60, TimeUnit.SECONDS);
            long totalNanos = System.nanoTime() - submitStart;

            System.out.printf("  blocking execute : %.2f ms%n", blockingNanos / 1e6);
            System.out.printf("  async submit     : %.2f ms%n", submitNanos / 1e6);
            System.out.printf("  async total      : %.2f ms%n", totalNanos / 1e6);

            assertTrue(String.format("executeAsync should return well before completion, but submit took %.2f ms of %.2f ms", //
                    submitNanos / 1e6, blockingNanos / 1e6), submitNanos < blockingNanos / 2);
        }
    }

    /** Host work placed between submit and join overlaps the GPU instead of queueing behind it. */
    @Test
    public void testHostWorkOverlapsDeviceWork() throws Exception {
        FloatArray in = new FloatArray(SIZE);
        FloatArray out = new FloatArray(SIZE);
        in.init(2.0f);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(buildGraph(in, out))) {
            plan.execute(); // warm up

            long deviceOnlyStart = System.nanoTime();
            plan.execute();
            long deviceOnlyNanos = System.nanoTime() - deviceOnlyStart;

            long hostOnlyStart = System.nanoTime();
            double hostResult = hostWork();
            long hostOnlyNanos = System.nanoTime() - hostOnlyStart;

            long overlappedStart = System.nanoTime();
            CompletableFuture<TornadoExecutionResult> pending = plan.executeAsync();
            hostResult += hostWork();
            pending.get(60, TimeUnit.SECONDS);
            long overlappedNanos = System.nanoTime() - overlappedStart;

            System.out.printf("  device only      : %.2f ms%n", deviceOnlyNanos / 1e6);
            System.out.printf("  host only        : %.2f ms%n", hostOnlyNanos / 1e6);
            System.out.printf("  overlapped       : %.2f ms (sum would be %.2f ms)%n", //
                    overlappedNanos / 1e6, (deviceOnlyNanos + hostOnlyNanos) / 1e6);

            assertTrue("overlapping should beat running host and device work back to back", //
                    overlappedNanos < (deviceOnlyNanos + hostOnlyNanos));
            assertTrue("host work must actually have run", hostResult != 0.0);

            float want = expected(2.0f, 0.5f);
            for (int i = 0; i < SIZE; i += 1024) {
                assertEquals(want, out.get(i), DELTA * Math.max(1.0f, Math.abs(want)));
            }
        }
    }

    /** Several plans in flight at once: every future must complete and every result must be correct. */
    @Test
    public void testMultiplePlansInFlight() throws Exception {
        final int plans = 3;
        FloatArray[] in = new FloatArray[plans];
        FloatArray[] out = new FloatArray[plans];
        TornadoExecutionPlan[] executionPlans = new TornadoExecutionPlan[plans];
        @SuppressWarnings("unchecked")
        CompletableFuture<TornadoExecutionResult>[] pending = new CompletableFuture[plans];

        try {
            for (int p = 0; p < plans; p++) {
                in[p] = new FloatArray(SIZE);
                out[p] = new FloatArray(SIZE);
                in[p].init(p + 1.0f);
                executionPlans[p] = new TornadoExecutionPlan(buildGraph(in[p], out[p]));
            }
            for (int p = 0; p < plans; p++) {
                pending[p] = executionPlans[p].executeAsync();
            }
            CompletableFuture.allOf(pending).get(120, TimeUnit.SECONDS);

            for (int p = 0; p < plans; p++) {
                float want = expected(p + 1.0f, 0.5f);
                for (int i = 0; i < SIZE; i += 4096) {
                    assertEquals("plan " + p, want, out[p].get(i), DELTA * Math.max(1.0f, Math.abs(want)));
                }
            }
        } finally {
            for (TornadoExecutionPlan plan : executionPlans) {
                if (plan != null) {
                    plan.close();
                }
            }
        }
    }

    /** Host-side busy work of roughly the same order as the kernel, with a result to keep it alive. */
    private static double hostWork() {
        double acc = 1.0;
        for (int i = 1; i < 40_000_000; i++) {
            acc += 1.0 / i;
        }
        return acc;
    }
}
