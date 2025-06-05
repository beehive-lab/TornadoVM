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
package uk.ac.manchester.tornado.benchmarks.dotvector;

import static uk.ac.manchester.tornado.api.math.TornadoMath.findULPDistance;

import java.util.Random;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat3;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner dotvector
 * </code>
 */
public class DotTornado extends BenchmarkDriver {

    private final int numElements;

    private VectorFloat3 a;
    private VectorFloat3 b;
    private FloatArray c;

    public DotTornado(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        a = new VectorFloat3(numElements);
        b = new VectorFloat3(numElements);
        c = new FloatArray(numElements);

        Random r = new Random();
        for (int i = 0; i < numElements; i++) {
            a.set(i, new Float3(r.nextFloat(), r.nextFloat(), r.nextFloat()));
            b.set(i, new Float3(r.nextFloat(), r.nextFloat(), r.nextFloat()));
        }

        taskGraph = new TaskGraph("benchmark");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b);
        taskGraph.task("dotVector", GraphicsKernels::dotVector, a, b, c);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withPreCompilation();

    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();

        a = null;
        b = null;
        c = null;

        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {

        final FloatArray result = new FloatArray(numElements);

        runBenchmark(device);
        executionPlan.clearProfiles();

        GraphicsKernels.dotVector(a, b, result);

        final float ulp = findULPDistance(result, c);
        return Float.compare(ulp, MAX_ULP) <= 0;
    }

}
