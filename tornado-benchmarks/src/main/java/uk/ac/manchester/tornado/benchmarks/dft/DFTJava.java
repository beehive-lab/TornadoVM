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
package uk.ac.manchester.tornado.benchmarks.dft;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

public class DFTJava extends BenchmarkDriver {

    private int size;
    private DoubleArray inReal;
    private DoubleArray inImag;
    private DoubleArray outReal;
    private DoubleArray outImag;

    public DFTJava(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        inReal = new DoubleArray(size);
        inImag = new DoubleArray(size);
        outReal = new DoubleArray(size);
        outImag = new DoubleArray(size);

        for (int i = 0; i < size; i++) {
            inReal.set(i, (1 / (double) (i + 2)));
            inImag.set(i, (1 / (double) (i + 2)));
        }
    }

    @Override
    public boolean validate(TornadoDevice device) {
        return true;
    }

    @Override
    public void tearDown() {
        outImag = null;
        outReal = null;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        ComputeKernels.computeDFT(inReal, inImag, outReal, outImag);
    }
}
