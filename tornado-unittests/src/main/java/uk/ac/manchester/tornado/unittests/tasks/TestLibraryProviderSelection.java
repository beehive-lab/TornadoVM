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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Library-provider selection on backends other than CUDA. The Metal backend exposes its native
 * command queue to library providers (TornadoNativeStreamSupport), so CUDA library providers must
 * not claim a Metal device just because it has a native stream: a cuBLAS task on Metal has to be
 * refused by the registry with a clear message, not fail inside cuBLAS.
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tasks.TestLibraryProviderSelection
 * </code>
 */
public class TestLibraryProviderSelection extends TornadoTestBase {

    @Test
    public void testCudaLibraryRefusedOnMetal() {
        TornadoVMBackendType backend = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backend != TornadoVMBackendType.METAL) {
            assertNotBackend(backend, "Checks CUDA library providers on the Metal backend only (default device is " + backend + ")");
        }

        final int n = 64;
        FloatArray x = new FloatArray(n);
        FloatArray result = new FloatArray(1);
        x.init(1.0f);

        TaskGraph taskGraph = new TaskGraph("providerSelection") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .libraryTask("sasum", CuBlas::cublasSasum, n, x, 1, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
            fail("A cuBLAS library task must not run on the Metal backend");
        } catch (Exception e) {
            assertTrue("Expected the registry's 'not supported on device' error, got: " + describe(e), mentions(e, "is not supported on device"));
        }
    }

    private static boolean mentions(Throwable t, String text) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null && c.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append(" <- ");
        }
        return sb.toString();
    }
}
