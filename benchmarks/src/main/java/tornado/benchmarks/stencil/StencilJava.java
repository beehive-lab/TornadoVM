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
package tornado.benchmarks.stencil;

import java.util.Arrays;
import java.util.Random;
import tornado.benchmarks.BenchmarkDriver;

import static tornado.benchmarks.stencil.Stencil.copy;
import static tornado.benchmarks.stencil.Stencil.stencil3d;

public class StencilJava extends BenchmarkDriver {

    private final int sz, n;
    private final float FAC = 1 / 26;
    private float[] a0, a1;

    private static final double TOLERANCE = 1.0e-15;

    public StencilJava(int iterations, int dataSize) {
        super(iterations);
        sz = (int) Math.cbrt(dataSize / 8) / 2;
        n = sz - 2;
    }

    @Override
    public void setUp() {
        a0 = new float[sz * sz * sz];
        a1 = new float[sz * sz * sz];

        Arrays.fill(a0, 0);
        Arrays.fill(a1, 0);

        final Random rand = new Random(7);
        for (int i = 1; i < n + 1; i++) {
            for (int j = 1; j < n + 1; j++) {
                for (int k = 1; k < n + 1; k++) {
                    a0[i * sz * sz + j * sz + k] = rand.nextFloat();
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
    public void code() {
        stencil3d(n, sz, a0, a1, FAC);
        copy(sz, a0, a1);
    }

    @Override
    public void barrier() {

    }

    @Override
    public boolean validate() {
        return true;
    }

    public void printSummary() {
        System.out.printf("id=java-serial, elapsed=%f, per iteration=%f\n", getElapsed(), getElapsedPerIteration());
    }

}
