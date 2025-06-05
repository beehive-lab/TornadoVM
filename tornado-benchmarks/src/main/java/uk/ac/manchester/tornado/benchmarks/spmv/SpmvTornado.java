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
package uk.ac.manchester.tornado.benchmarks.spmv;

import static uk.ac.manchester.tornado.api.math.TornadoMath.findULPDistance;
import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.spmv;
import static uk.ac.manchester.tornado.benchmarks.spmv.Benchmark.initData;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays;
import uk.ac.manchester.tornado.matrix.SparseMatrixUtils.CSRMatrix;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner spmv
 * </code>
 */
public class SpmvTornado extends BenchmarkDriver {

    private final CSRMatrix<FloatArray> matrix;

    private FloatArray v;
    private FloatArray y;

    public SpmvTornado(int iterations, CSRMatrix<FloatArray> matrix) {
        super(iterations);
        this.matrix = matrix;
    }

    @Override
    public void setUp() {
        v = new FloatArray(matrix.size);
        y = new FloatArray(matrix.size);
        initData(v);
        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix.vals, matrix.cols, matrix.rows, v, y) //
                .task("spmv", LinearAlgebraArrays::spmv, matrix.vals, matrix.cols, matrix.rows, v, matrix.size, y) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withPreCompilation();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();

        v = null;
        y = null;

        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {

        final FloatArray ref = new FloatArray(matrix.size);

        runBenchmark(device);
        executionPlan.clearProfiles();

        spmv(matrix.vals, matrix.cols, matrix.rows, v, matrix.size, ref);

        final float ulp = findULPDistance(y, ref);
        System.out.printf("ulp is %f\n", ulp);
        return ulp < MAX_ULP;
    }
}
