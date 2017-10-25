/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
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
package tornado.benchmarks.rotatevector;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.Float3;
import tornado.collections.types.Matrix4x4Float;
import tornado.collections.types.VectorFloat3;
import tornado.runtime.api.TaskSchedule;

import static tornado.benchmarks.GraphicsKernels.rotateVector;
import static tornado.collections.types.FloatOps.findMaxULP;
import static tornado.common.Tornado.getProperty;

public class RotateTornado extends BenchmarkDriver {

    private final int numElements;

    private VectorFloat3 input, output;
    private Matrix4x4Float m;

    private TaskSchedule graph;

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

        final Float3 value = new Float3(new float[]{1f, 2f, 3f});
        for (int i = 0; i < numElements; i++) {
            input.set(i, value);
        }

        graph = new TaskSchedule("benchmark");
        if (Boolean.parseBoolean(getProperty("benchmark.streamin", "True"))) {
            graph.streamIn(input);
        }
        graph.task("rotateVector", GraphicsKernels::rotateVector, output, m,
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

        final VectorFloat3 result = new VectorFloat3(numElements);

        code();
        graph.syncObjects(output);
        graph.clearProfiles();

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
