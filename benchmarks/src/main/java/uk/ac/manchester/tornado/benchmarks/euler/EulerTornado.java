/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.benchmarks.euler;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

public class EulerTornado extends BenchmarkDriver {

    private int size;
    long[] input;
    long[] outputA;
    long[] outputB;
    long[] outputC;
    long[] outputD;
    long[] outputE;

    public EulerTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    private long[] init(int size) {
        long[] input = new long[size];
        for (int i = 0; i < size; i++) {
            input[i] = (long) i * i * i * i * i;
        }
        return input;
    }

    @Override
    public void setUp() {
        input = init(size);
        outputA = new long[size];
        outputB = new long[size];
        outputC = new long[size];
        outputD = new long[size];
        outputE = new long[size];
        ts = new TaskSchedule("benchmark") //
                .streamIn(input) //
                .task("euler", ComputeKernels::euler, size, input, outputA, outputB, outputC, outputD, outputE) //
                .streamOut(outputA, outputB, outputC, outputD, outputE);
    }

    @Override
    public void tearDown() {
        input = null;
        outputA = null;
        outputB = null;
        outputC = null;
        outputD = null;
        outputE = null;
        super.tearDown();
    }

    private void runSequential(int size, long[] input, long[] outputA, long[] outputB, long[] outputC, long[] outputD, long[] outputE) {
        ComputeKernels.euler(size, input, outputA, outputB, outputC, outputD, outputE);
        for (int i = 0; i < outputA.length; i++) {
            if (outputA[i] != 0) {
                long a = outputA[i];
                long b = outputB[i];
                long c = outputC[i];
                long d = outputD[i];
                long e = outputE[i];
                System.out.println(a + "^5 + " + b + "^5 + " + c + "^5 + " + d + "^5 = " + e + "^5");
            }
        }
    }

    private void runParallel(int size, long[] input, long[] outputA, long[] outputB, long[] outputC, long[] outputD, long[] outputE, TornadoDevice device) {
        TaskSchedule ts = new TaskSchedule("s0") //
                .task("s0", ComputeKernels::euler, size, input, outputA, outputB, outputC, outputD, outputE) //
                .streamOut(outputA, outputB, outputC, outputD, outputE);
        ts.mapAllTo(device);
        ts.execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        long[] input = init(size);
        long[] outputA = new long[size];
        long[] outputB = new long[size];
        long[] outputC = new long[size];
        long[] outputD = new long[size];
        long[] outputE = new long[size];

        runSequential(size, input, outputA, outputB, outputC, outputD, outputE);

        long[] outputAT = new long[size];
        long[] outputBT = new long[size];
        long[] outputCT = new long[size];
        long[] outputDT = new long[size];
        long[] outputET = new long[size];

        runParallel(size, input, outputAT, outputBT, outputCT, outputDT, outputET, device);

        for (int i = 0; i < outputA.length; i++) {
            if (outputAT[i] != outputA[i]) {
                return false;
            }
            if (outputBT[i] != outputB[i]) {
                return false;
            }
            if (outputCT[i] != outputC[i]) {
                return false;
            }
            if (outputDT[i] != outputD[i]) {
                return false;
            }
            if (outputET[i] != outputE[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        ts.mapAllTo(device);
        ts.execute();
    }
}
