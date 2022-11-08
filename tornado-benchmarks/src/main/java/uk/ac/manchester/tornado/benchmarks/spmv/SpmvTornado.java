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
package uk.ac.manchester.tornado.benchmarks.spmv;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.findULPDistance;
import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.spmv;
import static uk.ac.manchester.tornado.benchmarks.spmv.Benchmark.initData;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays;
import uk.ac.manchester.tornado.matrix.SparseMatrixUtils.CSRMatrix;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 *     tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner spmv
 * </code>
 */
public class SpmvTornado extends BenchmarkDriver {

    private final CSRMatrix<float[]> matrix;

    private float[] v;
    private float[] y;

    public SpmvTornado(int iterations, CSRMatrix<float[]> matrix) {
        super(iterations);
        this.matrix = matrix;
    }

    @Override
    public void setUp() {
        v = new float[matrix.size];
        y = new float[matrix.size];
        initData(v);
        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix.vals, matrix.cols, matrix.rows, v, y) //
                .task("spmv", LinearAlgebraArrays::spmv, matrix.vals, matrix.cols, matrix.rows, v, matrix.size, y) //
                .transferToHost(y);
        taskGraph.warmup();
    }

    @Override
    public void tearDown() {
        taskGraph.dumpProfiles();

        v = null;
        y = null;

        taskGraph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public void benchmarkMethod(TornadoDevice device) {
        taskGraph.mapAllTo(device);
        taskGraph.execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {

        final float[] ref = new float[matrix.size];

        benchmarkMethod(device);
        taskGraph.clearProfiles();

        spmv(matrix.vals, matrix.cols, matrix.rows, v, matrix.size, ref);

        final float ulp = findULPDistance(y, ref);
        System.out.printf("ulp is %f\n", ulp);
        return ulp < MAX_ULP;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf("id=%s, elapsed=%f, per iteration=%f\n", getProperty("benchmark.device"), getElapsed(), getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n", getProperty("benchmark.device"));
        }
    }
}
