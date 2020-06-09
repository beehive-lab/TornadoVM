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
package uk.ac.manchester.tornado.benchmarks.dotvector;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.findULPDistance;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat3;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

import java.util.Random;
import java.util.stream.IntStream;

public class DotTornado extends BenchmarkDriver {

    private final int numElements;

    private VectorFloat3 a;
    private VectorFloat3 b;
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

        Random r = new Random();
        for (int i = 0; i < numElements; i++) {
            float[] ra = new float[3];
            IntStream.range(0, ra.length).forEach(x -> ra[x] = r.nextFloat());
            float[] rb = new float[3];
            IntStream.range(0, rb.length).forEach(x -> rb[x] = r.nextFloat());
            a.set(i, new Float3(ra));
            b.set(i, new Float3(rb));
        }

        graph = new TaskSchedule("benchmark");
        graph.streamIn(a, b);
        graph.task("dotVector", GraphicsKernels::dotVector, a, b, c);
        graph.streamOut(c);
        graph.warmup();
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
    public void benchmarkMethod() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[numElements];

        benchmarkMethod();
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
