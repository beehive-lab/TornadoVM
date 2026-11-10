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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@code TornadoExecutionPlan.uploadWithNextExecution}: an input transferred on the first execution
 * only, which the host changes later, is uploaded again by the next execution once the plan is told.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestUploadWithNextExecution
 * </code>
 */
public class TestUploadWithNextExecution extends TornadoTestBase {

    private static final int SIZE = 1024;

    /** out = in * parameters[0] + parameters[1]. */
    public static void scale(KernelContext context, FloatArray in, FloatArray parameters, FloatArray out) {
        int i = context.globalIdx;
        out.set(i, in.get(i) * parameters.get(0) + parameters.get(1));
    }

    /** data = data + parameters[0], in place. */
    public static void add(KernelContext context, FloatArray data, FloatArray parameters) {
        int i = context.globalIdx;
        data.set(i, data.get(i) + parameters.get(0));
    }

    private static FloatArray ramp() {
        FloatArray in = new FloatArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            in.set(i, i);
        }
        return in;
    }

    private static void assertScaled(FloatArray out, float factor, float offset, String when) {
        for (int i = 0; i < SIZE; i++) {
            assertEquals(i * factor + offset, out.get(i), 0.0f, "out[" + i + "] " + when);
        }
    }

    private static TaskGraph scaleGraph(int parametersMode, FloatArray in, FloatArray parameters, FloatArray out) {
        return new TaskGraph("s") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in) //
                .transferToDevice(parametersMode, parameters) //
                .task("scale", TestUploadWithNextExecution::scale, new KernelContext(), in, parameters, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
    }

    private static GridScheduler grid(String... tasks) {
        GridScheduler scheduler = new GridScheduler();
        for (String task : tasks) {
            scheduler.addWorkerGrid(task, new WorkerGrid1D(SIZE));
        }
        return scheduler;
    }

    /** The host's changes reach the device only in the executions that follow a call. */
    @Test
    public void testUploadedByTheNextExecutionOnly() throws TornadoExecutionPlanException {
        FloatArray in = ramp();
        FloatArray parameters = FloatArray.fromElements(2f, 1f);
        FloatArray out = new FloatArray(SIZE);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(scaleGraph(DataTransferMode.FIRST_EXECUTION, in, parameters, out).snapshot())) {
            plan.withGridScheduler(grid("s.scale"));
            plan.execute();
            assertScaled(out, 2f, 1f, "after the first execution");

            parameters.set(0, 3f);
            plan.execute();
            assertScaled(out, 2f, 1f, "after a host change the plan was not told about");

            plan.uploadWithNextExecution(parameters);
            parameters.set(1, 5f); // read when the plan executes, not when it is told
            plan.execute();
            assertScaled(out, 3f, 5f, "after the execution that follows the call");

            parameters.set(0, 7f);
            plan.execute();
            assertScaled(out, 3f, 5f, "one call, one upload");

            plan.uploadWithNextExecution(parameters);
            plan.uploadWithNextExecution(parameters);
            plan.execute();
            assertScaled(out, 7f, 5f, "told twice before one execution");
        }
    }

    /** Before the first execution, and for an object the plan uploads every time, the call changes nothing. */
    @Test
    public void testNothingToDoWhenTheUploadIsDueAnyway() throws TornadoExecutionPlanException {
        FloatArray in = ramp();
        FloatArray parameters = FloatArray.fromElements(2f, 0f);
        FloatArray out = new FloatArray(SIZE);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(scaleGraph(DataTransferMode.FIRST_EXECUTION, in, parameters, out).snapshot())) {
            plan.withGridScheduler(grid("s.scale"));
            plan.uploadWithNextExecution(parameters, in);
            plan.execute();
            assertScaled(out, 2f, 0f, "told before the first execution");
        }
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(scaleGraph(DataTransferMode.EVERY_EXECUTION, in, parameters, out).snapshot())) {
            plan.withGridScheduler(grid("s.scale"));
            plan.execute();
            parameters.set(0, 4f);
            plan.uploadWithNextExecution(parameters, new FloatArray(4)); // the second one is not the plan's
            plan.execute();
            assertScaled(out, 4f, 0f, "an input uploaded on every execution");
        }
    }

    /** An object a graph consumes from the device has no transfer of its own: it is uploaded at once. */
    @Test
    public void testConsumedObjectIsUploadedAtOnce() throws TornadoExecutionPlanException {
        FloatArray data = ramp();
        FloatArray parameters = FloatArray.fromElements(1f);
        TaskGraph producer = new TaskGraph("p") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, data, parameters) //
                .task("add", TestUploadWithNextExecution::add, new KernelContext(), data, parameters) //
                .persistOnDevice(data, parameters);
        TaskGraph consumer = new TaskGraph("c") //
                .consumeFromDevice("p", data, parameters) //
                .task("add", TestUploadWithNextExecution::add, new KernelContext(), data, parameters) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, data);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(producer.snapshot(), consumer.snapshot())) {
            plan.withGridScheduler(grid("p.add", "c.add"));
            plan.withGraph(0).execute();
            plan.withGraph(1).execute();
            assertEquals(12f, data.get(10), 0.0f, "10 + 1 + 1");

            parameters.set(0, 100f);
            plan.uploadWithNextExecution(parameters);
            plan.withGraph(1).execute();
            assertEquals(112f, data.get(10), 0.0f, "12 + 100: the consumer saw the new parameters");
        }
    }

    /** A CUDA graph replays its captured launches: the upload does not wait for the execution. */
    @Test
    public void testWithCudaGraph() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);
        FloatArray in = ramp();
        FloatArray parameters = FloatArray.fromElements(2f, 1f);
        FloatArray out = new FloatArray(SIZE);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(scaleGraph(DataTransferMode.FIRST_EXECUTION, in, parameters, out).snapshot())) {
            plan.withGridScheduler(grid("s.scale")).withCUDAGraph();
            for (int run = 0; run < 3; run++) {
                plan.execute();
                assertScaled(out, 2f, 1f, "CUDA graph, execution " + run);
            }
            parameters.set(0, 6f);
            plan.uploadWithNextExecution(parameters);
            for (int run = 0; run < 3; run++) {
                plan.execute();
                assertScaled(out, 6f, 1f, "CUDA graph, execution " + run + " after the host change");
            }
        }
    }
}
