/*
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.benchmarks.convolveimage;

import static uk.ac.manchester.tornado.benchmarks.BenchmarkUtils.createFilter;
import static uk.ac.manchester.tornado.benchmarks.BenchmarkUtils.createImage;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.FloatOps;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

public class ConvolveImageTornado extends BenchmarkDriver {

    private final int imageSizeX,imageSizeY,filterSize;

    private ImageFloat input,output,filter;

    private TaskSchedule graph;

    public ConvolveImageTornado(int iterations, int imageSizeX, int imageSizeY, int filterSize) {
        super(iterations);
        this.imageSizeX = imageSizeX;
        this.imageSizeY = imageSizeY;
        this.filterSize = filterSize;
    }

    @Override
    public void setUp() {
        input = new ImageFloat(imageSizeX, imageSizeY);
        output = new ImageFloat(imageSizeX, imageSizeY);
        filter = new ImageFloat(filterSize, filterSize);

        createImage(input);
        createFilter(filter);

        graph = new TaskSchedule("benchmark");

        if (Boolean.parseBoolean(TornadoRuntime.getProperty("benchmark.streamin", "True"))) {
            graph.streamIn(input);
        }

        graph.task("convolveImage", GraphicsKernels::convolveImage, input, filter, output);

        if (Boolean.parseBoolean(TornadoRuntime.getProperty("benchmark.streamout", "True"))) {
            graph.streamOut(output);
        }

        if (Boolean.parseBoolean(TornadoRuntime.getProperty("benchmark.warmup", "True"))) {
            graph.warmup();
        }
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

        final ImageFloat result = new ImageFloat(imageSizeX, imageSizeY);

        code();
        graph.syncObject(output);
        graph.clearProfiles();

        GraphicsKernels.convolveImage(input, filter, result);

        float maxULP = 0f;
        for (int y = 0; y < output.Y(); y++) {
            for (int x = 0; x < output.X(); x++) {
                final float ulp = FloatOps.findMaxULP(output.get(x, y), result.get(x, y));

                if (ulp > maxULP) {
                    maxULP = ulp;
                }
            }
        }
        return Float.compare(maxULP, MAX_ULP) <= 0;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", TornadoRuntime.getProperty("benchmark.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", TornadoRuntime.getProperty("benchmark.device"));
        }
    }

}
