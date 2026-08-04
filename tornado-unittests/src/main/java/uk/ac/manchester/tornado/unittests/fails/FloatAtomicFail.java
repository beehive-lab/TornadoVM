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

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * {@code KernelContext.atomicAdd(FloatArray, ...)} is intentionally NOT supported on the OpenCL
 * backend -- OpenCL's {@code atom_add} builtin has no floating-point overload, so
 * {@code OCLGraphBuilderPlugins} rejects it at graph-build time
 * ("In OpenCL, the atom_add function does not support floating point operations."). The CUDA
 * backend, by contrast, has a native {@code atomicAdd(float*, float)} and the javadoc on
 * {@link KernelContext#atomicAdd(FloatArray, int, float)} documents that CUDA equivalence
 * directly, so this is verified as a genuine CORRECTNESS test on CUDA/whatever backend is built,
 * with OpenCL explicitly excluded to document the restriction.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fails.FloatAtomicFail
 * </code>
 */
public class FloatAtomicFail extends TornadoTestBase {

    public static void atomicAddFloat(KernelContext context, FloatArray accumulator) {
        context.atomicAdd(accumulator, 0, 1.0f);
    }

    @Test
    public void testFloatAtomicAddOpenCL() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.OPENCL, "OpenCL's atom_add has no floating-point overload -- KernelContext.atomicAdd(FloatArray, ...) is rejected at graph-build time on that backend.");

        final int numThreads = 256;
        FloatArray accumulator = new FloatArray(1);
        accumulator.init(0.0f);
        KernelContext context = new KernelContext();

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, accumulator) //
                .task("t0", FloatAtomicFail::atomicAddFloat, context, accumulator) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, accumulator);

        WorkerGrid worker = new WorkerGrid1D(numThreads);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", worker);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(gridScheduler).execute();
        }

        org.junit.Assert.assertEquals((float) numThreads, accumulator.get(0), 0.01f);
    }

}
