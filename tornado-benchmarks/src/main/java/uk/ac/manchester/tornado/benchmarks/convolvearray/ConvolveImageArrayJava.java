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
import static uk.ac.manchester.tornado.benchmarks.GraphicsKernels.convolveImageArray;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class ConvolveImageArrayJava extends BenchmarkDriver {

    private final int imageSizeX;
    private final int imageSizeY;
    private final int filterSize;

    private FloatArray input;
    private FloatArray output;
    private FloatArray filter;

    public ConvolveImageArrayJava(int iterations, int imageSizeX, int imageSizeY, int filterSize) {
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

    }

    @Override
    public void tearDown() {
        input = null;
        output = null;
        filter = null;
        super.tearDown();
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        convolveImageArray(input, filter, output, imageSizeX, imageSizeY, filterSize, filterSize);
    }

    @Override
    public void barrier() {

    }

    @Override
    public boolean validate(TornadoDevice device) {
        return true;
    }

    public void printSummary() {
        System.out.printf("id=java-serial, elapsed=%f, per iteration=%f\n", getElapsed(), getElapsedPerIteration());
    }

}
