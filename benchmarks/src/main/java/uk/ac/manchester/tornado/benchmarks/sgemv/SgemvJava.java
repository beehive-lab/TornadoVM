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
package uk.ac.manchester.tornado.benchmarks.sgemv;

import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.sgemv;

import java.util.Random;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class SgemvJava extends BenchmarkDriver {

    private final int m, n;

    private float[] a, x, y;

    public SgemvJava(int iterations, int m, int n) {
        super(iterations);
        this.m = m;
        this.n = n;
    }

    @Override
    public void setUp() {
        a = new float[m * n];
        x = new float[n];
        y = new float[n];

        final Random random = new Random();

        for (int i = 0; i < m; i++) {
            a[i * (m + 1)] = 1;
        }

        for (int i = 0; i < n; i++) {
            x[i] = random.nextFloat();
        }

    }

    @Override
    public void tearDown() {
        a = null;
        x = null;
        y = null;
        super.tearDown();
    }

    @Override
    public void code() {
        sgemv(m, n, a, x, y);
    }

    @Override
    public void barrier() {

    }

    @Override
    public boolean validate() {

        return true;
    }

    public void printSummary() {
        System.out.printf("id=java-serial, elapsed=%f, per iteration=%f\n", getElapsed(), getElapsedPerIteration());
    }

}
