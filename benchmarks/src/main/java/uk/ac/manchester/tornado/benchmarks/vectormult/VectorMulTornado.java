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

package uk.ac.manchester.tornado.benchmarks.vectormult;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.abs;
import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.vectorMultiply;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

public class VectorMulTornado extends BenchmarkDriver {
    private int numElements;
    private float[] a,b,c;
    private TaskSchedule graph;

    public VectorMulTornado(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        a = new float[numElements];
        b = new float[numElements];
        c = new float[numElements];

        Arrays.fill(a, 3);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        graph = new TaskSchedule("benchmark");
        graph.task("t0", ComputeKernels::vectorMultiply, a, b, c);
        graph.warmup();
    }

    @Override
    public boolean validate() {
        boolean val = true;
        float[] result = new float[numElements];

        Arrays.fill(result, 0);

        code();
        graph.syncObject(c);
        graph.clearProfiles();

        vectorMultiply(a, b, result);

        for (int i = 0; i < numElements; i++) {
            if (abs(c[i] - result[i]) > 0.01) {
                val = false;
                break;
            }
        }
        System.out.printf("Number validation: " + val + "\n");
        return true;
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
}
