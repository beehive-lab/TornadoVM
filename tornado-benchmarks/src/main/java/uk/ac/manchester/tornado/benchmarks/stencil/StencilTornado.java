/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.benchmarks.stencil;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.findULPDistance;
import static uk.ac.manchester.tornado.benchmarks.stencil.Stencil.copy;
import static uk.ac.manchester.tornado.benchmarks.stencil.Stencil.stencil3d;

import java.util.Arrays;
import java.util.Random;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner stencil
 * </code>
 */
public class StencilTornado extends BenchmarkDriver {

    private final int sz;
    private final int n;
    private final float FAC = 1 / 26;
    private float[] a0;
    private float[] a1;
    private float[] ainit;

    public StencilTornado(int iterations, int dataSize) {
        super(iterations);
        sz = (int) Math.cbrt(dataSize / 8) / 2;
        n = sz - 2;
    }

    @Override
    public void setUp() {
        a0 = new float[sz * sz * sz];
        a1 = new float[sz * sz * sz];
        ainit = new float[sz * sz * sz];

        Arrays.fill(a1, 0);

        final Random rand = new Random(7);
        for (int i = 1; i < n + 1; i++) {
            for (int j = 1; j < n + 1; j++) {
                for (int k = 1; k < n + 1; k++) {
                    ainit[(i * sz * sz) + (j * sz) + k] = rand.nextFloat();
                }
            }
        }
        copy(sz, ainit, a0);
        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a0, a1) //
                .task("stencil", Stencil::stencil3d, n, sz, a0, a1, FAC) //
                .task("copy", Stencil::copy, sz, a1, a0) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a0);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withWarmUp();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();
        a0 = null;
        a1 = null;
        ainit = null;
        super.tearDown();
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {

        final float[] b0 = new float[ainit.length];
        final float[] b1 = new float[ainit.length];

        copy(sz, ainit, b0);
        for (int i = 0; i < iterations; i++) {
            benchmarkMethod(device);
        }
        barrier();
        executionPlan.clearProfiles();

        for (int i = 0; i < iterations; i++) {
            stencil3d(n, sz, b0, b1, FAC);
            copy(sz, b1, b0);
        }

        final float ulp = findULPDistance(a0, b0);
        return ulp < MAX_ULP;
    }

    @Override
    protected void barrier() {
        executionResult.transferToHost(a0);
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", getProperty("benchmark.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", getProperty("benchmark.device"));
        }
    }
}
