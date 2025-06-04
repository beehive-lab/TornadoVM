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
package uk.ac.manchester.tornado.benchmarks.dgemm;

import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.dgemm;

import java.util.Random;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner dgemm
 * </code>
 */
public class DgemmTornado extends BenchmarkDriver {

    private final int m;
    private final int n;
    private DoubleArray a;
    private DoubleArray b;
    private DoubleArray c;

    public DgemmTornado(int iterations, int m, int n) {
        super(iterations);
        this.m = m;
        this.n = n;
    }

    @Override
    public void setUp() {
        a = new DoubleArray(m * n);
        b = new DoubleArray(m * n);
        c = new DoubleArray(m * n);

        final Random random = new Random();

        for (int i = 0; i < m; i++) {
            a.set(i * (m + 1), 1);
        }

        for (int i = 0; i < m * n; i++) {
            b.set(i, random.nextFloat());
        }

        taskGraph = new TaskGraph("benchmark");
        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b) //
                .task("dgemm", LinearAlgebraArrays::dgemm, m, n, n, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        executionPlan = new TornadoExecutionPlan(taskGraph.snapshot());
        executionPlan.withPreCompilation();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();

        a = null;
        b = null;
        c = null;

        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {

        final DoubleArray result = new DoubleArray(m * n);

        runBenchmark(device);
        executionPlan.clearProfiles();

        dgemm(m, n, m, a, b, result);

        final double ulp = TornadoMath.findULPDistance(c, result);
        return ulp < MAX_ULP;
    }

}
