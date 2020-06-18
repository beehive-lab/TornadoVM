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
package uk.ac.manchester.tornado.benchmarks.dft;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

public class DFTJava extends BenchmarkDriver {

    private int size;
    private double[] inReal;
    private double[] inImag;
    private double[] outReal;
    private double[] outImag;

    public DFTJava(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        inReal = new double[size];
        inImag = new double[size];
        outReal = new double[size];
        outImag = new double[size];

        for (int i = 0; i < size; i++) {
            inReal[i] = 1 / (double) (i + 2);
            inImag[i] = 1 / (double) (i + 2);
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
    public void benchmarkMethod(TornadoDevice device) {
        ComputeKernels.computeDFT(inReal, inImag, outReal, outImag);
    }
}
