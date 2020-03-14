/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package uk.ac.manchester.tornado.benchmarks.convolvearray;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

import static uk.ac.manchester.tornado.benchmarks.BenchmarkUtils.createFilter;
import static uk.ac.manchester.tornado.benchmarks.BenchmarkUtils.createImage;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.FloatOps;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class ConvolveImageArrayTornado extends BenchmarkDriver {

    private final int imageSizeX,imageSizeY,filterSize;

    private float[] input,output,filter;

    private TaskSchedule graph;

    public ConvolveImageArrayTornado(int iterations, int imageSizeX, int imageSizeY, int filterSize) {
        super(iterations);
        this.imageSizeX = imageSizeX;
        this.imageSizeY = imageSizeY;
        this.filterSize = filterSize;
    }

    @Override
    public void setUp() {
        input = new float[imageSizeX * imageSizeY];
        output = new float[imageSizeX * imageSizeY];
        filter = new float[filterSize * filterSize];

        createImage(input, imageSizeX, imageSizeY);
        createFilter(filter, filterSize, filterSize);

        graph = new TaskSchedule("benchmark");
        graph.streamIn(input);
        graph.task("convolveImageArray", GraphicsKernels::convolveImageArray, input, filter, output, imageSizeX, imageSizeY, filterSize, filterSize);
        graph.streamOut(output);
        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        input = null;
        output = null;
        filter = null;

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[imageSizeX * imageSizeY];

        code();
        graph.syncObject(output);
        graph.clearProfiles();

        GraphicsKernels.convolveImageArray(input, filter, result, imageSizeX, imageSizeY, filterSize, filterSize);

        float maxULP = 0f;
        for (int i = 0; i < output.length; i++) {
            final float ulp = FloatOps.findMaxULP(result[i], output[i]);

            if (ulp > maxULP) {
                maxULP = ulp;
            }
        }
        return Float.compare(maxULP, MAX_ULP) <= 0;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", TornadoRuntime.getProperty("s0.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", TornadoRuntime.getProperty("s0.device"));
        }
    }
}
