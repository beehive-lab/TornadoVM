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
import static org.junit.Assert.fail;

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
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Kernels launching kernels from the device with {@link KernelContext#launch} (CUDA Dynamic
 * Parallelism). CUDA backend only.
 *
 * <p>How to run:
 *
 * <pre>
 * tornado-test --printKernel -V uk.ac.manchester.tornado.unittests.kernelcontext.api.TestDynamicParallelism
 * </pre>
 */
public class TestDynamicParallelism extends TornadoTestBase {

    private static final DeviceKernel DOUBLE = DeviceKernel.of(TestDynamicParallelism::doubleChild);
    private static final DeviceKernel SEGMENT = DeviceKernel.of(TestDynamicParallelism::segmentChild);
    private static final DeviceKernel FILL_2D = DeviceKernel.of(TestDynamicParallelism::fill2DChild);
    private static final DeviceKernel NESTED_CHILD = DeviceKernel.of(TestDynamicParallelism::nestedChild);
    private static final DeviceKernel NESTED_GRANDCHILD = DeviceKernel.of(TestDynamicParallelism::nestedGrandchild);
    private static final DeviceKernel SUM = DeviceKernel.of(TestDynamicParallelism::sumChild);
    private static final DeviceKernel FILL = DeviceKernel.of(TestDynamicParallelism::fillChild);
    private static final DeviceKernel SCALE = DeviceKernel.of(TestDynamicParallelism::scaleChild);
    private static final DeviceKernel LOCAL_CHILD = DeviceKernel.of(TestDynamicParallelism::localChild);
    private static final DeviceKernel PING = DeviceKernel.of(TestDynamicParallelism::ping);
    private static final DeviceKernel PONG = DeviceKernel.of(TestDynamicParallelism::pong);

    // ---------------------------------------------------------------- child kernels

    private static void doubleChild(KernelContext context, IntArray a, int n) {
        int i = context.globalIdx;
        if (i < n) {
            a.set(i, a.get(i) * 2);
        }
    }

    private static void segmentChild(KernelContext context, IntArray a, int offset, int length) {
        int i = context.globalIdx;
        if (i < length) {
            a.set(offset + i, (offset + i) * 3);
        }
    }

    private static void fill2DChild(KernelContext context, IntArray out, int width, int height) {
        int x = context.globalIdx;
        int y = context.globalIdy;
        if (x < width && y < height) {
            out.set(y * width + x, y * 1000 + x);
        }
    }

    /**
     * Marks its own elements and launches the grandchild. The grandchild may start before the rest
     * of this grid has run, so the two write different arrays.
     */
    private static void nestedChild(KernelContext context, IntArray a, IntArray marks, int n) {
        int i = context.globalIdx;
        if (i < n) {
            marks.set(i, 1);
        }
        if (i == 0) {
            context.launch(NESTED_GRANDCHILD, n, 64, a, n);
        }
    }

    private static void nestedGrandchild(KernelContext context, IntArray a, int n) {
        int i = context.globalIdx;
        if (i < n) {
            a.set(i, a.get(i) * 10);
        }
    }

    /** One thread sums what the whole parent grid wrote: only correct if it runs after the parent. */
    private static void sumChild(KernelContext context, IntArray partial, IntArray result, int n) {
        if (context.globalIdx == 0) {
            int sum = 0;
            for (int i = 0; i < n; i++) {
                sum += partial.get(i);
            }
            result.set(0, sum);
        }
    }

    private static void fillChild(KernelContext context, IntArray out, int value, int n) {
        int i = context.globalIdx;
        if (i < n) {
            out.set(i, value + i);
        }
    }

    private static void scaleChild(KernelContext context, FloatArray out, FloatArray in, float factor, long offset, int n) {
        int i = context.globalIdx;
        if (i < n) {
            out.set(i, in.get(i) * factor + offset);
        }
    }

    private static void localChild(KernelContext context, int[] local, IntArray out) {
        out.set(context.globalIdx, local[context.globalIdx]);
    }

