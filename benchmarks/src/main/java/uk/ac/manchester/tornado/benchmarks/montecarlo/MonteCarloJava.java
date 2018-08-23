/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science, The
 * University of Manchester. All rights reserved. DO NOT ALTER OR REMOVE
 * COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 only, as published by
 * the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License version 2 for more
 * details (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version 2
 * along with this work; if not, write to the Free Software Foundation, Inc., 51
 * Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: Michalis Papadimitriou
 *
 */
//
package uk.ac.manchester.tornado.benchmarks.montecarlo;

import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.monteCarlo;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class MonteCarloJava extends BenchmarkDriver {

    private final int size;

    private float[] seq;

    public MonteCarloJava(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        seq = new float[size];
    }

    @Override
    public void tearDown() {
        seq = null;
        super.tearDown();
    }

    @Override
    public void code() {
        monteCarlo(seq, size);
    }

    @Override
    public void barrier() {

    }

    @Override
    public boolean validate() {

        return true;
    }

    public void printSummary() {
        System.out.printf("java-serial, elapsed=%f, per iteration=%f\n", getElapsed(), getElapsedPerIteration());
    }
}
