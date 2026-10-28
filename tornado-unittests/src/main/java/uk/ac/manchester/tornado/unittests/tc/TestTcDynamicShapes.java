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
package uk.ac.manchester.tornado.unittests.tc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Kernels run over arrays of many sizes, one plan per size with {@code withDynamicShapes()}, as an
 * application with changing shapes does. A kernel that reads no array length shares one TC
 * compilation across the sizes; kernels
 * that read the length of an input or of an array they only write must still see each size.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tc.TestTcDynamicShapes
 * </code>
 */
public class TestTcDynamicShapes extends TornadoTestBase {

    private static final int LOCAL = 64;
    private static final int[] SIZES = { 100, 300, 1000, 37, 2048, 64 };

    /** Reads no array length: the size arrives as a scalar. */
    public static void scale(KernelContext context, FloatArray in, FloatArray out, int n, float factor) {
        int i = context.globalIdx;
        if (i < n) {
            out.set(i, in.get(i) * factor + n);
        }
    }

    /** Reads the length of an array it only writes. */
    public static void fillWithLength(KernelContext context, IntArray out, int n) {
        int i = context.globalIdx;
        if (i < n) {
            out.set(i, out.getSize());
        }
    }

    /** Reads the length of an input. */
    public static void last(KernelContext context, FloatArray in, FloatArray out, int n) {
        int i = context.globalIdx;
        if (i < n) {
            out.set(i, in.get(in.getSize() - 1) + i);
        }
    }

    private static void run(TaskGraph graph, String task, int n) throws TornadoExecutionPlanException {
        WorkerGrid grid = new WorkerGrid1D((n + LOCAL - 1) / LOCAL * LOCAL);
        grid.setLocalWork(LOCAL, 1, 1);
        GridScheduler scheduler = new GridScheduler(graph.getTaskGraphName() + "." + task, grid);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withDynamicShapes().withGridScheduler(scheduler).execute();
        }
    }

    @Test
    public void testKernelWithoutLengthsOverManySizes() throws TornadoExecutionPlanException {
        for (int k = 0; k < SIZES.length; k++) {
            int n = SIZES[k];
            float factor = 1.5f + k;
            FloatArray in = new FloatArray(n);
            FloatArray out = new FloatArray(n);
            for (int i = 0; i < n; i++) {
                in.set(i, i * 0.25f);
            }
            KernelContext context = new KernelContext();
            run(new TaskGraph("shared" + k) //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                    .task("t0", TestTcDynamicShapes::scale, context, in, out, n, factor) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, out), "t0", n);
            for (int i = 0; i < n; i++) {
                assertEquals(i * 0.25f * factor + n, out.get(i), 1e-3f, "size " + n + ", index " + i);
            }
        }
    }

    @Test
    public void testKernelReadingTheLengthOfAWrittenArray() throws TornadoExecutionPlanException {
        for (int k = 0; k < SIZES.length; k++) {
            int n = SIZES[k];
            IntArray out = new IntArray(n);
            KernelContext context = new KernelContext();
            run(new TaskGraph("written" + k) //
                    .task("t0", TestTcDynamicShapes::fillWithLength, context, out, n) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, out), "t0", n);
            for (int i = 0; i < n; i++) {
                assertEquals(n, out.get(i), "size " + n + ", index " + i);
            }
        }
    }

    @Test
    public void testKernelReadingTheLengthOfAnInput() throws TornadoExecutionPlanException {
        for (int k = 0; k < SIZES.length; k++) {
            int n = SIZES[k];
            FloatArray in = new FloatArray(n);
            FloatArray out = new FloatArray(n);
            for (int i = 0; i < n; i++) {
                in.set(i, i);
            }
            KernelContext context = new KernelContext();
            run(new TaskGraph("input" + k) //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                    .task("t0", TestTcDynamicShapes::last, context, in, out, n) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, out), "t0", n);
            for (int i = 0; i < n; i++) {
                assertEquals(n - 1 + i, out.get(i), 1e-3f, "size " + n + ", index " + i);
            }
        }
    }
}