    /** Ping and pong launch each other (a launch cycle) until {@code depth} reaches zero. */
    private static void ping(KernelContext context, IntArray trace, int depth) {
        if (context.globalIdx == 0) {
            trace.set(depth, 1);
            if (depth > 0) {
                context.launch(PONG, 1, 1, trace, depth - 1);
            }
        }
    }

    private static void pong(KernelContext context, IntArray trace, int depth) {
        if (context.globalIdx == 0) {
            trace.set(depth, 2);
            if (depth > 0) {
                context.launch(PING, 1, 1, trace, depth - 1);
            }
        }
    }

    // ---------------------------------------------------------------- parent kernels

    /** A block of 2048 threads exceeds every device's limit: the launch fails and the child never runs. */
    private static void invalidLaunchParent(KernelContext context, IntArray out, int n) {
        if (context.globalIdx == 0) {
            context.launch(FILL, n, 2048, out, 5, n);
        }
    }

    private static void localArrayParent(KernelContext context, IntArray out, int n) {
        int[] local = context.allocateIntLocalArray(64);
        if (context.globalIdx == 0) {
            context.launch(LOCAL_CHILD, 64, 64, local, out);
        }
    }

    private static void singleLaunchParent(KernelContext context, IntArray a, int n) {
        if (context.globalIdx == 0) {
            context.launch(DOUBLE, n, 128, a, n);
        }
    }

    private static void perBlockParent(KernelContext context, IntArray a, int segment) {
        if (context.localIdx == 0) {
            context.launch(SEGMENT, segment, 64, a, context.groupIdx * segment, segment);
        }
    }

    private static void launch2DParent(KernelContext context, IntArray out, int width, int height) {
        if (context.globalIdx == 0) {
            context.launch3D(FILL_2D, DeviceLaunchMode.DEFAULT, width, height, 1, 16, 16, 1, out, width, height);
        }
    }

    private static void nestedParent(KernelContext context, IntArray a, IntArray marks, int n) {
        if (context.globalIdx == 0) {
            context.launch(NESTED_CHILD, n, 64, a, marks, n);
        }
    }

    private static void tailParent(KernelContext context, IntArray partial, IntArray result, int n) {
        int i = context.globalIdx;
        if (i < n) {
            partial.set(i, i + 1);
        }
        if (i == 0) {
            context.launch(SUM, DeviceLaunchMode.TAIL, 1, 1, partial, result, n);
        }
    }

    private static void fireAndForgetParent(KernelContext context, IntArray out, int n) {
        if (context.globalIdx == 0) {
            context.launch(FILL, DeviceLaunchMode.FIRE_AND_FORGET, n, 128, out, 7, n);
        }
    }

    /** Never touches {@code out} itself: only the child writes it. */
    private static void childOnlyWritesParent(KernelContext context, IntArray out, int n) {
        if (context.globalIdx == 0) {
            context.launch(FILL, n, 128, out, 100, n);
        }
    }

    private static void mixedArgumentsParent(KernelContext context, FloatArray out, FloatArray in, int n) {
        if (context.globalIdx == 0) {
            context.launch(SCALE, n, 128, out, in, 2.5f, 4L, n);
        }
    }

    // ---------------------------------------------------------------- tests

