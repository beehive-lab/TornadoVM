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
package uk.ac.manchester.tornado.benchmarks.convolvearray;

import static uk.ac.manchester.tornado.benchmarks.BenchmarkUtils.createFilter;
import static uk.ac.manchester.tornado.benchmarks.BenchmarkUtils.createImage;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner convolvearray
 * </code>
 */
public class ConvolveImageArrayTornado extends BenchmarkDriver {

    private final int imageSizeX;
    private final int imageSizeY;
    private final int filterSize;
    private FloatArray input;
    private FloatArray output;
    private FloatArray filter;

    public ConvolveImageArrayTornado(int iterations, int imageSizeX, int imageSizeY, int filterSize) {
        super(iterations);
        this.imageSizeX = imageSizeX;
        this.imageSizeY = imageSizeY;
        this.filterSize = filterSize;
    }

    @Override
    public void setUp() {
        input = new FloatArray(imageSizeX * imageSizeY);
        output = new FloatArray(imageSizeX * imageSizeY);
        filter = new FloatArray(filterSize * filterSize);

        createImage(input, imageSizeX, imageSizeY);
        createFilter(filter, filterSize, filterSize);

        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, filter) //
                .task("convolveImageArray", GraphicsKernels::convolveImageArray, input, filter, output, imageSizeX, imageSizeY, filterSize, filterSize) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withPreCompilation();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();

        input = null;
        output = null;
        filter = null;

        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {

        final FloatArray result = new FloatArray(imageSizeX * imageSizeY);

        runBenchmark(device);

        GraphicsKernels.convolveImageArray(input, filter, result, imageSizeX, imageSizeY, filterSize, filterSize);

        float maxULP = 0f;
        for (int i = 0; i < output.getSize(); i++) {
            final float ulp = FloatOps.findMaxULP(result.get(i), output.get(i));

            if (ulp > maxULP) {
                maxULP = ulp;
            }
        }
        return Float.compare(maxULP, MAX_ULP) <= 0;
    }

}
