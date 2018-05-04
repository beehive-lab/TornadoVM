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
 * Authors: Michalis Papadimitriou
 *
 */
package uk.ac.manchester.tornado.benchmarks.dft;

import uk.ac.manchester.tornado.benchmarks.*;

public class DftJava extends BenchmarkDriver {
    private int size;
    private double[] inreal,inimag,outreal,outimag;

    public DftJava(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        inreal = new double[size];
        inimag = new double[size];
        outreal = new double[size];
        outimag = new double[size];

        for (int i = 0; i < size; i++) {
            inreal[i] = 1 / (double) (i + 2);
            inimag[i] = 1 / (double) (i + 2);
        }
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public void tearDown() {
        outimag = null;
        outreal = null;
    }

    @Override
    public void code() {
        ComputeKernels.computeDft(inreal, inimag, outreal, outimag);

    }
}
