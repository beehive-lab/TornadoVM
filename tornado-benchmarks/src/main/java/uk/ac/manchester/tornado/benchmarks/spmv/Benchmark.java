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

import java.util.Random;

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.BenchmarkRunner;
import uk.ac.manchester.tornado.matrix.SparseMatrixUtils;
import uk.ac.manchester.tornado.matrix.SparseMatrixUtils.CSRMatrix;

public class Benchmark extends BenchmarkRunner {

    private CSRMatrix<FloatArray> matrix;
    private String path;

    public static void initData(final FloatArray v) {
        final Random rand = new Random();
        rand.setSeed(7);
        for (int i = 0; i < v.getSize(); i++) {
            v.set(i, rand.nextFloat() * 100.0f);
        }
    }

    @Override
    public void parseArgs(String[] args) {
        if (args.length == 2) {
            iterations = Integer.parseInt(args[0]);
            final String fullPath = args[1];
            path = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            matrix = SparseMatrixUtils.loadMatrixF(fullPath);
        } else {
            path = System.getProperty("spmv.matrix", "/bcsstk32.mtx");
            matrix = SparseMatrixUtils.loadMatrixF(Benchmark.class.getResourceAsStream(path));
            iterations = Integer.parseInt(System.getProperty("spmv.iterations", "1400"));
        }
    }

    @Override
    protected String getName() {
        return "spmv";
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d-%s", getName(), iterations, matrix.size, path);
    }

    @Override
    protected String getConfigString() {
        return String.format("matrix=%s", path);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
        return new SpmvJava(iterations, matrix);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver() {
        return new SpmvTornado(iterations, matrix);
    }

}
