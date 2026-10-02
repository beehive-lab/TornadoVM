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
package uk.ac.manchester.tornado.unittests.tasks;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Tasks that receive the same array object in more than one parameter. Aliasing arguments is legal
 * Java, so the task graph must compile and run them like any other task.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestAliasedTaskArguments
 * </code>
 */
public class TestAliasedTaskArguments extends TornadoTestBase {

    private static final int N = 1024;

    /** Writes both outputs; when they alias, the second write wins. */
    public static void twoOutputsKernel(KernelContext context, FloatArray in, FloatArray first, FloatArray result, FloatArray second, int n) {
        int i = context.globalIdx;
        if (i < n) {
            first.set(i, in.get(i) + 1.0f);
            result.set(i, in.get(i) * 3.0f);
            second.set(i, in.get(i) * 2.0f);
        }
    }

    public static void twoOutputs(FloatArray in, FloatArray first, FloatArray second) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            first.set(i, in.get(i) + 1.0f);
            second.set(i, in.get(i) * 2.0f);
        }
    }

    public static void addInPlace(FloatArray a, FloatArray b) {
        for (@Parallel int i = 0; i < a.getSize(); i++) {
            b.set(i, a.get(i) + b.get(i));
        }
    }

    public static void incrementFrom(FloatArray out, FloatArray in) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, in.get(i) + 1.0f);
        }
    }

    public static void copy(FloatArray in, FloatArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i));
        }
    }

    private static FloatArray ramp() {
        FloatArray in = new FloatArray(N);
        for (int i = 0; i < N; i++) {
            in.set(i, i);
        }
        return in;
    }

    /** The case reported with jstack: a KernelContext task whose 2nd and 4th arrays are the same object. */
    @Test
    public void testKernelContextAliasedOutputs() throws TornadoExecutionPlanException {
        FloatArray in = ramp();
        FloatArray dummy = new FloatArray(N);
        FloatArray result = new FloatArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestAliasedTaskArguments::twoOutputsKernel, new KernelContext(), in, dummy, result, dummy, N) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result, dummy);
        WorkerGrid1D grid = new WorkerGrid1D(N);
        grid.setLocalWork(64, 1, 1);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(new GridScheduler("s0.t0", grid)).execute();
        }
        for (int i = 0; i < N; i++) {
            assertEquals(i * 3.0f, result.get(i), 0.0f);
            assertEquals(i * 2.0f, dummy.get(i), 0.0f);
        }
    }

    @Test
    public void testParallelAliasedOutputs() throws TornadoExecutionPlanException {
        FloatArray in = ramp();
        FloatArray out = new FloatArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestAliasedTaskArguments::twoOutputs, in, out, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < N; i++) {
            assertEquals(i * 2.0f, out.get(i), 0.0f);
        }
    }

    /** The same array as a read and a written argument. */
    @Test
    public void testAliasedInputAndOutput() throws TornadoExecutionPlanException {
        FloatArray a = ramp();
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestAliasedTaskArguments::addInPlace, a, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < N; i++) {
            assertEquals(2.0f * i, a.get(i), 0.0f);
        }
    }

    /** The same array as a written argument followed by a read argument. */
    @Test
    public void testAliasedOutputThenInput() throws TornadoExecutionPlanException {
        FloatArray a = ramp();
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t0", TestAliasedTaskArguments::incrementFrom, a, a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < N; i++) {
            assertEquals(i + 1.0f, a.get(i), 0.0f);
        }
    }

    /** Aliased outputs of one task consumed by the next task in the graph. */
    @Test
    public void testAliasedOutputsFeedingAnotherTask() throws TornadoExecutionPlanException {
        FloatArray in = ramp();
        FloatArray tmp = new FloatArray(N);
        FloatArray out = new FloatArray(N);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .task("t0", TestAliasedTaskArguments::twoOutputs, in, tmp, tmp) //
                .task("t1", TestAliasedTaskArguments::copy, tmp, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < N; i++) {
            assertEquals(i * 2.0f, out.get(i), 0.0f);
        }
    }
}
