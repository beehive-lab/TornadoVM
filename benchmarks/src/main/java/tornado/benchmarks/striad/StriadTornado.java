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
package tornado.benchmarks.striad;

import java.util.Arrays;
import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.runtime.api.TaskSchedule;

import static tornado.benchmarks.LinearAlgebraArrays.striad;
import static tornado.common.Tornado.getProperty;
import static uk.ac.manchester.tornado.collections.math.TornadoMath.findULPDistance;

public class StriadTornado extends BenchmarkDriver {

    private final int numElements;

    private float[] x, y, z;
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

        graph = new TaskSchedule("benchmark");
        if (Boolean.parseBoolean(getProperty("benchmark.streamin", "True"))) {
            graph.streamIn(x, y);
        }

        graph.task("striad", LinearAlgebraArrays::striad, alpha, x, y, z);

        if (Boolean.parseBoolean(getProperty("benchmark.streamout", "True"))) {
            graph.streamOut(z);
        }

        if (Boolean.parseBoolean(getProperty("benchmark.warmup", "True"))) {
            graph.warmup();
        }
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
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final float[] result = new float[numElements];

        code();
        graph.syncObjects(z);
        graph.clearProfiles();

        striad(alpha, x, y, result);

        final float ulp = findULPDistance(z, result);
        return ulp < MAX_ULP;
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
