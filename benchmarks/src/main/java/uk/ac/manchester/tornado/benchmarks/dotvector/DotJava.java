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
package uk.ac.manchester.tornado.benchmarks.dotvector;

import static uk.ac.manchester.tornado.benchmarks.GraphicsKernels.dotVector;

import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat3;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

import java.util.Random;
import java.util.stream.IntStream;

public class DotJava extends BenchmarkDriver {

    private final int numElements;

    private VectorFloat3 a;
    private VectorFloat3 b;
    private float[] c;

    public DotJava(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        a = new VectorFloat3(numElements);
        b = new VectorFloat3(numElements);
        c = new float[numElements];

        Random r = new Random();
        for (int i = 0; i < numElements; i++) {
            float[] ra = new float[3];
            IntStream.range(0, ra.length).forEach(x -> ra[x] = r.nextFloat());
            float[] rb = new float[3];
            IntStream.range(0, rb.length).forEach(x -> rb[x] = r.nextFloat());
            a.set(i, new Float3(ra));
            b.set(i, new Float3(rb));
        }
    }

    @Override
    public void tearDown() {
        a = null;
        b = null;
        c = null;
        super.tearDown();
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        dotVector(a, b, c);
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
