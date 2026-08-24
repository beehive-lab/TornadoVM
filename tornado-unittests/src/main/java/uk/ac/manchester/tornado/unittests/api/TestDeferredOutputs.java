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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
 * Tests for {@link TornadoExecutionPlan#withDeferredOutputs()}: an execution returns with its
 * copy-outs still in flight, and the outputs become valid once the execution has been awaited -
 * explicitly through the {@link TornadoExecutionResult}, or implicitly at the next execution of
 * the same plan.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestDeferredOutputs
 * </code>
 */
public class TestDeferredOutputs extends TornadoTestBase {

    private static final int SIZE = 1 << 16;

    public static void scale(FloatArray input, FloatArray output, float alpha) {
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            output.set(i, alpha * input.get(i));
        }
    }

    private static TaskGraph buildGraph(FloatArray input, FloatArray output) {
        return new TaskGraph("deferred") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t", TestDeferredOutputs::scale, input, output, 2.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
    }

    /** Busy-waits, so that it does not park the thread the way a sleep would. */
    private static long hostWork(long micros) {
        long deadline = System.nanoTime() + micros * 1000;
        long spins = 0;
        while (System.nanoTime() < deadline) {
            spins++;
        }
        return spins;
    }

    @Test
    public void testAwaitMakesOutputsVisible() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(3.0f);
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph(input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withDeferredOutputs();
            executionPlan.execute().await();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(6.0f, output.get(i), 0.001f);
        }
    }

    /**
     * The point of the option: host work between {@code execute()} and {@code await()} overlaps
     * with the read-back, and the results are still correct afterwards.
     */
    @Test
    public void testHostWorkBetweenExecuteAndAwait() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray output = new FloatArray(SIZE);
        long spins = 0;

        ImmutableTaskGraph immutableTaskGraph = buildGraph(input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withDeferredOutputs();
            for (int iteration = 1; iteration <= 8; iteration++) {
                input.init(iteration);
                TornadoExecutionResult executionResult = executionPlan.execute();
                spins += hostWork(200);
                executionResult.await();
                for (int i = 0; i < SIZE; i++) {
                    assertEquals(2.0f * iteration, output.get(i), 0.001f);
                }
            }
        }
        assertTrue(spins > 0);
    }

    /**
     * Nothing is lost when the caller never awaits: the next execution of the same plan awaits
     * the previous one before it can overwrite anything.
     */
    @Test
    public void testNextExecutionAwaitsThePreviousOne() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph(input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withDeferredOutputs();
            for (int iteration = 1; iteration <= 8; iteration++) {
                input.init(iteration);
                executionPlan.execute();
            }
            executionPlan.execute().await();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(16.0f, output.get(i), 0.001f);
        }
    }

    /** {@code isReady()} is an observation point, so it awaits like {@code await()} does. */
    @Test
    public void testIsReadyAwaits() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(5.0f);
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph(input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withDeferredOutputs();
            TornadoExecutionResult executionResult = executionPlan.execute();
            assertTrue(executionResult.isReady());
            for (int i = 0; i < SIZE; i++) {
                assertEquals(10.0f, output.get(i), 0.001f);
            }
        }
    }

    /** Switching the option back off restores the synchronous contract for later executions. */
    @Test
    public void testWithoutDeferredOutputsRestoresBlockingExecution() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph(input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withDeferredOutputs();
            input.init(1.0f);
            executionPlan.execute().await();

            executionPlan.withoutDeferredOutputs();
            for (int iteration = 2; iteration <= 4; iteration++) {
                input.init(iteration);
                executionPlan.execute();
                for (int i = 0; i < SIZE; i++) {
                    assertEquals(2.0f * iteration, output.get(i), 0.001f);
                }
            }
        }
    }

    /** A deferred plan that is simply closed must not leave a copy-out in flight. */
    @Test
    public void testCloseAwaitsPendingOutputs() throws TornadoExecutionPlanException {
        FloatArray input = new FloatArray(SIZE);
        input.init(7.0f);
        FloatArray output = new FloatArray(SIZE);

        ImmutableTaskGraph immutableTaskGraph = buildGraph(input, output).snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withDeferredOutputs();
            executionPlan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            assertEquals(14.0f, output.get(i), 0.001f);
        }
    }
}
