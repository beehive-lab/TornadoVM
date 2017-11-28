/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
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
package tornado.benchmarks.dotimage;

import tornado.benchmarks.BenchmarkDriver;
import tornado.collections.types.Float3;
import tornado.collections.types.ImageFloat;
import tornado.collections.types.ImageFloat3;
import tornado.runtime.api.TaskSchedule;

import static tornado.benchmarks.GraphicsKernels.dotImage;

public class DotJava extends BenchmarkDriver {

    private final int numElementsX;
    private final int numElementsY;

    private ImageFloat3 a, b;
    private ImageFloat c;

    private TaskSchedule graph;

    public DotJava(int iterations, int numElementsX, int numElementsY) {
        super(iterations);
        this.numElementsX = numElementsX;
        this.numElementsY = numElementsY;
    }

    @Override
    public void setUp() {
        a = new ImageFloat3(numElementsX, numElementsY);
        b = new ImageFloat3(numElementsX, numElementsY);
        c = new ImageFloat(numElementsX, numElementsY);

        final Float3 valueA = new Float3(1f, 1f, 1f);
        final Float3 valueB = new Float3(2f, 2f, 2f);

        for (int i = 0; i < numElementsX; i++) {
            for (int j = 0; j < numElementsY; j++) {
                a.set(i, j, valueA);
                b.set(i, j, valueB);
            }
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
        dotImage(a, b, c);
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