    private void assumeDynamicParallelism() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
    }

    private static GridScheduler grid(String name, int size, int local) {
        WorkerGrid worker = new WorkerGrid1D(size);
        worker.setLocalWork(local, 1, 1);
        return new GridScheduler("dp." + name, worker);
    }

    @Test
    public void testSingleLaunch() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int n = 1000;
        IntArray a = new IntArray(n);
        for (int i = 0; i < n; i++) {
            a.set(i, i);
        }
        TaskGraph taskGraph = new TaskGraph("dp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestDynamicParallelism::singleLaunchParent, new KernelContext(), a, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("t0", 32, 32)).execute();
        }
        for (int i = 0; i < n; i++) {
            assertEquals(2 * i, a.get(i));
        }
    }

    @Test
    public void testLaunchPerBlock() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int blocks = 64;
        final int segment = 300;
        IntArray a = new IntArray(blocks * segment);
        a.init(-1);
        TaskGraph taskGraph = new TaskGraph("dp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestDynamicParallelism::perBlockParent, new KernelContext(), a, segment) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("t0", blocks * 32, 32)).execute();
        }
        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(i * 3, a.get(i));
        }
    }

    @Test
    public void testLaunch2D() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int width = 70;
        final int height = 45;
        IntArray out = new IntArray(width * height);
        out.init(-1);
        TaskGraph taskGraph = new TaskGraph("dp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, out) //
                .task("t0", TestDynamicParallelism::launch2DParent, new KernelContext(), out, width, height) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("t0", 32, 32)).execute();
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                assertEquals(y * 1000 + x, out.get(y * width + x));
            }
        }
    }

    @Test
    public void testNestedLaunch() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int n = 500;
        IntArray a = new IntArray(n);
        IntArray marks = new IntArray(n);
        for (int i = 0; i < n; i++) {
            a.set(i, i);
        }
        TaskGraph taskGraph = new TaskGraph("dp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, marks) //
                .task("t0", TestDynamicParallelism::nestedParent, new KernelContext(), a, marks, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, marks);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("t0", 32, 32)).execute();
        }
        for (int i = 0; i < n; i++) {
            assertEquals(i * 10, a.get(i));
            assertEquals(1, marks.get(i));
        }
    }

    @Test
    public void testTailLaunch() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int n = 64 * 256;
        IntArray partial = new IntArray(n);
        IntArray result = new IntArray(1);
        TaskGraph taskGraph = new TaskGraph("dp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, partial, result) //
                .task("t0", TestDynamicParallelism::tailParent, new KernelContext(), partial, result, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("t0", n, 256)).execute();
        }
        assertEquals(n * (n + 1) / 2, result.get(0));
    }

    @Test
    public void testFireAndForget() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int n = 777;
        IntArray out = new IntArray(n);
        out.init(-1);
        TaskGraph taskGraph = new TaskGraph("dp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, out) //
                .task("t0", TestDynamicParallelism::fireAndForgetParent, new KernelContext(), out, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("t0", 32, 32)).execute();
        }
        for (int i = 0; i < n; i++) {
            assertEquals(7 + i, out.get(i));
        }
    }

    /**
     * The parent never reads or writes {@code out}. It must still be treated as written, or the
     * child's results would never reach the host.
     */
    @Test
    public void testChildOnlyWrites() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int n = 1024;
        IntArray out = new IntArray(n);
        out.init(-1);
        TaskGraph taskGraph = new TaskGraph("dp") //
                .task("t0", TestDynamicParallelism::childOnlyWritesParent, new KernelContext(), out, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("t0", 32, 32)).execute();
        }
        for (int i = 0; i < n; i++) {
            assertEquals(100 + i, out.get(i));
        }
    }

    @Test
    public void testMixedArguments() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int n = 300;
        FloatArray in = new FloatArray(n);
        FloatArray out = new FloatArray(n);
        for (int i = 0; i < n; i++) {
            in.set(i, i);
        }
        TaskGraph taskGraph = new TaskGraph("dp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", TestDynamicParallelism::mixedArgumentsParent, new KernelContext(), out, in, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("t0", 32, 32)).execute();
        }
        for (int i = 0; i < n; i++) {
            assertEquals(i * 2.5f + 4, out.get(i), 1e-4f);
        }
    }

    /** A second execution reuses the compiled, linked module. */
    @Test
    public void testRepeatedExecution() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int n = 256;
        IntArray a = new IntArray(n);
        a.init(1);
        TaskGraph taskGraph = new TaskGraph("dp") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestDynamicParallelism::singleLaunchParent, new KernelContext(), a, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("t0", 32, 32));
            for (int iteration = 0; iteration < 4; iteration++) {
                plan.execute();
            }
        }
        for (int i = 0; i < n; i++) {
            assertEquals(16, a.get(i));
        }
    }

    /** Kernels that launch each other: A -> B -> A -> ... */
    @Test
    public void testLaunchCycle() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int depth = 7;
        IntArray trace = new IntArray(depth + 1);
        TaskGraph taskGraph = new TaskGraph("dp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, trace) //
                .task("t0", TestDynamicParallelism::ping, new KernelContext(), trace, depth) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, trace);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("t0", 32, 32)).execute();
        }
        for (int d = depth; d >= 0; d--) {
            // ping writes 1 at the top level, pong 2 one level down, and so on.
            assertEquals((depth - d) % 2 == 0 ? 1 : 2, trace.get(d));
        }
    }

    /**
     * A launch the device rejects does not run the child and does not fail the parent: the kernel
     * completes (the error is reported on the device's stdout).
     */
    @Test
    public void testInvalidLaunchDoesNotRunChild() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int n = 64;
        IntArray out = new IntArray(n);
        out.init(-1);
        TaskGraph taskGraph = new TaskGraph("dp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, out) //
                .task("t0", TestDynamicParallelism::invalidLaunchParent, new KernelContext(), out, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("t0", 32, 32)).execute();
        }
        for (int i = 0; i < n; i++) {
            assertEquals(-1, out.get(i));
        }
    }

    /** Local memory belongs to the launching block, so it cannot be handed to a child. */
    @Test
    public void testLocalArrayArgumentRejected() {
        assumeDynamicParallelism();
        final int n = 64;
        IntArray out = new IntArray(n);
        // The kernel is sketched, and rejected, as soon as the task is added to the graph.
        try {
            TaskGraph taskGraph = new TaskGraph("dp") //
                    .task("t0", TestDynamicParallelism::localArrayParent, new KernelContext(), out, n) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                plan.withGridScheduler(grid("t0", 64, 64)).execute();
            }
        } catch (Throwable e) {
            StringBuilder messages = new StringBuilder();
            for (Throwable t = e; t != null; t = t.getCause()) {
                messages.append(t.getMessage()).append('\n');
            }
            assertTrue(messages.toString(), messages.toString().contains("local-memory array"));
            return;
        }
        fail("A local-memory array passed to KernelContext.launch must be rejected");
    }

    /** The parent is captured into a CUDA graph and replayed; its device launches run on every replay. */
    @Test
    public void testLaunchUnderCUDAGraph() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int n = 512;
        IntArray a = new IntArray(n);
        a.init(1);
        TaskGraph taskGraph = new TaskGraph("dp") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestDynamicParallelism::singleLaunchParent, new KernelContext(), a, n) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("t0", 32, 32)).withCUDAGraph();
            for (int iteration = 0; iteration < 3; iteration++) {
                plan.execute();
            }
        }
        for (int i = 0; i < n; i++) {
            assertEquals(8, a.get(i));
        }
    }

    /**
     * 8192 parent blocks each launch a child: more launches than the driver's default pending-launch
     * limit (2048) can be outstanding at once. The plan raises the limit with
     * withCUDAPendingLaunchCount, so every child runs.
     */
    @Test
    public void testPendingLaunchCountFromPlan() throws TornadoExecutionPlanException {
        assumeDynamicParallelism();
        final int blocks = 8192;
        final int segment = 300;
        IntArray a = new IntArray(blocks * segment);
        a.init(-1);
        TaskGraph taskGraph = new TaskGraph("dp") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a) //
                .task("t0", TestDynamicParallelism::perBlockParent, new KernelContext(), a, segment) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(grid("t0", blocks * 32, 32)).withCUDAPendingLaunchCount(2 * blocks).execute();
        }
        for (int i = 0; i < a.getSize(); i++) {
            assertEquals(i * 3, a.get(i));
        }
    }

    @Test
    public void testPendingLaunchCountMustBePositive() {
        TaskGraph taskGraph = new TaskGraph("dp").task("t0", TestDynamicParallelism::singleLaunchParent, new KernelContext(), new IntArray(1), 1);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withCUDAPendingLaunchCount(0);
            fail("withCUDAPendingLaunchCount(0) must be rejected");
        } catch (IllegalArgumentException | TornadoExecutionPlanException expected) {
            // rejected
        }
    }
}
