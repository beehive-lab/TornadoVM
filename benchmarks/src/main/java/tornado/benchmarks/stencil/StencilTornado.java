/*
 * Copyright 2012 James Clarkson.
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
 */
package tornado.benchmarks.stencil;

import java.util.Arrays;
import java.util.Random;
import tornado.benchmarks.BenchmarkDriver;
import tornado.common.SchedulableTask;
import tornado.runtime.api.TaskSchedule;

import static tornado.benchmarks.stencil.Stencil.copy;
import static tornado.benchmarks.stencil.Stencil.stencil3d;
import static tornado.collections.math.TornadoMath.findULPDistance;
import static tornado.common.Tornado.getProperty;
import static tornado.runtime.cache.TornadoObjectCache.sync;

public class StencilTornado extends BenchmarkDriver {

    private final int sz, n;
    private final float FAC = 1 / 26;
    private float[] a0, a1, ainit;

    private static final double TOLERANCE = 1.0e-15;

    private TaskSchedule graph;
    private SchedulableTask stencilTask;

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

        Arrays.fill(a0, 0);
        Arrays.fill(a1, 0);
        Arrays.fill(ainit, 0);

        final Random rand = new Random(7);
        for (int i = 1; i < n + 1; i++) {
            for (int j = 1; j < n + 1; j++) {
                for (int k = 1; k < n + 1; k++) {
                    ainit[i * sz * sz + j * sz + k] = rand.nextFloat();
                }
            }
        }

        System.arraycopy(ainit, 0, a0, 0, ainit.length);

        graph = new TaskSchedule("benchmark")
                .task("stencil", Stencil::stencil3d, n, sz, a0, a1, FAC)
                .task("copy", Stencil::copy, sz, a0, a1);

        stencilTask = graph.getTask("stencil");

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        a0 = null;
        a1 = null;
        ainit = null;

        graph.getDefaultDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final float[] b0 = new float[ainit.length];
        final float[] b1 = new float[ainit.length];

        System.arraycopy(ainit, 0, b0, 0, ainit.length);
        for (int i = 0; i < iterations; i++) {
            code();
        }
        graph.clearProfiles();

        for (int i = 0; i < iterations; i++) {
            stencil3d(n, sz, b0, b1, FAC);
            copy(sz, b0, b1);
        }

        final float ulp = findULPDistance(a0, b0);
        return ulp < MAX_ULP;
    }

    @Override
    protected void barrier() {
        sync(a0);
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
