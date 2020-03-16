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
package uk.ac.manchester.tornado.benchmarks.striad;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.findULPDistance;
import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.striad;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays;

public class StriadTornado extends BenchmarkDriver {

    private final int numElements;
    private float[] x;
    private float[] y;
    private float[] z;
    private final float alpha = 2f;
    private TaskSchedule graph;

    public StriadTornado(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        x = new float[numElements];
        y = new float[numElements];
        z = new float[numElements];
        Arrays.fill(x, 1);
        Arrays.fill(y, 2);
        graph = new TaskSchedule("benchmark") //
                .streamIn(x, y) //
                .task("striad", LinearAlgebraArrays::striad, alpha, x, y, z) //
                .streamOut(z);
        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        x = null;
        y = null;
        z = null;

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
        graph.syncObjects(z);
        graph.clearProfiles();

        striad(alpha, x, y, result);

        final float ulp = findULPDistance(z, result);
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
