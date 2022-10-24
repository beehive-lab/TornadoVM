/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.benchmarks.hilbert;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner hilbert
 * </code>
 */
public class HilbertTornado extends BenchmarkDriver {

    private int size;
    private float[] hilbertMatrix;

    public HilbertTornado(int size, int iterations) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        hilbertMatrix = new float[size * size];
        taskGraph = new TaskGraph("benchmark") //
                .task("t0", ComputeKernels::hilbertComputation, hilbertMatrix, size, size) //
                .transferToHost(hilbertMatrix);
        taskGraph.warmup();
    }

    @Override
    public void tearDown() {
        taskGraph.dumpProfiles();
        hilbertMatrix = null;
        taskGraph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        boolean val = true;
        float[] testData = new float[size * size];
        TaskGraph check = new TaskGraph("s0") //
                .task("t0", ComputeKernels::hilbertComputation, testData, size, size) //
                .transferToHost(testData); //

        check.mapAllTo(device);
        check.execute();
        float[] seq = new float[size * size];
        ComputeKernels.hilbertComputation(seq, size, size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (Math.abs(testData[i * size + j] - seq[i * size + j]) > 0.01f) {
                    val = false;
                    break;
                }
            }
        }
        return val;
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        taskGraph.mapAllTo(device);
        taskGraph.execute();
    }
}
