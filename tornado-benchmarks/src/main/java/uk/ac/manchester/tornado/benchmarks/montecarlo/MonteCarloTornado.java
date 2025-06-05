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
package uk.ac.manchester.tornado.benchmarks.montecarlo;

import static uk.ac.manchester.tornado.api.math.TornadoMath.abs;

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
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner montecarlo
 * </code>
 */
public class MonteCarloTornado extends BenchmarkDriver {

    private FloatArray output;
    private int size;

    public MonteCarloTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        output = new FloatArray(size);
        taskGraph = new TaskGraph("benchmark") //
                .task("montecarlo", ComputeKernels::monteCarlo, output, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withPreCompilation();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();
        output = null;
        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        FloatArray result;
        boolean isCorrect = true;

        result = new FloatArray(size);

        ComputeKernels.monteCarlo(result, size);
        executionPlan.withDevice(device).execute();
        executionPlan.clearProfiles();

        for (int i = 0; i < size; i++) {
            if (abs(output.get(i) - result.get(i)) > 0.01) {
                isCorrect = false;
                break;
            }
        }
        System.out.printf("Number validation: " + isCorrect + "\n");
        return isCorrect;
    }

}
