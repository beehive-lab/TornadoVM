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
package uk.ac.manchester.tornado.benchmarks.dotimage;

import static uk.ac.manchester.tornado.api.collections.types.FloatOps.findMaxULP;

import java.util.Random;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat;
import uk.ac.manchester.tornado.api.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.GraphicsKernels;

public class DotTornado extends BenchmarkDriver {

    private final int numElementsX;
    private final int numElementsY;

    private ImageFloat3 a;
    private ImageFloat3 b;
    private ImageFloat c;

    public DotTornado(int iterations, int numElementsX, int numElementsY) {
        super(iterations);
        this.numElementsX = numElementsX;
        this.numElementsY = numElementsY;
    }

    @Override
    public void setUp() {
        a = new ImageFloat3(numElementsX, numElementsY);
        b = new ImageFloat3(numElementsX, numElementsY);
        c = new ImageFloat(numElementsX, numElementsY);

        Random r = new Random();
        for (int i = 0; i < numElementsX; i++) {
            for (int j = 0; j < numElementsY; j++) {
                float[] ra = new float[3];
                IntStream.range(0, ra.length).forEach(x -> ra[x] = r.nextFloat());
                float[] rb = new float[3];
                IntStream.range(0, rb.length).forEach(x -> rb[x] = r.nextFloat());
                a.set(i, j, new Float3(ra));
                b.set(i, j, new Float3(rb));
            }
        }
        ts = new TaskSchedule("benchmark") //
                .streamIn(a, b) //
                .task("dotVector", GraphicsKernels::dotImage, a, b, c) //
                .streamOut(c);
        ts.warmup();
    }

    @Override
    public void tearDown() {
        ts.dumpProfiles();
        a = null;
        b = null;
        c = null;
        ts.getDevice().reset();
        super.tearDown();
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        ts.mapAllTo(device);
        ts.execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {

        final ImageFloat result = new ImageFloat(numElementsX, numElementsX);

        benchmarkMethod(device);
        ts.syncObjects(c);
        ts.clearProfiles();

        GraphicsKernels.dotImage(a, b, result);

        float maxULP = 0f;
        for (int i = 0; i < c.Y(); i++) {
            for (int j = 0; j < c.X(); j++) {
                final float ulp = findMaxULP(c.get(j, i), result.get(j, i));

                if (ulp > maxULP) {
                    maxULP = ulp;
                }
            }
        }
        return Float.compare(maxULP, MAX_ULP) <= 0;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", TornadoRuntime.getProperty("benchmark.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", TornadoRuntime.getProperty("benchmark.device"));
        }
    }
}
