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

import static uk.ac.manchester.tornado.api.math.TornadoMath.abs;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner dft
 * </code>
 */
public class DFTTornado extends BenchmarkDriver {

    private int size;
    private FloatArray inReal;
    private FloatArray inImag;
    private FloatArray outReal;
    private FloatArray outImag;

    public DFTTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    private void initData() {
        inReal = new FloatArray(size);
        inImag = new FloatArray(size);
        outReal = new FloatArray(size);
        outImag = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            inReal.set(i, (1 / (i + 2)));
            inImag.set(i, (1 / (i + 2)));
        }
    }

    @Override
    public void setUp() {
        initData();
        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inReal, inImag) //
                .task("t0", ComputeKernels::computeDFT, inReal, inImag, outReal, outImag) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outReal, outImag);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withPreCompilation();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        boolean validation = true;
        FloatArray outRealTor = new FloatArray(size);
        FloatArray outImagTor = new FloatArray(size);

        executionPlan.withDevice(device) //
                .withPreCompilation() //
                .execute();

        ComputeKernels.computeDFT(inReal, inImag, outRealTor, outImagTor);

        executionPlan.clearProfiles();

        for (int i = 0; i < size; i++) {
            if (abs(outImagTor.get(i) - outImag.get(i)) > 0.01) {
                validation = false;
                break;
            }
            if (abs(outReal.get(i) - outRealTor.get(i)) > 0.01) {
                validation = false;
                break;
            }
        }
        System.out.print("Is correct?: " + validation + "\n");
        return validation;
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();

        outImag = null;
        outReal = null;

        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }
}
