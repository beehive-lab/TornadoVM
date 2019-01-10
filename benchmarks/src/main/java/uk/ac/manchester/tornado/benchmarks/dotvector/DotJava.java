/*
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class DotJava extends BenchmarkDriver {

    private final int numElements;

    private VectorFloat3 a,b;
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

        final Float3 valueA = new Float3(1f, 1f, 1f);
        final Float3 valueB = new Float3(2f, 2f, 2f);
        for (int i = 0; i < numElements; i++) {
            a.set(i, valueA);
            b.set(i, valueB);
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
    public void code() {
        dotVector(a, b, c);
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
