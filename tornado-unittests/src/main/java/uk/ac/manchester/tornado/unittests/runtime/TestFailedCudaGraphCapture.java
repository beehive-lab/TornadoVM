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
package uk.ac.manchester.tornado.unittests.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;
import uk.ac.manchester.tornado.cublas.provider.CuBlasLibraryProvider;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * An execution that fails in the middle of capturing a CUDA graph must not affect later captures
 * on the same device: the failed capture is abandoned and nothing recorded for it is replayed into
 * the next one.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.runtime.TestFailedCudaGraphCapture
 * </code>
 */
public class TestFailedCudaGraphCapture extends TornadoTestBase {

    private static final int SIZE = 1 << 16;
    private static final int LOCAL_SIZE = 256;
    private static final int ROWS = 256;

    public static void addOne(KernelContext context, FloatArray array) {
        int id = context.globalIdx;
        if (id < array.getSize()) {
            array.set(id, array.get(id) + 1.0f);
        }
    }

    private static WorkerGrid worker(int threads) {
        WorkerGrid worker = new WorkerGrid1D(threads);
        worker.setLocalWork(LOCAL_SIZE, 1, 1);
        return worker;
    }

    @Test
    public void testFailedCaptureDoesNotAffectTheNextOne() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL);
        assertNotBackend(TornadoVMBackendType.METAL);

        if (!CuBlasLibraryProvider.isAvailable()) {
            throw new TornadoVMCUDANotSupported("This test makes a cuBLAS call fail during the capture; cuBLAS is not available");
        }
        // The kernel is recorded (its arguments are written once the capture ends); then cuBLAS
        // rejects the sgemv (a leading dimension of 0), so the execution fails inside the capture.
        FloatArray matrix = new FloatArray(ROWS * ROWS);
        FloatArray vector = new FloatArray(ROWS);
        FloatArray result = new FloatArray(ROWS);
        TaskGraph failing = new TaskGraph("failing") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector) //
                .task("valid", TestFailedCudaGraphCapture::addOne, new KernelContext(), vector) //
                .libraryTask("invalid", CuBlas::cublasSgemv, CuBlasOperation.CUBLAS_OP_N.operation(), ROWS, ROWS, 1.0f, matrix, 0, vector, 1, 0.0f, result, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(failing.snapshot())) {
            plan.withGridScheduler(new GridScheduler("failing.valid", worker(ROWS))).withCUDAGraph();
            assertThrows(RuntimeException.class, plan::execute);
        }

        // A later plan on the same device captures and replays as usual.
        FloatArray second = new FloatArray(SIZE);
        TaskGraph working = new TaskGraph("working") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, second) //
                .task("valid", TestFailedCudaGraphCapture::addOne, new KernelContext(), second) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, second);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(working.snapshot())) {
            plan.withGridScheduler(new GridScheduler("working.valid", worker(SIZE))).withCUDAGraph();
            for (int execution = 0; execution < 3; execution++) {
                second.init(execution * 10.0f);
                plan.execute();
                for (int i = 0; i < SIZE; i++) {
                    assertEquals(execution * 10.0f + 1.0f, second.get(i), 0.0f);
                }
            }
        }
    }
}
