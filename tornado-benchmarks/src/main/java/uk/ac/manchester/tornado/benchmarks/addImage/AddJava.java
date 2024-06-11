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
package uk.ac.manchester.tornado.benchmarks.addImage;

import java.util.Random;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.images.ImageFloat4;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

public class AddJava extends BenchmarkDriver {

    private final int numElementsX;
    private final int numElementsY;

    ImageFloat4 a;
    ImageFloat4 b;
    ImageFloat4 c;

    public AddJava(int iterations, int numElementsX, int numElementsY) {
        super(iterations);
        this.numElementsX = numElementsX;
        this.numElementsY = numElementsY;
    }

    @Override
    public void setUp() {
        a = new ImageFloat4(numElementsX, numElementsY);
        b = new ImageFloat4(numElementsX, numElementsY);
        c = new ImageFloat4(numElementsX, numElementsY);

        Random r = new Random(73);
        for (int j = 0; j < numElementsY; j++) {
            for (int i = 0; i < numElementsX; i++) {
                a.set(i, j, new Float4(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()));
                b.set(i, j, new Float4(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()));
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
    public void runBenchmark(TornadoDevice device) {
        GraphicsKernels.addImage(a, b, c);
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
