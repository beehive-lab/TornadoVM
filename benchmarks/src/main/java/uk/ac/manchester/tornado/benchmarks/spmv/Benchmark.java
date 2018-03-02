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

import java.util.Random;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.BenchmarkRunner;
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
