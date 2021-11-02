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

import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat3;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

public class DotTornado extends BenchmarkDriver {

    private final int numElements;

    private VectorFloat3 a;
    private VectorFloat3 b;
    private float[] c;

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

        ts = new TaskSchedule("benchmark");
        ts.streamIn(a, b);
        ts.task("dotVector", GraphicsKernels::dotVector, a, b, c);
        ts.streamOut(c);
        ts.warmup();
    }

    @Override
    public void tearDown() {
        ts.dumpProfiles();

        a = null;
        b = null;
        c = null;

        ts.getDevice().reset();
        super.tearDown();
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        ts.mapAllTo(device);
        ts.execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {

        final float[] result = new float[numElements];

        benchmarkMethod(device);
        ts.clearProfiles();

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
