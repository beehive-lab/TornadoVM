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
package uk.ac.manchester.tornado.benchmarks.euler;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

public class EulerJava extends BenchmarkDriver {

    private int size;
    LongArray input;
    LongArray outputA;
    LongArray outputB;
    LongArray outputC;
    LongArray outputD;
    LongArray outputE;

    public EulerJava(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    private LongArray init(int size) {
        LongArray input = new LongArray(size);
        for (int i = 0; i < size; i++) {
            input.set(i, ((long) i * i * i * i * i));
        }
        return input;
    }

    @Override
    public void setUp() {
        input = init(size);
        outputA = new LongArray(size);
        outputB = new LongArray(size);
        outputC = new LongArray(size);
        outputD = new LongArray(size);
        outputE = new LongArray(size);
    }

    @Override
    public boolean validate(TornadoDevice device) {
        return true;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        ComputeKernels.euler(size, input, outputA, outputB, outputC, outputD, outputE);
    }
}
