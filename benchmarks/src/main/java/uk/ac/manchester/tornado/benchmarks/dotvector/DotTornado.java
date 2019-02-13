/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.benchmarks.dotvector;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.findULPDistance;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat3;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

public class DotTornado extends BenchmarkDriver {

    private final int numElements;

    private VectorFloat3 a,b;
    private float[] c;

    private TaskSchedule graph;

    public DotTornado(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        a = new VectorFloat3(numElements);
        b = new VectorFloat3(numElements);
        c = new float[numElements];

        final Float3 valueA = new Float3(1f, 1f, 1f);
        final Float3 valueB = new Float3(2f, 2f, 2f);
        for (int i = 0; i < numElements; i++) {
            a.set(i, valueA);
            b.set(i, valueB);
        }

        graph = new TaskSchedule("benchmark");
        if (Boolean.parseBoolean(TornadoRuntime.getProperty("benchmark.streamin", "True"))) {
            graph.streamIn(a, b);
        }
        graph.task("dotVector", GraphicsKernels::dotVector, a, b, c);
        if (Boolean.parseBoolean(TornadoRuntime.getProperty("benchmark.streamout", "True"))) {
            graph.streamOut(c);
        }

        if (Boolean.parseBoolean(TornadoRuntime.getProperty("benchmark.warmup", "True"))) {
            graph.warmup();
        }
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        a = null;
        b = null;
        c = null;

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[numElements];

        code();
        graph.clearProfiles();

        GraphicsKernels.dotVector(a, b, result);

        final float ulp = findULPDistance(result, c);
        return Float.compare(ulp, MAX_ULP) <= 0;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", TornadoRuntime.getProperty("benchmark.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", TornadoRuntime.getProperty("benchmark.device"));
        }
    }
}
