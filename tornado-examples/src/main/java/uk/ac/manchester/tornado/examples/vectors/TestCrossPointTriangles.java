/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.vectors;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoRuntime;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.vectors.Double3;

import java.util.Random;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado --threadInfo -m tornado.examples/uk.ac.manchester.tornado.examples.vectors.TestCrossPointTriangles
 * </code>
 *
 */
public class TestCrossPointTriangles {

    private static int crossPointTriangleTriangle(final Double3 v10, final Double3 v11, final Double3 v12) {

        final double tol = 1e-6;

        double nTov0 = Double3.dot(v10, v10);
        double nTov1 = Double3.dot(v11, v11);
        double nTov2 = Double3.dot(v12, v12);

        if (TornadoMath.abs(nTov0) < tol && TornadoMath.abs(nTov1) < tol && TornadoMath.abs(nTov2) < tol) {
            return 0;
        }

        return 1;
    }

    private static void shortCircuitTestKernel(KernelContext context, final DoubleArray a) {

        int[] sVal = context.allocateIntLocalArray(256);
        int localId = context.localIdx;
        int globalId = context.globalIdx;
        if (globalId < a.getSize() / 9) {

            Double3 v10 = new Double3(a.get(globalId * 9), a.get(globalId * 9 + 1), a.get(globalId * 9 + 2));
            Double3 v11 = new Double3(a.get(globalId * 9 + 3), a.get(globalId * 9 + 4), a.get(globalId * 9 + 5));
            Double3 v12 = new Double3(a.get(globalId * 9 + 6), a.get(globalId * 9 + 7), a.get(globalId * 9 + 8));
            sVal[localId] = crossPointTriangleTriangle(v10, v11, v12);
        }
    }

    public static void main() throws TornadoExecutionPlanException {

        TornadoRuntime runtime = TornadoRuntimeProvider.getTornadoRuntime();
        TornadoVMBackendType backendType = runtime.getBackendType(0);
        switch (backendType) {
            case SPIRV -> throw new TornadoRuntimeException("Backend not supported");
        }

        DoubleArray tris1 = new DoubleArray(9 * 256);
        Random random = new Random();
        for (int i = 0; i < tris1.getSize(); i++) {
            tris1.set(i, random.nextDouble());
        }

        WorkerGrid1D workerGrid = new WorkerGrid1D(256); // Create a 1D Worker
        GridScheduler gridScheduler = new GridScheduler("TestCrossPointTriangles.shortCircuitTestKernel", workerGrid);
        KernelContext context = new KernelContext();
        workerGrid.setLocalWork(256, 1, 1);

        TaskGraph taskGraph = new TaskGraph("TestCrossPointTriangles") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, tris1) //
                .task("shortCircuitTestKernel", TestCrossPointTriangles::shortCircuitTestKernel, context, tris1) //
                .transferToHost(DataTransferMode.FIRST_EXECUTION, tris1);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        try (TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph)) {
            executor.withGridScheduler(gridScheduler).execute();
        }
    }
}
