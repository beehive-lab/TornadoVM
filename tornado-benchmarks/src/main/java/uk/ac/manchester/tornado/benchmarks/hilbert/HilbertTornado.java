/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.benchmarks.hilbert;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner hilbert
 * </code>
 */
public class HilbertTornado extends BenchmarkDriver {

    private int size;
    private FloatArray hilbertMatrix;

    public HilbertTornado(int size, int iterations) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        hilbertMatrix = new FloatArray(size * size);
        taskGraph = new TaskGraph("benchmark") //
                .task("t0", ComputeKernels::hilbertComputation, hilbertMatrix, size, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, hilbertMatrix);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withPreCompilation();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();
        hilbertMatrix = null;
        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        boolean val = true;
        FloatArray testData = new FloatArray(size * size);
        TaskGraph taskGraph1 = new TaskGraph("s0") //
                .task("t0", ComputeKernels::hilbertComputation, testData, size, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, testData); //

        ImmutableTaskGraph immutableTaskGraph = taskGraph1.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
        executor.withDevice(device).execute();

        FloatArray seq = new FloatArray(size * size);
        ComputeKernels.hilbertComputation(seq, size, size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (Math.abs(testData.get(i * size + j) - seq.get(i * size + j)) > 0.01f) {
                    val = false;
                    break;
                }
            }
        }
        return val;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }
}
