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
package uk.ac.manchester.tornado.benchmarks.rotatevector;

import static uk.ac.manchester.tornado.api.types.utils.FloatOps.findMaxULP;
import static uk.ac.manchester.tornado.benchmarks.GraphicsKernels.rotateVector;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat3;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner rotatevector
 * </code>
 */
public class RotateTornado extends BenchmarkDriver {

    private final int numElements;
    private VectorFloat3 input;
    private VectorFloat3 output;
    private Matrix4x4Float m;

    public RotateTornado(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        input = new VectorFloat3(numElements);
        output = new VectorFloat3(numElements);

        m = new Matrix4x4Float();
        m.identity();

        final Float3 value = new Float3(1f, 2f, 3f);
        for (int i = 0; i < numElements; i++) {
            input.set(i, value);
        }

        taskGraph = new TaskGraph("benchmark");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, input);
        taskGraph.task("rotateVector", GraphicsKernels::rotateVector, output, m, input);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withPreCompilation();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();
        input = null;
        output = null;
        m = null;
        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {

        final VectorFloat3 result = new VectorFloat3(numElements);

        runBenchmark(device);
        executionPlan.clearProfiles();

        rotateVector(result, m, input);

        float maxULP = 0f;
        for (int i = 0; i < numElements; i++) {
            final float ulp = findMaxULP(output.get(i), result.get(i));

            if (ulp > maxULP) {
                maxULP = ulp;
            }
        }

        return Float.compare(maxULP, MAX_ULP) <= 0;
    }
}
