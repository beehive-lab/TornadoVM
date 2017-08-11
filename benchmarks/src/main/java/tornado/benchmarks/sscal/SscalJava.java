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
package tornado.benchmarks.sscal;

import tornado.benchmarks.BenchmarkDriver;

import static tornado.benchmarks.LinearAlgebraArrays.sscal;

public class SscalJava extends BenchmarkDriver {

    private final int numElements;

    private static final float alpha = 2f;
    private float[] x;

    public SscalJava(int iterations, int numElements) {
        super(iterations);
        this.numElements = numElements;
    }

    @Override
    public void setUp() {
        x = new float[numElements];

        for (int i = 0; i < numElements; i++) {
            x[i] = i;
        }

    }

    @Override
    public void tearDown() {
        x = null;
        super.tearDown();
    }

    @Override
    public void code() {
        sscal(alpha, x);
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
