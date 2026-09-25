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
package uk.ac.manchester.tornado.unittests.math;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * A math intrinsic whose argument the compiler can see is a constant reaches the canonicaliser's
 * constant fold. Every intrinsic the backends can generate code for has to survive that path: one
 * that cannot be folded must decline to fold and be emitted as a call, not abort the compilation.
 *
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.math.TestConstantFoldedMath
 * </code>
 */
public class TestConstantFoldedMath extends TornadoTestBase {

    private static final int SIZE = 256;

    /** The angle an FFT stage of span 2 computes for every one of its butterflies. */
    private static final float ZERO_ANGLE = -2f * (float) Math.PI * 0 / 2;

    private static final float ANGLE = 0.7f;

    public static void constCos(KernelContext context, FloatArray out) {
        out.set(context.globalIdx, TornadoMath.cos(ZERO_ANGLE));
    }

    public static void constSin(KernelContext context, FloatArray out) {
        out.set(context.globalIdx, TornadoMath.sin(ZERO_ANGLE));
    }

    public static void constTan(KernelContext context, FloatArray out) {
        out.set(context.globalIdx, TornadoMath.tan(ANGLE));
    }

    public static void constAtan(KernelContext context, FloatArray out) {
        out.set(context.globalIdx, TornadoMath.atan(ANGLE));
    }

    public static void constTanh(KernelContext context, FloatArray out) {
        out.set(context.globalIdx, TornadoMath.tanh(ANGLE));
    }

    public static void constCeil(KernelContext context, FloatArray out) {
        out.set(context.globalIdx, TornadoMath.ceil(ANGLE));
    }

    public static void constSqrt(KernelContext context, FloatArray out) {
        out.set(context.globalIdx, TornadoMath.sqrt(4.0f));
    }

    public static void constExp(KernelContext context, FloatArray out) {
        out.set(context.globalIdx, TornadoMath.exp(1.0f));
    }

    private void assertConstantFolds(String name, Task2<KernelContext, FloatArray> kernel, float expected) throws TornadoExecutionPlanException {
        FloatArray out = new FloatArray(SIZE);
        out.init(0.0f);

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setGlobalWork(SIZE, 1, 1);
        worker.setLocalWork(64, 1, 1);
        GridScheduler gridScheduler = new GridScheduler(name + ".t", worker);

        TaskGraph taskGraph = new TaskGraph(name) //
                .task("t", kernel, context, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(expected, out.get(i), 0.001f);
        }
    }

    @Test
    public void testConstantCos() throws TornadoExecutionPlanException {
        assertConstantFolds("constCos", TestConstantFoldedMath::constCos, (float) Math.cos(ZERO_ANGLE));
    }

    @Test
    public void testConstantSin() throws TornadoExecutionPlanException {
        assertConstantFolds("constSin", TestConstantFoldedMath::constSin, (float) Math.sin(ZERO_ANGLE));
    }

    @Test
    public void testConstantTan() throws TornadoExecutionPlanException {
        assertConstantFolds("constTan", TestConstantFoldedMath::constTan, (float) Math.tan(ANGLE));
    }

    @Test
    public void testConstantAtan() throws TornadoExecutionPlanException {
        assertConstantFolds("constAtan", TestConstantFoldedMath::constAtan, (float) Math.atan(ANGLE));
    }

    @Test
    public void testConstantTanh() throws TornadoExecutionPlanException {
        assertConstantFolds("constTanh", TestConstantFoldedMath::constTanh, (float) Math.tanh(ANGLE));
    }

    @Test
    public void testConstantCeil() throws TornadoExecutionPlanException {
        assertConstantFolds("constCeil", TestConstantFoldedMath::constCeil, (float) Math.ceil(ANGLE));
    }

    /** SQRT is already one of the operations the fold implements: the control for the above. */
    @Test
    public void testConstantSqrt() throws TornadoExecutionPlanException {
        assertConstantFolds("constSqrt", TestConstantFoldedMath::constSqrt, (float) Math.sqrt(4.0f));
    }

    @Test
    public void testConstantExp() throws TornadoExecutionPlanException {
        assertConstantFolds("constExp", TestConstantFoldedMath::constExp, (float) Math.exp(1.0f));
    }
}
