/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.unittests.loops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoFunctions;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Loops that leave through a {@code break} carrying a value. The value of the
 * variable after the loop depends on which exit was taken: the one assigned
 * at the {@code break} on a hit, the natural-exit value on a miss. Each row is
 * a hit or a miss depending on the input, so a backend that runs the
 * natural-exit assignment on the {@code break} path too (or never) fails.
 *
 * <p>
 * The trip count is read from device memory: a scalar kernel argument is
 * constant-folded and the loop is then fully unrolled, hiding the exits.
 *
 * <p>
 * Runs on the Metal backend, whose code generator is covered here.
 *
 * <p>
 * How to run:
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.loops.TestLoopBreakValue
 * </pre>
 */
public class TestLoopBreakValue extends TornadoTestBase {

    private static final int ROWS = 256;
    private static final int COLS = 16;
    private static final int LOCAL_SIZE = 64;

    /** Natural-exit value is loop invariant ({@code -1}). */
    public static void findFirst(KernelContext context, FloatArray in, FloatArray out, IntArray dims) {
        int id = context.globalIdx;
        int n = dims.get(0);
        if (id < dims.get(1)) {
            float found = -1f;
            for (int p = 0; p < n; p++) {
                if (in.get(id * n + p) > 0.5f) {
                    found = p * 3;
                    break;
                }
            }
            out.set(id, found);
        }
    }

    /** Natural-exit value is loop variant (the value carried by the last iteration). */
    public static void findFirstOrLast(KernelContext context, FloatArray in, FloatArray out, IntArray dims) {
        int id = context.globalIdx;
        int n = dims.get(0);
        if (id < dims.get(1)) {
            float found = -1f;
            for (int p = 0; p < n; p++) {
                if (in.get(id * n + p) > 0.5f) {
                    found = p * 3;
                    break;
                }
                found = p;
            }
            out.set(id, found);
        }
    }

    /** Integer result, two different {@code break} sites and a natural exit. */
    public static void classify(KernelContext context, IntArray in, IntArray out, IntArray dims) {
        int id = context.globalIdx;
        int n = dims.get(0);
        if (id < dims.get(1)) {
            int result = 1000;
            for (int p = 0; p < n; p++) {
                int v = in.get(id * n + p);
                if (v > 100) {
                    result = p;
                    break;
                }
                if (v < -100) {
                    result = -p - 1;
                    break;
                }
            }
            out.set(id, result);
        }
    }

    private void assumeMetal() {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.CUDA);
    }

    private static float[] referenceFindFirst(FloatArray in, boolean lastOnMiss) {
        float[] expected = new float[ROWS];
        for (int id = 0; id < ROWS; id++) {
            float found = -1f;
            for (int p = 0; p < COLS; p++) {
                if (in.get(id * COLS + p) > 0.5f) {
                    found = p * 3;
                    break;
                }
                if (lastOnMiss) {
                    found = p;
                }
            }
            expected[id] = found;
        }
        return expected;
    }

    private static FloatArray floatInput(int hitEvery) {
        FloatArray in = new FloatArray(ROWS * COLS);
        in.init(0.0f);
        for (int id = 0; id < ROWS; id++) {
            if (hitEvery > 0 && id % hitEvery == 0) {
                // First hit at a row-dependent column, later hits must be ignored.
                int p = (id / hitEvery) % COLS;
                in.set(id * COLS + p, 1.0f);
                in.set(id * COLS + COLS - 1, 1.0f);
            }
        }
        return in;
    }

    private static IntArray dims() {
        return IntArray.fromElements(COLS, ROWS);
    }

    private static GridScheduler grid(String taskName) {
        WorkerGrid worker = new WorkerGrid1D(ROWS);
        worker.setLocalWork(LOCAL_SIZE, 1, 1);
        return new GridScheduler("s0." + taskName, worker);
    }

    private static FloatArray runFloat(TornadoFunctions.Task4<KernelContext, FloatArray, FloatArray, IntArray> kernel, FloatArray in) throws TornadoExecutionPlanException {
        FloatArray out = new FloatArray(ROWS);
        out.init(-7.0f);
        IntArray dims = dims();
        KernelContext context = new KernelContext();
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, dims, out) //
                .task("t0", kernel, context, in, out, dims) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(immutableTaskGraph)) {
            plan.withGridScheduler(grid("t0")).execute();
        }
        return out;
    }

    private static void check(float[] expected, FloatArray actual) {
        for (int id = 0; id < ROWS; id++) {
            assertEquals(expected[id], actual.get(id), 0.0f, "row " + id);
        }
    }

    @Test
    public void testBreakValueHit() throws TornadoExecutionPlanException {
        assumeMetal();
        FloatArray in = floatInput(1);
        check(referenceFindFirst(in, false), runFloat(TestLoopBreakValue::findFirst, in));
    }

    @Test
    public void testBreakValueMiss() throws TornadoExecutionPlanException {
        assumeMetal();
        FloatArray in = floatInput(0);
        check(referenceFindFirst(in, false), runFloat(TestLoopBreakValue::findFirst, in));
    }

    @Test
    public void testBreakValueMixed() throws TornadoExecutionPlanException {
        assumeMetal();
        FloatArray in = floatInput(3);
        check(referenceFindFirst(in, false), runFloat(TestLoopBreakValue::findFirst, in));
    }

    @Test
    public void testBreakValueLoopVariantExit() throws TornadoExecutionPlanException {
        assumeMetal();
        FloatArray in = floatInput(3);
        check(referenceFindFirst(in, true), runFloat(TestLoopBreakValue::findFirstOrLast, in));
    }

    @Test
    public void testBreakValueTwoBreaks() throws TornadoExecutionPlanException {
        assumeMetal();
        IntArray in = new IntArray(ROWS * COLS);
        in.init(0);
        int[] expected = new int[ROWS];
        for (int id = 0; id < ROWS; id++) {
            int p = id % COLS;
            switch (id % 3) {
                case 0 -> {
                    in.set(id * COLS + p, 500);
                    expected[id] = p;
                }
                case 1 -> {
                    in.set(id * COLS + p, -500);
                    expected[id] = -p - 1;
                }
                default -> expected[id] = 1000;
            }
        }

        IntArray out = new IntArray(ROWS);
        out.init(-7);
        IntArray dims = dims();
        KernelContext context = new KernelContext();
        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in, dims, out) //
                .task("t0", TestLoopBreakValue::classify, context, in, out, dims) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(immutableTaskGraph)) {
            plan.withGridScheduler(grid("t0")).execute();
        }

        for (int id = 0; id < ROWS; id++) {
            assertEquals(expected[id], out.get(id), "row " + id);
        }
    }
}
