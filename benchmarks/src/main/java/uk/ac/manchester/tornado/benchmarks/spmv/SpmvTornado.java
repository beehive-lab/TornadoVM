/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.benchmarks.spmv;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays;
import uk.ac.manchester.tornado.collections.matrix.SparseMatrixUtils.CSRMatrix;

import static uk.ac.manchester.tornado.api.collections.math.TornadoMath.findULPDistance;
import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.spmv;
import static uk.ac.manchester.tornado.benchmarks.spmv.Benchmark.populateVector;
import static uk.ac.manchester.tornado.common.Tornado.getProperty;

import uk.ac.manchester.tornado.api.TaskSchedule;

public class SpmvTornado extends BenchmarkDriver {

    private final CSRMatrix<float[]> matrix;

    private float[] v, y;

    private TaskSchedule graph;

    public SpmvTornado(int iterations, CSRMatrix<float[]> matrix) {
        super(iterations);
        this.matrix = matrix;
    }

    @Override
    public void setUp() {
        v = new float[matrix.size];
        y = new float[matrix.size];

        populateVector(v);

        graph = new TaskSchedule("benchmark")
                .task("spmv", LinearAlgebraArrays::spmv, matrix.vals,
                        matrix.cols, matrix.rows, v, matrix.size, y)
                .streamOut(y);

        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        v = null;
        y = null;

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public void code() {
        graph.execute();
    }

    @Override
    public boolean validate() {

        final float[] ref = new float[matrix.size];

        code();
        graph.clearProfiles();

        spmv(matrix.vals, matrix.cols, matrix.rows, v, matrix.size, ref);

        final float ulp = findULPDistance(y, ref);
        System.out.printf("ulp is %f\n", ulp);
        return ulp < MAX_ULP;
    }

    public void printSummary() {
        if (isValid()) {
            System.out.printf(
                    "id=%s, elapsed=%f, per iteration=%f\n",
                    getProperty("benchmark.device"), getElapsed(),
                    getElapsedPerIteration());
        } else {
            System.out.printf("id=%s produced invalid result\n",
                    getProperty("benchmark.device"));
        }
    }
}
