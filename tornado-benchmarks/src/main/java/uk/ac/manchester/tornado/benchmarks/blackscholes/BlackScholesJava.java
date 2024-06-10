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

package uk.ac.manchester.tornado.benchmarks.blackscholes;

import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.blackscholes;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class BlackScholesJava extends BenchmarkDriver {

    private final int size;
    private FloatArray randArray;
    private FloatArray call;
    private FloatArray put;

    public BlackScholesJava(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        randArray = new FloatArray(size);
        call = new FloatArray(size);
        put = new FloatArray(size);

        for (int i = 0; i < size; i++) {
            randArray.set(i, (i * 1.0f) / size);
        }
    }

    @Override
    public boolean validate(TornadoDevice device) {
        return true;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        blackscholes(randArray, call, put);
    }
}
