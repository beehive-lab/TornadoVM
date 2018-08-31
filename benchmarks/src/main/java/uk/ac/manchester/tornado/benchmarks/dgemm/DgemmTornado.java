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
package uk.ac.manchester.tornado.benchmarks.dgemm;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.findULPDistance;
import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.dgemm;

import java.util.Random;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays;

public class DgemmTornado extends BenchmarkDriver {

    private final int m,n;

    private double[] a,b,c;

    private TaskSchedule graph;

    public DgemmTornado(int iterations, int m, int n) {
        super(iterations);
        this.m = m;
        this.n = n;
    }

    @Override
    public void setUp() {
        a = new double[m * n];
        b = new double[m * n];
        c = new double[m * n];

        final Random random = new Random();

        for (int i = 0; i < m; i++) {
            a[i * (m + 1)] = 1;
        }

        for (int i = 0; i < m * n; i++) {
            b[i] = random.nextFloat();
        }

        graph = new TaskSchedule("benchmark").task("dgemm", LinearAlgebraArrays::dgemm, m, n, n, a, b, c).streamOut(c);

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
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final double[] result = new double[m * n];

        code();
        graph.clearProfiles();

        dgemm(m, n, m, a, b, result);

        final double ulp = findULPDistance(c, result);
        return ulp < MAX_ULP;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", TornadoRuntime.getProperty("benchmark.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", TornadoRuntime.getProperty("benchmark.device"));
        }
    }

}
