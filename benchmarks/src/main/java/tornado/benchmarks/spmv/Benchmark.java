/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.benchmarks.spmv;

import java.util.Random;
import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.BenchmarkRunner;
import uk.ac.manchester.tornado.collections.matrix.SparseMatrixUtils;
import uk.ac.manchester.tornado.collections.matrix.SparseMatrixUtils.CSRMatrix;

public class Benchmark extends BenchmarkRunner {

    private CSRMatrix<float[]> matrix;
    private String path;

    public static void populateVector(final float[] v) {
        final Random rand = new Random();
        rand.setSeed(7);
        for (int i = 0; i < v.length; i++) {
            v[i] = rand.nextFloat() * 100.0f;
        }
    }

    @Override
    public void parseArgs(String[] args) {
        if (args.length == 2) {
            iterations = Integer.parseInt(args[0]);
            final String fullpath = args[1];
            path = fullpath.substring(fullpath.lastIndexOf("/") + 1);
            matrix = SparseMatrixUtils.loadMatrixF(fullpath);
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
