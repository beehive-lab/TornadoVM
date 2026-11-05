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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoBackendNotFound;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@link TornadoExecutionPlan#initializeDeviceAsync(int, int)}: initialising a device on a background thread.
 *
 * <p>
 * How to run:
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestInitializeDeviceAsync
 * </code>
 */
public class TestInitializeDeviceAsync extends TornadoTestBase {

    private static final int N = 1024;

    public static void square(KernelContext context, IntArray a) {
        int i = context.globalIdx;
        a.set(i, a.get(i) * a.get(i));
    }

    private static void runOn(TornadoDevice device) throws TornadoExecutionPlanException {
        IntArray a = new IntArray(N);
        for (int i = 0; i < N; i++) {
            a.set(i, i);
        }
        TaskGraph graph = new TaskGraph("init") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a) //
                .task("t", TestInitializeDeviceAsync::square, new KernelContext(), a) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a);
        WorkerGrid1D grid = new WorkerGrid1D(N);
        grid.setLocalWork(64, 1, 1);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withDevice(device).withGridScheduler(new GridScheduler("init.t", grid)).execute();
        }
        for (int i = 0; i < N; i++) {
            assertEquals(i * i, a.get(i), "a[" + i + "]");
        }
    }

    /** The future yields the device {@code getDevice} names, ready to run on. */
    @Test
    public void testFutureYieldsTheDevice() throws Exception {
        TornadoDevice initialized = TornadoExecutionPlan.initializeDeviceAsync().get();
        assertEquals(TornadoExecutionPlan.getDefaultDevice(), initialized);
        assertEquals(TornadoExecutionPlan.getDevice(0, 0), initialized);
        runOn(initialized);
    }

    /** An execution that starts while the initialisation is still running waits for it and is correct. */
    @Test
    public void testExecutionDuringInitialization() throws Exception {
        CompletableFuture<TornadoDevice> initializing = TornadoExecutionPlan.initializeDeviceAsync(0, 0);
        runOn(TornadoExecutionPlan.getDevice(0, 0));
        assertEquals(TornadoExecutionPlan.getDevice(0, 0), initializing.get());
    }

    /** A backend that does not exist fails the future with the runtime's own error. */
    @Test
    public void testUnknownBackendFailsTheFuture() {
        int missing = getTornadoRuntime().getNumBackends();
        ExecutionException e = assertThrows(ExecutionException.class, () -> TornadoExecutionPlan.initializeDeviceAsync(missing, 0).get());
        assertInstanceOf(TornadoBackendNotFound.class, e.getCause());
    }
}
