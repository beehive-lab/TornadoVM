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

package uk.ac.manchester.tornado.benchmarks.montecarlo;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.abs;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

public class MonteCarloTornado extends BenchmarkDriver {

    private float[] output;
    private int size;

    public MonteCarloTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        output = new float[size];
        ts = new TaskSchedule("benchmark") //
                .task("montecarlo", ComputeKernels::monteCarlo, output, size) //
                .streamOut(output);
        ts.warmup();
    }

    @Override
    public void tearDown() {
        ts.dumpProfiles();
        output = null;
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
        float[] result;
        boolean isCorrect = true;

        result = new float[size];

        ComputeKernels.monteCarlo(result, size);
        ts.warmup();
        ts.mapAllTo(device);
        for (int i = 0; i < 3; i++) {
            ts.execute();
        }
        ts.syncObjects(output);
        ts.clearProfiles();

        for (int i = 0; i < size; i++) {
            if (abs(output[i] - result[i]) > 0.01) {
                isCorrect = false;
                break;
            }
        }
        System.out.printf("Number validation: " + isCorrect + "\n");
        return isCorrect;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", TornadoRuntime.getProperty("benchmark.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", TornadoRuntime.getProperty("benchmark.device"));
        }
    }
}
