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
package uk.ac.manchester.tornado.benchmarks.dotimage;

import static uk.ac.manchester.tornado.api.types.utils.FloatOps.findMaxULP;

import java.util.Random;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.images.ImageFloat;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner dotimage
 * </code>
 */
public class DotTornado extends BenchmarkDriver {

    private final int numElementsX;
    private final int numElementsY;

    private ImageFloat3 a;
    private ImageFloat3 b;
    private ImageFloat c;

    public DotTornado(int iterations, int numElementsX, int numElementsY) {
        super(iterations);
        this.numElementsX = numElementsX;
        this.numElementsY = numElementsY;
    }

    @Override
    public void setUp() {
        a = new ImageFloat3(numElementsX, numElementsY);
        b = new ImageFloat3(numElementsX, numElementsY);
        c = new ImageFloat(numElementsX, numElementsY);

        Random r = new Random();
        for (int i = 0; i < numElementsX; i++) {
            for (int j = 0; j < numElementsY; j++) {
                a.set(i, j, new Float3(r.nextFloat(), r.nextFloat(), r.nextFloat()));
                b.set(i, j, new Float3(r.nextFloat(), r.nextFloat(), r.nextFloat()));
            }
        }
        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("dotVector", GraphicsKernels::dotImage, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

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
        executionResult.getProfilerResult().dumpProfiles();
        super.tearDown();
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {

        final ImageFloat result = new ImageFloat(numElementsX, numElementsX);
        runBenchmark(device);
        executionPlan.clearProfiles();

        GraphicsKernels.dotImage(a, b, result);

        float maxULP = 0f;
        for (int i = 0; i < c.Y(); i++) {
            for (int j = 0; j < c.X(); j++) {
                final float ulp = findMaxULP(c.get(j, i), result.get(j, i));

                if (ulp > maxULP) {
                    maxULP = ulp;
                }
            }
        }
        return Float.compare(maxULP, MAX_ULP) <= 0;
    }

}
