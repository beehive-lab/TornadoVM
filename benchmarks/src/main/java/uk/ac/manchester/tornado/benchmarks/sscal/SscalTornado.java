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
package uk.ac.manchester.tornado.benchmarks.sscal;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.findULPDistance;
import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.sscal;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays;

public class SscalTornado extends BenchmarkDriver {

    private final int numElements;

    private static final float alpha = 2f;
    private float[] x;

    private TaskSchedule graph;

    public SscalTornado(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        x = new float[numElements];

        for (int i = 0; i < numElements; i++) {
            x[i] = i;
        }

        graph = new TaskSchedule("benchmark");
        if (Boolean.parseBoolean(getProperty("benchmark.streamin", "True"))) {
            graph.streamIn(x);
        }
        graph.task("sscal", LinearAlgebraArrays::sscal, alpha, x);

        if (Boolean.parseBoolean(getProperty("benchmark.streamout", "True"))) {
            graph.streamOut(x);
        }

        if (Boolean.parseBoolean(getProperty("benchmark.warmup", "True"))) {
            graph.warmup();
        }
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        x = null;

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
        graph.syncObjects(x);
        graph.clearProfiles();

        sscal(alpha, result);

        final float ulp = findULPDistance(x, result);
        return ulp < MAX_ULP;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", getProperty("benchmark.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", getProperty("benchmark.device"));
        }
    }
}
