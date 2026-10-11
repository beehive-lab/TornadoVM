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
package uk.ac.manchester.tornado.unittests.fails;

import static org.junit.Assert.assertThrows;
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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * A kernel that needs more threadgroup memory than the device has compiles, but Metal refuses to
 * build its compute pipeline. That refusal must reach the application as an exception that carries
 * Metal's reason, instead of a kernel that is silently never launched or a JVM that exits without
 * a message.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fails.LocalMemoryFail
 * </code>
 */
public class LocalMemoryFail extends TornadoTestBase {

    // 48 KB of local memory, over the 32 KB that Apple GPUs allow per threadgroup. Much larger
    // requests crash Metal's compiler service instead of producing a limit error.
    private static final int LOCAL_FLOATS = 12_288;

    private static final int SIZE = 256;

    public static void copyThroughLocalMemory(KernelContext context, FloatArray input, FloatArray output) {
        int id = context.globalIdx;
        int localId = context.localIdx;
        float[] staging = context.allocateFloatLocalArray(LOCAL_FLOATS);
        if (id < input.getSize()) {
            staging[localId] = input.get(id);
        }
        context.localBarrier();
        if (id < input.getSize()) {
            output.set(id, staging[localId]);
        }
    }

    @Test
    public void testTooMuchLocalMemoryIsReported() {
        assertNotBackend(TornadoVMBackendType.OPENCL, "Only the Metal driver's report of a refused pipeline is checked here.");
        assertNotBackend(TornadoVMBackendType.CUDA, "Only the Metal driver's report of a refused pipeline is checked here.");

        FloatArray input = new FloatArray(SIZE);
        FloatArray output = new FloatArray(SIZE);
        input.init(1.0f);

        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setLocalWork(64, 1, 1);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", LocalMemoryFail::copyThroughLocalMemory, new KernelContext(), input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        RuntimeException failure = assertThrows(RuntimeException.class, () -> {
            try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
                executionPlan.withGridScheduler(gridScheduler).execute();
            }
        });
        String message = String.valueOf(failure.getMessage()) + String.valueOf(failure.getCause());
        assertTrue("the failure should say the pipeline was refused, got: " + message, message.contains("Unable to create the Metal compute pipeline"));
        assertTrue("the failure should carry Metal's reason, got: " + message, message.contains("hreadgroup memory"));
    }
}
