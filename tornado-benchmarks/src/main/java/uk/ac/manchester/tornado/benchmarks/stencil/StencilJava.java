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
package uk.ac.manchester.tornado.benchmarks.stencil;

import static uk.ac.manchester.tornado.benchmarks.stencil.Stencil.copy;
import static uk.ac.manchester.tornado.benchmarks.stencil.Stencil.stencil3d;

import java.util.Random;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class StencilJava extends BenchmarkDriver {

    private final int sz;
    private final int n;
    private final float FAC = 1 / 26;
    private FloatArray a0;
    private FloatArray a1;

    public StencilJava(int iterations, int dataSize) {
        super(iterations);
        sz = (int) Math.cbrt(dataSize / 8) / 2;
        n = sz - 2;
    }

    @Override
    public void setUp() {
        a0 = new FloatArray(sz * sz * sz);
        a1 = new FloatArray(sz * sz * sz);

        a0.init(0);
        a1.init(0);

        final Random rand = new Random(7);
        for (int i = 1; i < n + 1; i++) {
            for (int j = 1; j < n + 1; j++) {
                for (int k = 1; k < n + 1; k++) {
                    a0.set(i * sz * sz + j * sz + k, rand.nextFloat());
                }
            }
        }
    }

    @Override
    public void tearDown() {
        a0 = null;
        a1 = null;
        super.tearDown();
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        stencil3d(n, sz, a0, a1, FAC);
        copy(sz, a0, a1);
    }

    @Override
    public void barrier() {

    }

    @Override
    public boolean validate(TornadoDevice device) {
        return true;
    }

    public void printSummary() {
        System.out.printf("id=java-serial, elapsed=%f, per iteration=%f\n", getElapsed(), getElapsedPerIteration());
    }

}
