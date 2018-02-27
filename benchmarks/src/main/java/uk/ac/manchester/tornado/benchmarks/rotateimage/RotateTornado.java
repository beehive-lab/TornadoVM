/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.benchmarks.rotateimage;

import tornado.runtime.api.TaskSchedule;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;
import uk.ac.manchester.tornado.collections.types.Float3;
import uk.ac.manchester.tornado.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.collections.types.Matrix4x4Float;

import static tornado.common.Tornado.getProperty;
import static uk.ac.manchester.tornado.benchmarks.GraphicsKernels.rotateImage;
import static uk.ac.manchester.tornado.collections.types.FloatOps.findMaxULP;

public class RotateTornado extends BenchmarkDriver {

    private final int numElementsX, numElementsY;

    private ImageFloat3 input, output;
    private Matrix4x4Float m;

    private TaskSchedule graph;

    public RotateTornado(int iterations, int numElementsX, int numElementsY) {
        super(iterations);
        this.numElementsX = numElementsX;
        this.numElementsY = numElementsY;
    }

    @Override
    public void setUp() {
        input = new ImageFloat3(numElementsX, numElementsY);
        output = new ImageFloat3(numElementsX, numElementsY);

        m = new Matrix4x4Float();
        m.identity();

        final Float3 value = new Float3(1f, 2f, 3f);
        for (int i = 0; i < input.Y(); i++) {
            for (int j = 0; j < input.X(); j++) {
                input.set(j, i, value);
            }
        }

        graph = new TaskSchedule("benchmark");
        if (Boolean.parseBoolean(getProperty("benchmark.streamin", "True"))) {
            graph.streamIn(input);
        }
        graph.task("rotateImage", GraphicsKernels::rotateImage, output, m,
                input);
        if (Boolean.parseBoolean(getProperty("benchmark.streamout", "True"))) {
            graph.streamOut(output);
        }

        if (Boolean.parseBoolean(getProperty("benchmark.warmup", "True"))) {
            graph.warmup();
        }
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        input = null;
        output = null;
        m = null;

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final ImageFloat3 result = new ImageFloat3(numElementsX, numElementsY);

        code();
        graph.syncObjects(output);
        graph.clearProfiles();

        rotateImage(result, m, input);

        float maxULP = 0f;
        for (int i = 0; i < input.Y(); i++) {
            for (int j = 0; j < input.X(); j++) {
                final float ulp = findMaxULP(output.get(j, i),
                        result.get(j, i));

                if (ulp > maxULP) {
                    maxULP = ulp;
                }
            }
        }
        return Float.compare(maxULP, MAX_ULP) <= 0;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf(
                    "id=%s, elapsed=%f, per iteration=%f\n",
                    getProperty("benchmark.device"), getElapsed(),
                    getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n",
                    getProperty("benchmark.device"));
        }
    }

}
